package com.flashpick.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flashpick.app.data.AppDatabase
import com.flashpick.app.data.model.VideoRecord
import com.flashpick.app.data.repository.AsrRepository
import com.flashpick.app.data.repository.VlmRepository
import com.flashpick.app.utils.VideoUtils
import com.google.gson.Gson
import java.io.File

class VideoAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val videoPath = inputData.getString("video_path") ?: return Result.failure()
        val videoFile = File(videoPath)
        
        if (!videoFile.exists()) return Result.failure()

        val dao = AppDatabase.getDatabase(applicationContext).videoRecordDao()
        
        // Read trigger time from input (actual pre-roll duration)
        val triggerTimeMs = inputData.getLong("trigger_time_ms", 0L)
        val sourceUrl = inputData.getString("source_url")

        // 1. Ensure record exists in DB (or insert it)
        var record = dao.getByPath(videoPath)
        if (record == null) {
            // Create initial record
            // Use triggerTimeMs to capture the thumbnail at the exact trigger moment
            val thumbFile = VideoUtils.extractAndSaveThumbnail(videoFile, atTimeMs = if (triggerTimeMs > 0) triggerTimeMs else null)
            val thumbPath = thumbFile?.absolutePath ?: ""
            
            record = VideoRecord(
                filePath = videoPath,
                thumbnailPath = thumbPath,
                createdAt = videoFile.lastModified(),
                durationMs = 0, // TODO: Get real duration
                sizeBytes = videoFile.length(),
                sourcePackage = inputData.getString("source_package") ?: "",
                appName = inputData.getString("app_name") ?: "",
                url = sourceUrl
            )
            val id = dao.insert(record)
            record = record.copy(id = id)
        } else if (!sourceUrl.isNullOrEmpty() && record.url.isNullOrEmpty()) {
            // Update URL if it was missing
            dao.updateRecordDetails(record.id, record.title ?: "", record.summary ?: "", sourceUrl)
            record = record.copy(url = sourceUrl)
        }

        // 2. Extract Thumbnail if missing (Fallback)
        var thumbFile = if (record.thumbnailPath.isNotEmpty()) File(record.thumbnailPath) else null
        if (thumbFile == null || !thumbFile.exists()) {
             thumbFile = VideoUtils.extractAndSaveThumbnail(videoFile, atTimeMs = if (triggerTimeMs > 0) triggerTimeMs else null)
             // Update record with new thumb path if needed (omitted for brevity)
        }

        if (thumbFile == null) return Result.failure()

        // 3. Call AI
        if (!VlmRepository.hasApiKey()) {
            return Result.success() 
        }

        return try {
            // Check for Transcript (Real-time or Voice Note)
            var transcript: String? = null

            // 1. Check for real-time transcript text file first
            val txtFile = File(videoFile.parentFile, "${videoFile.nameWithoutExtension}.txt")
            if (txtFile.exists()) {
                transcript = txtFile.readText()
            }

            // 2. If no text file, check for voice note audio and transcribe it
            if (transcript.isNullOrEmpty()) {
                val audioDir = File(videoFile.parentFile.parentFile, "audio")
                val audioFilename = "${videoFile.nameWithoutExtension}_voice.m4a"
                val audioFile = File(audioDir, audioFilename)

                if (audioFile.exists()) {
                    transcript = AsrRepository.transcribe(audioFile)
                }
            }

            // Extract multiple keyframes using hybrid strategy (Uniform 5 + Focus 3)
            val keyFrames = VideoUtils.extractKeyFrames(videoFile, count = 5, focusTimeMs = if (triggerTimeMs > 0) triggerTimeMs else null)
            val nonNullThumb = thumbFile!! // Safe because of check at line 53
            val framesToAnalyze = if (keyFrames.isNotEmpty()) keyFrames else listOf(nonNullThumb)

            val analysis = VlmRepository.analyzeImage(framesToAnalyze, transcript)
            
            // 4. Update DB
            val gson = Gson()
            dao.updateAiResult(
                id = record.id,
                title = analysis.title,
                summary = analysis.summary,
                keywords = gson.toJson(analysis.keywords),
                entities = gson.toJson(analysis.entities),
                keyFrames = gson.toJson(keyFrames.map { it.absolutePath }),
                appName = analysis.app_name
            )
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
