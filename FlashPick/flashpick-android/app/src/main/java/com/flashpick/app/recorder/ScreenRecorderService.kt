package com.flashpick.app.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.Process
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.flashpick.app.R
import com.flashpick.app.recorder.buffer.CircularBuffer
import com.flashpick.app.recorder.buffer.EncodedFrame
import com.flashpick.app.worker.VideoAnalysisWorker
import com.flashpick.app.service.AppMonitorService
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.ranges.coerceIn
import kotlin.math.max

class ScreenRecorderService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val circularBuffer = CircularBuffer(BUFFER_CAPACITY_US)
    private var outputFormat: MediaFormat? = null
    private var lastPresentationUs: Long = 0L
    private var isCapturing = false
    private var preCaptureUs: Long = DEFAULT_PRE_CAPTURE_US
    private var postCaptureUs: Long = DEFAULT_POST_CAPTURE_US

    private var isSaving = false
    private var muxer: MediaMuxer? = null
    private var muxerTrackIndex: Int = -1
    private var targetEndTimeUs: Long = 0L
    private val audioBuffer = CircularBuffer(BUFFER_CAPACITY_US)
    private var audioEncoder: MediaCodec? = null
    private var audioOutputFormat: MediaFormat? = null
    private var audioTrackIndex: Int = -1
    private var audioPtsUs: Long = 0L
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    @Volatile
    private var audioCaptureActive = false
    private var audioThread: Thread? = null
    private var micRecorder: AudioRecord? = null
    private var playbackRecorder: AudioRecord? = null
    private var currentClipBase: String? = null
    private var lastClipBase: String? = null
    private var videoBasePtsUs: Long = -1L
    private var audioBasePtsUs: Long = -1L
    private var actualPreRollUs: Long = 0L // Store actual pre-roll duration
    private var voiceRecorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Auto-Link Capture
    private var pendingUrl: String? = null
    private var lastUrlCaptureTime: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        loadWindowSettings()
        // Start Clipboard Monitor
        startClipboardMonitor()
    }

    private fun startClipboardMonitor() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboard?.addPrimaryClipChangedListener {
            try {
                if (clipboard.hasPrimaryClip()) {
                    val item = clipboard.primaryClip?.getItemAt(0)
                    val text = item?.text?.toString() ?: ""
                    if (text.contains("http")) { // Simple check
                        val urlRegex = "(https?://\\S+)".toRegex()
                        val match = urlRegex.find(text)
                        val url = match?.value
                        
                        if (!url.isNullOrEmpty()) {
                            pendingUrl = url
                            lastUrlCaptureTime = System.currentTimeMillis()
                            Log.i(TAG, "Captured URL from clipboard: $url")
                            // Optional: Toast to confirm
                            showToast("已捕获链接: $url") 
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Clipboard read failed", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val permission = ScreenRecorderPermissionStore.current()
        if (permission == null) {
            Log.w(TAG, "Missing MediaProjection permission, stop service")
            stopSelf()
            return START_NOT_STICKY
        }

        ensureProjection(permission)
        if (encoder == null) {
            startEncoding()
            Log.i(TAG, "ScreenRecorderService started and MediaProjection initialized")
        }

        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                isCapturing = true
                startAudioComponents()
                Log.i(TAG, "Capture enabled")
            }
            ACTION_STOP_CAPTURE -> pauseCapture(flushClip = true)
            ACTION_REQUEST_CLIP -> {
                val preMs = intent.getLongExtra(EXTRA_PRE_MS, -1L)
                val postMs = intent.getLongExtra(EXTRA_POST_MS, -1L)
                handleClipRequest(
                    if (preMs > 0) preMs * 1000L else null,
                    if (postMs > 0) postMs * 1000L else null
                )
            }
            ACTION_SET_WINDOW -> {
                val preMs = intent.getLongExtra(EXTRA_PRE_MS, preCaptureUs / 1000L)
                val postMs = intent.getLongExtra(EXTRA_POST_MS, postCaptureUs / 1000L)
                updateCaptureWindow(preMs, postMs)
            }
            ACTION_START_VOICE_NOTE -> startVoiceNote()
            ACTION_STOP_VOICE_NOTE -> stopVoiceNote()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseResources()
        Log.i(TAG, "ScreenRecorderService destroyed")
    }

    private fun ensureProjection(permission: ScreenRecorderController.ScreenRecorderPermission) {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (mediaProjection == null) {
            mediaProjection = mediaProjectionManager.getMediaProjection(
                permission.resultCode,
                permission.data
            )?.apply {
                registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        Log.i(TAG, "MediaProjection stopped by system")
                        pauseCapture(flushClip = true)
                        ScreenRecorderPermissionStore.clear()
                        Toast.makeText(
                            this@ScreenRecorderService,
                            getString(R.string.recorder_permission_request),
                            Toast.LENGTH_SHORT
                        ).show()
                        stopSelf()
                    }
                }, null)
            }
        }
    }

    private fun startEncoding() {
        val projection = mediaProjection ?: return
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            setCallback(codecCallback)
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = createInputSurface()
            start()
        }

        virtualDisplay = projection.createVirtualDisplay(
            "FlashPickRecorder",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            null
        )
    }

    private val codecCallback = object : MediaCodec.Callback() {
        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            val buffer = codec.getOutputBuffer(index)
            if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                if (!isCapturing) {
                    codec.releaseOutputBuffer(index, false)
                    return
                }
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                val data = ByteArray(info.size)
                buffer.get(data)
                val copyInfo = MediaCodec.BufferInfo().apply {
                    set(0, info.size, info.presentationTimeUs, info.flags)
                }
                val frame = EncodedFrame(data, copyInfo)
                circularBuffer.add(frame)
                lastPresentationUs = copyInfo.presentationTimeUs
                if (isSaving) {
                    writeFrame(frame)
                    if (targetEndTimeUs > 0 && copyInfo.presentationTimeUs >= targetEndTimeUs) {
                        finishSaving()
                    }
                }
            }
            codec.releaseOutputBuffer(index, false)
        }

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            // Surface input; not used
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            outputFormat = format
            Log.i(TAG, "Encoder format changed: $format")
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "Encoder error", e)
            stopSelf()
        }
    }

    private val audioCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            val inputBuffer = codec.getInputBuffer(index) ?: return
            inputBuffer.clear()
            val data = try {
                audioQueue.poll(20, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            }
            val size = data?.size ?: 0
            if (size > 0 && data != null) {
                inputBuffer.put(data)
            }
            val pts = nextAudioPts(size)
            codec.queueInputBuffer(index, 0, size, pts, 0)
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            val buffer = codec.getOutputBuffer(index)
            if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                val data = ByteArray(info.size)
                buffer.get(data)
                val copyInfo = MediaCodec.BufferInfo().apply {
                    set(0, info.size, info.presentationTimeUs, info.flags)
                }
                val frame = EncodedFrame(data, copyInfo)
                audioBuffer.add(frame)
                if (isSaving) {
                    writeAudioFrame(frame)
                }
            }
            codec.releaseOutputBuffer(index, false)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            audioOutputFormat = format
            Log.i(TAG, "Audio encoder format changed: $format")
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "Audio encoder error", e)
            stopAudioEncoder()
        }
    }

    private fun handleClipRequest(overridePreUs: Long? = null, overridePostUs: Long? = null) {
        if (isSaving) {
            Log.w(TAG, "Clip request ignored: already saving")
            return
        }
        if (!isCapturing) {
            Toast.makeText(this, "当前未在白名单应用内，无法保存", Toast.LENGTH_SHORT).show()
            return
        }
        val format = outputFormat
        if (format == null) {
            Log.w(TAG, "Clip request ignored: encoder format not ready")
            return
        }
        val usePre = overridePreUs ?: preCaptureUs
        val usePost = overridePostUs ?: postCaptureUs
        
        val preWindow = usePre.coerceAtMost(BUFFER_CAPACITY_US)
        val snapshot = circularBuffer.snapshot(preWindow)
        val audioSnapshot = audioBuffer.snapshot(preWindow)
        val latestTimestamp = snapshot.lastOrNull()?.info?.presentationTimeUs ?: lastPresentationUs
        targetEndTimeUs = latestTimestamp + usePost.coerceAtMost(BUFFER_CAPACITY_US)
        val firstVideoPts = snapshot.firstOrNull()?.info?.presentationTimeUs
            ?: max(latestTimestamp - preWindow, 0L)
        videoBasePtsUs = firstVideoPts
        
        // Calculate actual pre-roll duration (trigger point relative to start of clip)
        actualPreRollUs = (latestTimestamp - firstVideoPts).coerceAtLeast(0L)

        val firstAudioPts = audioSnapshot.firstOrNull()?.info?.presentationTimeUs
        audioBasePtsUs = firstAudioPts ?: firstVideoPts

        val outputFile = createOutputFile()
        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
                muxerTrackIndex = addTrack(format)
                val audioFormat = audioOutputFormat
                if (audioFormat != null) {
                    audioTrackIndex = addTrack(audioFormat)
                } else {
                    audioTrackIndex = -1
                    Log.w(TAG, "Audio format not ready, clip will be muted")
                }
                start()
            }
            snapshot.forEach { writeFrame(it) }
            audioSnapshot.forEach { writeAudioFrame(it) }
            isSaving = true
            Log.i(TAG, "Saving clip to ${outputFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start muxer", e)
            muxer = null
            muxerTrackIndex = -1
            audioTrackIndex = -1
            targetEndTimeUs = 0L
        }
    }

    private fun writeFrame(frame: EncodedFrame) {
        val muxer = muxer ?: return
        if (muxerTrackIndex < 0) return
        if (videoBasePtsUs < 0 && frame.info.presentationTimeUs >= 0) {
            videoBasePtsUs = frame.info.presentationTimeUs
        }
        val buffer = ByteBuffer.wrap(frame.data)
        muxer.writeSampleData(muxerTrackIndex, buffer, normalizedInfo(frame.info, videoBasePtsUs))
    }


    private fun writeAudioFrame(frame: EncodedFrame) {
        val muxer = muxer ?: return
        if (audioTrackIndex < 0) return
        if (audioBasePtsUs < 0 && frame.info.presentationTimeUs >= 0) {
            audioBasePtsUs = frame.info.presentationTimeUs
        }
        val buffer = ByteBuffer.wrap(frame.data)
        muxer.writeSampleData(audioTrackIndex, buffer, normalizedInfo(frame.info, audioBasePtsUs))
    }

    private fun finishSaving() {
        try {
            muxer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping muxer", e)
        }
        try {
            muxer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing muxer", e)
        }
        muxer = null
        muxerTrackIndex = -1
        audioTrackIndex = -1
        isSaving = false
        targetEndTimeUs = 0L
        lastClipBase = currentClipBase ?: lastClipBase
        
        // Trigger AI Analysis
        currentClipBase?.let { base ->
            val file = File(getVideoDir(), "${base}.mp4")
            if (file.exists()) {
                // Delay 3s to allow auto-click "Copy Link" to update clipboard
                mainHandler.postDelayed({
                    scheduleAnalysis(file)
                }, 3000)
            }
        }

        currentClipBase = null
        videoBasePtsUs = -1L
        audioBasePtsUs = -1L
        Log.i(TAG, "Clip saved")
    }

    private fun scheduleAnalysis(videoFile: File) {
        val packageName = AppMonitorService.currentPackage ?: AppMonitorService.activePackage ?: "unknown"
        
        // Priority: 1. Clipboard (if recent < 3 mins) 2. Browser Monitor
        var sourceUrl = AppMonitorService.currentUrl
        if (!pendingUrl.isNullOrEmpty() && (System.currentTimeMillis() - lastUrlCaptureTime < 180_000)) {
            sourceUrl = pendingUrl
            // Consume it
            pendingUrl = null
        }
        
        val inputData = Data.Builder()
            .putString("video_path", videoFile.absolutePath)
            .putString("source_package", packageName)
            // App name will be resolved in UI or Worker using PackageManager
            .putString("app_name", "")
            .putLong("trigger_time_ms", actualPreRollUs / 1000)
            .putString("source_url", sourceUrl)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<VideoAnalysisWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        Log.i(TAG, "Scheduled analysis for ${videoFile.name}")
    }

    private fun updateCaptureWindow(preMillis: Long, postMillis: Long) {
        val preUs = (preMillis * 1000L).coerceIn(1_000_000L, BUFFER_CAPACITY_US)
        val postUs = (postMillis * 1000L).coerceIn(1_000_000L, BUFFER_CAPACITY_US)
        preCaptureUs = preUs
        postCaptureUs = postUs
        val preSeconds = (preUs / 1_000_000L).toInt()
        val postSeconds = (postUs / 1_000_000L).toInt()
        
        saveWindowSettings(preMillis, postMillis)
        
        showToast(getString(R.string.capture_window_updated, preSeconds, postSeconds))
        Log.i(TAG, "Capture window updated: pre=${preSeconds}s post=${postSeconds}s")
    }

    private fun startVoiceNote() {
        if (voiceRecorder != null) return
        val base = resolveVoiceBase() ?: run {
                Log.w(TAG, "Voice note start ignored: no clip base")
                showToast(getString(R.string.voice_note_no_clip))
            return
        }
        try {
            Log.i(TAG, "Voice note start accepted, base=$base")
            val dir = getAudioDir()
            val file = File(dir, "${base}_voice.m4a")
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                setAudioEncodingBitRate(AUDIO_BIT_RATE)
                setAudioChannels(AUDIO_CHANNEL_COUNT)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            voiceRecorder = recorder
            voiceFile = file
            showToast(getString(R.string.voice_note_recording))
            Log.i(TAG, "Voice note recording started: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice note recording", e)
            showToast(getString(R.string.voice_note_failed))
            stopVoiceNote()
        }
    }

    private fun stopVoiceNote() {
        voiceRecorder?.let { recorder ->
            try {
                recorder.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voice recorder", e)
            }
            try {
                recorder.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing voice recorder", e)
            }
        }
        voiceFile?.let {
            Log.i(TAG, "Voice note saved: ${it.absolutePath}")
            showToast(getString(R.string.voice_note_saved))
        }
        voiceRecorder = null
        voiceFile = null
    }

    private fun resolveVoiceBase(): String? {
        currentClipBase?.let { return it }
        lastClipBase?.let { return it }
        val latest = getVideoDir()
            .listFiles { file -> file.isFile && file.extension.equals("mp4", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
        return latest?.nameWithoutExtension?.also { lastClipBase = it }
    }

    private fun normalizedInfo(
        original: MediaCodec.BufferInfo,
        basePtsUs: Long
    ): MediaCodec.BufferInfo {
        val pts = if (basePtsUs >= 0) {
            (original.presentationTimeUs - basePtsUs).coerceAtLeast(0L)
        } else {
            original.presentationTimeUs
        }
        return MediaCodec.BufferInfo().apply {
            set(0, original.size, pts, original.flags)
        }
    }

    private fun startAudioComponents() {
        startAudioEncoder()
        startPlaybackCapture()
    }

    private fun stopAudioComponents() {
        stopAudioCapture()
        stopAudioEncoder()
    }

    private fun startAudioEncoder() {
        if (audioEncoder != null) return
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            AUDIO_SAMPLE_RATE,
            AUDIO_CHANNEL_COUNT
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_BUFFER_SIZE_BYTES)
        }
        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            setCallback(audioCallback)
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        audioPtsUs = 0L
    }

    private fun stopAudioEncoder() {
        audioEncoder?.let { codec ->
            try {
                codec.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio encoder", e)
            }
            try {
                codec.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing audio encoder", e)
            }
        }
        audioEncoder = null
        audioOutputFormat = null
        audioTrackIndex = -1
        audioPtsUs = 0L
        audioBuffer.clear()
    }

    private fun startPlaybackCapture() {
        if (audioCaptureActive) return
        audioQueue.clear()
        val playback = createPlaybackRecorder()
        if (playback == null) {
            Log.w(TAG, "Audio capture unavailable on this device")
            return
        }
        playbackRecorder = playback
        audioCaptureActive = true
        audioThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            startRecorder(playbackRecorder)
            val playbackBuffer = ShortArray(AUDIO_BUFFER_FRAMES)
            val mixBuffer = ShortArray(AUDIO_BUFFER_FRAMES)
            try {
                while (audioCaptureActive) {
                    val frames = playbackRecorder?.read(
                        playbackBuffer,
                        0,
                        playbackBuffer.size,
                        AudioRecord.READ_BLOCKING
                    ) ?: 0
                    if (frames <= 0) continue
                    System.arraycopy(playbackBuffer, 0, mixBuffer, 0, frames)
                    audioQueue.offer(shortsToBytes(mixBuffer, frames))
                }
            } finally {
                stopAndReleaseRecorder(playbackRecorder)
                playbackRecorder = null
                audioCaptureActive = false
                audioThread = null
            }
        }.apply { start() }
    }

    private fun stopAudioCapture() {
        if (!audioCaptureActive) return
        audioCaptureActive = false
        stopRecorder(micRecorder)
        stopRecorder(playbackRecorder)
        try {
            audioThread?.join(200)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        audioThread = null
        stopAndReleaseRecorder(micRecorder)
        stopAndReleaseRecorder(playbackRecorder)
        micRecorder = null
        playbackRecorder = null
        audioQueue.clear()
    }

    private fun startRecorder(recorder: AudioRecord?) {
        try {
            recorder?.startRecording()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start AudioRecord", e)
        }
    }

    private fun stopRecorder(recorder: AudioRecord?) {
        try {
            recorder?.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to stop AudioRecord", e)
        }
    }

    private fun stopAndReleaseRecorder(recorder: AudioRecord?) {
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release AudioRecord", e)
        }
    }

    private fun createMicRecorder(): AudioRecord? {
        return try {
            val minSize = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = max(minSize, AUDIO_BUFFER_SIZE_BYTES)
            AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AUDIO_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
            .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init mic recorder", e)
            null
        }
    }

    private fun createPlaybackRecorder(): AudioRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val projection = mediaProjection ?: return null
        return try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .build()
            AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(AUDIO_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setAudioPlaybackCaptureConfig(config)
                .setBufferSizeInBytes(AUDIO_BUFFER_SIZE_BYTES)
                .build()
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init playback recorder", e)
            null
        }
    }

    private fun mixSamples(
        micBuffer: ShortArray?,
        micRead: Int,
        playbackBuffer: ShortArray?,
        playbackRead: Int,
        outBuffer: ShortArray,
        frames: Int
    ) {
        for (i in 0 until frames) {
            val micSample = if (micBuffer != null && i < micRead) micBuffer[i].toInt() else 0
            val playbackSample = if (playbackBuffer != null && i < playbackRead) playbackBuffer[i].toInt() else 0
            val mixed = (micSample + playbackSample).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outBuffer[i] = mixed.toShort()
        }
    }

    private fun shortsToBytes(buffer: ShortArray, frames: Int): ByteArray {
        val bytes = ByteArray(frames * BYTES_PER_SAMPLE)
        var index = 0
        for (i in 0 until frames) {
            val sample = buffer[i].toInt()
            bytes[index++] = (sample and 0xFF).toByte()
            bytes[index++] = ((sample shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun nextAudioPts(byteCount: Int): Long {
        val pts = audioPtsUs
        if (byteCount > 0) {
            val frames = byteCount / (BYTES_PER_SAMPLE * AUDIO_CHANNEL_COUNT)
            audioPtsUs += frames * 1_000_000L / AUDIO_SAMPLE_RATE
        }
        return pts
    }

    private fun pauseCapture(flushClip: Boolean) {
        if (flushClip && isSaving) {
            finishSaving()
        }
        isCapturing = false
        stopAudioComponents()
        audioQueue.clear()
        audioBuffer.clear()
        circularBuffer.clear()
        Log.i(TAG, "Capture paused")
    }

    private fun createOutputFile(): File {
        val dir = getVideoDir()
        val base = generateBaseName()
        currentClipBase = base
        return File(dir, "$base.mp4")
    }

    private fun releaseResources() {
        stopAudioComponents()
        stopVoiceNote()
        virtualDisplay?.release()
        virtualDisplay = null
        inputSurface?.release()
        inputSurface = null
        encoder?.stop()
        encoder?.release()
        encoder = null
        mediaProjection?.stop()
        mediaProjection = null
        circularBuffer.clear()
        muxer = null
        muxerTrackIndex = -1
        isSaving = false
        targetEndTimeUs = 0L
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recorder_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recorder_notification_title))
            .setContentText(getString(R.string.recorder_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun getDayBaseDir(date: Date = Date()): File {
        val rootDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "FlashPick")
        val dayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        val dayDir = File(rootDir, dayStr)
        if (!dayDir.exists()) dayDir.mkdirs()
        return dayDir
    }

    private fun getVideoDir(date: Date = Date()): File {
        val dir = File(getDayBaseDir(date), "video")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getAudioDir(date: Date = Date()): File {
        val dir = File(getDayBaseDir(date), "audio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun generateBaseName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "clip_${formatter.format(Date())}"
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadWindowSettings() {
        val prefs = getSharedPreferences("flashpick_prefs", Context.MODE_PRIVATE)
        val preMs = prefs.getLong("pre_capture_ms", DEFAULT_PRE_CAPTURE_US / 1000)
        val postMs = prefs.getLong("post_capture_ms", DEFAULT_POST_CAPTURE_US / 1000)
        
        preCaptureUs = (preMs * 1000).coerceIn(1_000_000L, BUFFER_CAPACITY_US)
        postCaptureUs = (postMs * 1000).coerceIn(1_000_000L, BUFFER_CAPACITY_US)
    }

    private fun saveWindowSettings(preMs: Long, postMs: Long) {
        val prefs = getSharedPreferences("flashpick_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("pre_capture_ms", preMs)
            .putLong("post_capture_ms", postMs)
            .apply()
    }

    companion object {
        private const val TAG = "ScreenRecorderService"
        private const val CHANNEL_ID = "flashpick_recorder"
        private const val NOTIFICATION_ID = 2001
        private const val BIT_RATE = 4_000_000
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1
        private const val DEFAULT_PRE_CAPTURE_US = 5_000_000L
        private const val DEFAULT_POST_CAPTURE_US = 5_000_000L
        private const val BUFFER_CAPACITY_US = 10_000_000L
        private const val AUDIO_SAMPLE_RATE = 44_100
        private const val AUDIO_CHANNEL_COUNT = 1
        private const val AUDIO_BIT_RATE = 128_000
        private const val AUDIO_BUFFER_FRAMES = 2048
        private const val BYTES_PER_SAMPLE = 2 * AUDIO_CHANNEL_COUNT
        private const val AUDIO_BUFFER_SIZE_BYTES = AUDIO_BUFFER_FRAMES * BYTES_PER_SAMPLE
        private const val CAPTURE_MIC_IN_BUFFER = false
        private const val ACTION_START_CAPTURE = "flashpick.action.START_CAPTURE"
        private const val ACTION_STOP_CAPTURE = "flashpick.action.STOP_CAPTURE"
        private const val ACTION_REQUEST_CLIP = "flashpick.action.REQUEST_CLIP"
        private const val ACTION_SET_WINDOW = "flashpick.action.SET_WINDOW"
        private const val ACTION_START_VOICE_NOTE = "flashpick.action.START_VOICE_NOTE"
        private const val ACTION_STOP_VOICE_NOTE = "flashpick.action.STOP_VOICE_NOTE"
        private const val EXTRA_PRE_MS = "extra_pre_ms"
        private const val EXTRA_POST_MS = "extra_post_ms"

        fun start(context: Context) {
            val permission = ScreenRecorderPermissionStore.current()
            if (permission == null) {
                Log.w(TAG, "Cannot start ScreenRecorderService without permission")
                return
            }
            val intent = Intent(context, ScreenRecorderService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun startCapture(context: Context) {
            val permission = ScreenRecorderPermissionStore.current()
            if (permission == null) {
                Log.w(TAG, "Cannot start capture without permission")
                return
            }
            val intent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_START_CAPTURE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopCapture(context: Context) {
            val intent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_STOP_CAPTURE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestClip(context: Context, preMillis: Long? = null, postMillis: Long? = null) {
            val permission = ScreenRecorderPermissionStore.current()
            if (permission == null) {
                Log.w(TAG, "Cannot request clip without permission")
                return
            }
            val intent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_REQUEST_CLIP
                if (preMillis != null) putExtra(EXTRA_PRE_MS, preMillis)
                if (postMillis != null) putExtra(EXTRA_POST_MS, postMillis)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun setWindow(context: Context, preMillis: Long, postMillis: Long) {
            val intent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_SET_WINDOW
                putExtra(EXTRA_PRE_MS, preMillis)
                putExtra(EXTRA_POST_MS, postMillis)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startVoiceNote(context: Context) {
            val intent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_START_VOICE_NOTE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopVoiceNote(context: Context) {
            val intent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_STOP_VOICE_NOTE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenRecorderService::class.java)
            context.stopService(intent)
        }
    }
}
