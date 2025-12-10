package com.flashpick.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.flashpick.app.R
import com.flashpick.app.recorder.ScreenRecorderPermissionActivity
import com.flashpick.app.recorder.ScreenRecorderPermissionStore
import com.flashpick.app.recorder.ScreenRecorderService
import com.flashpick.app.service.AppMonitorService
import kotlin.math.roundToInt

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    
    // --- Pet Window ---
    private var petView: View? = null
    private lateinit var petParams: WindowManager.LayoutParams
    private var bubbleCore: View? = null
    private var leftEye: View? = null
    private var rightEye: View? = null
    
    // --- Menu Window ---
    private var menuView: View? = null
    private lateinit var menuParams: WindowManager.LayoutParams
    private var isMenuVisible = false
    
    // Buttons
    private var btnCustom: View? = null // Top
    private var btnTalk: View? = null   // Left
    private var btnNote: View? = null   // Right

    // --- Time Selector Window ---
    private var timeSelectorView: View? = null
    private lateinit var timeSelectorParams: WindowManager.LayoutParams

    // --- AI Bubble Window ---
    private var aiBubbleView: View? = null
    private lateinit var aiBubbleParams: WindowManager.LayoutParams

    private var voiceNoteActive = false
    private val gestureHandler = Handler(Looper.getMainLooper())
    private val menuHideRunnable = Runnable { hideMenu() }

    // Blink Logic
    private val blinkHandler = Handler(Looper.getMainLooper())
    private val blinkRunnable = object : Runnable {
        override fun run() {
            blink()
            val delay = (2000L..6000L).random()
            blinkHandler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        windowManager = getSystemService(WindowManager::class.java)
        
        initPetView()
        initMenuView()
        initTimeSelectorView()
        // initAiBubbleView() // TODO: Feature 2
        
        addPetOverlay()
        
        blinkHandler.post(blinkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        blinkHandler.removeCallbacks(blinkRunnable)
        removeOverlays()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initPetView() {
        val bubble = LayoutInflater.from(this).inflate(R.layout.view_overlay_pet, FrameLayout(this), false)
        bubbleCore = bubble
        leftEye = bubble.findViewById(R.id.petEyeLeft)
        rightEye = bubble.findViewById(R.id.petEyeRight)

        val metrics = resources.displayMetrics
        val petSize = dpToPx(60).toInt()
        val halfSize = petSize / 2

        petParams = WindowManager.LayoutParams(
            petSize,
            petSize,
            getWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val prefs = getSharedPreferences("flashpick_overlay_prefs", Context.MODE_PRIVATE)
            val savedX = prefs.getInt("overlay_x", 100)
            val savedY = prefs.getInt("overlay_y", 300)
            x = savedX.coerceIn(-halfSize, metrics.widthPixels - halfSize)
            y = savedY.coerceIn(0, metrics.heightPixels - halfSize * 2)
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var lastClickTime = 0L
        val doubleClickTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

        val singleClickRunnable = Runnable { toggleMenu() }

        bubble.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = petParams.x
                    initialY = petParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    bubbleCore?.animate()?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(100)?.start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - initialTouchX)
                    val dy = Math.abs(event.rawY - initialTouchY)
                    if (!isDragging && (dx > touchSlop || dy > touchSlop)) {
                        isDragging = true
                        gestureHandler.removeCallbacks(singleClickRunnable)
                        if (isMenuVisible) hideMenu()
                    }
                    if (isDragging) {
                        petParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        petParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(petView, petParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    bubbleCore?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.setInterpolator(OvershootInterpolator())?.start()
                    if (isDragging) {
                        val prefs = getSharedPreferences("flashpick_overlay_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putInt("overlay_x", petParams.x).putInt("overlay_y", petParams.y).apply()
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < doubleClickTimeout) {
                            gestureHandler.removeCallbacks(singleClickRunnable)
                            performClipAction()
                            happySquint()
                            lastClickTime = 0
                        } else {
                            lastClickTime = now
                            gestureHandler.postDelayed(singleClickRunnable, doubleClickTimeout)
                        }
                    }
                    true
                }
                else -> false
            }
        }
        petView = bubble
    }

    private fun initMenuView() {
        val menu = LayoutInflater.from(this).inflate(R.layout.view_overlay_menu, FrameLayout(this), false)
        btnCustom = menu.findViewById(R.id.buttonCustom)
        btnTalk = menu.findViewById(R.id.buttonTalk)
        btnNote = menu.findViewById(R.id.buttonNote)
        
        val menuSize = dpToPx(180).toInt()
        menuParams = WindowManager.LayoutParams(
            menuSize, menuSize, getWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        // 1. Custom Clip (Slider)
        btnCustom?.setOnClickListener {
            hideMenu()
            showTimeSelector()
        }

        // 2. AI Talk (Hold to Ask)
        setupHoldButton(btnTalk) { isStart ->
            if (isStart) {
                ScreenRecorderService.startVoiceNote(this)
                // TODO: Flag this as a question
            } else {
                ScreenRecorderService.stopVoiceNote(this)
                hideMenu(force = true)
                // TODO: Show AI Bubble
            }
        }

        // 3. Voice Note (Hold to Record)
        setupHoldButton(btnNote) { isStart ->
            if (isStart) {
                ScreenRecorderService.startVoiceNote(this)
            } else {
                ScreenRecorderService.stopVoiceNote(this)
                hideMenu(force = true)
            }
        }
        
        menuView = menu
    }

    private fun setupHoldButton(view: View?, action: (Boolean) -> Unit) {
        view?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!voiceNoteActive) {
                        action(true)
                        voiceNoteActive = true
                        view.isPressed = true
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        gestureHandler.removeCallbacks(menuHideRunnable)
                        leftEye?.animate()?.scaleX(1.5f)?.scaleY(1.5f)?.setDuration(200)?.start()
                        rightEye?.animate()?.scaleX(1.5f)?.scaleY(1.5f)?.setDuration(200)?.start()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (voiceNoteActive) {
                        action(false)
                        view.isPressed = false
                        voiceNoteActive = false
                        leftEye?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.start()
                        rightEye?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun initTimeSelectorView() {
        val view = LayoutInflater.from(this).inflate(R.layout.view_time_selector, FrameLayout(this), false)
        val tvPre = view.findViewById<TextView>(R.id.tvPreTime)
        val sbPre = view.findViewById<SeekBar>(R.id.seekBarPre)
        val tvPost = view.findViewById<TextView>(R.id.tvPostTime)
        val sbPost = view.findViewById<SeekBar>(R.id.seekBarPost)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirm)

        sbPre.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvPre.text = "保留前 $progress 秒"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbPost.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvPost.text = "录制后 $progress 秒"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnConfirm.setOnClickListener {
            val pre = sbPre.progress.toLong() * 1000
            val post = sbPost.progress.toLong() * 1000
            ScreenRecorderService.requestClip(this, pre, post)
            if (timeSelectorView?.parent != null) windowManager?.removeView(timeSelectorView)
        }

        timeSelectorParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        
        timeSelectorView = view
    }

    private fun showTimeSelector() {
        if (timeSelectorView?.parent == null) {
            windowManager?.addView(timeSelectorView, timeSelectorParams)
        }
    }

    private fun showMenu() {
        if (isMenuVisible) return
        
        val prefs = getSharedPreferences("flashpick_overlay_prefs", Context.MODE_PRIVATE)
        val radiusDp = prefs.getInt("overlay_radius", 60)
        val sizeDp = prefs.getInt("overlay_size", 180)
        
        val menuSizePx = dpToPx(sizeDp).toInt()
        if (menuParams.width != menuSizePx) {
            menuParams.width = menuSizePx
            menuParams.height = menuSizePx
        }

        val petSize = dpToPx(60).toInt()
        val offset = (menuSizePx - petSize) / 2
        
        menuParams.x = petParams.x - offset
        menuParams.y = petParams.y - offset
        
        try {
            if (menuView?.parent == null) windowManager?.addView(menuView, menuParams)
            else windowManager?.updateViewLayout(menuView, menuParams)
        } catch (e: Exception) { return }
        
        isMenuVisible = true
        menuView?.visibility = View.VISIBLE

        // Animation Config
        val interpolator = OvershootInterpolator(1.2f)
        val duration = 300L
        val dist = dpToPx(radiusDp)

        // Reset positions
        btnCustom?.translationX = 0f; btnCustom?.translationY = 0f; btnCustom?.alpha = 0f
        btnTalk?.translationX = 0f; btnTalk?.translationY = 0f; btnTalk?.alpha = 0f
        btnNote?.translationX = 0f; btnNote?.translationY = 0f; btnNote?.alpha = 0f

        // Calculate positions for Right Side arc
        // Top-Right (-60 deg)
        val angleTopRight = Math.toRadians(-60.0)
        val x1 = (dist * Math.cos(angleTopRight)).toFloat()
        val y1 = (dist * Math.sin(angleTopRight)).toFloat()

        // Right (0 deg)
        val x2 = dist
        val y2 = 0f

        // Bottom-Right (60 deg)
        val angleBottomRight = Math.toRadians(60.0)
        val x3 = (dist * Math.cos(angleBottomRight)).toFloat()
        val y3 = (dist * Math.sin(angleBottomRight)).toFloat()

        // Animate: Custom -> Top-Right
        btnCustom?.animate()
            ?.translationX(x1)?.translationY(y1)
            ?.alpha(1f)?.setInterpolator(interpolator)?.setDuration(duration)?.start()
            
        // Animate: Talk -> Right
        btnTalk?.animate()
            ?.translationX(x2)?.translationY(y2)
            ?.alpha(1f)?.setInterpolator(interpolator)?.setDuration(duration)?.start()
            
        // Animate: Note -> Bottom-Right
        btnNote?.animate()
            ?.translationX(x3)?.translationY(y3)
            ?.alpha(1f)?.setInterpolator(interpolator)?.setDuration(duration)?.start()

        val eyeLookUp = -dpToPx(4)
        leftEye?.animate()?.translationY(eyeLookUp)?.setDuration(duration)?.start()
        rightEye?.animate()?.translationY(eyeLookUp)?.setDuration(duration)?.start()

        scheduleMenuAutoHide()
    }

    private fun hideMenu(force: Boolean = false) {
        if (voiceNoteActive && !force) return
        if (!isMenuVisible) return

        val duration = 200L
        btnCustom?.animate()?.translationX(0f)?.translationY(0f)?.alpha(0f)?.setDuration(duration)?.start()
        btnTalk?.animate()?.translationX(0f)?.translationY(0f)?.alpha(0f)?.setDuration(duration)?.start()
        btnNote?.animate()?.translationX(0f)?.translationY(0f)?.alpha(0f)?.setDuration(duration)?.withEndAction {
            if (isMenuVisible) {
                try { if (menuView?.parent != null) windowManager?.removeView(menuView) } catch (e: Exception) {}
                isMenuVisible = false
            }
        }?.start()
            
        leftEye?.animate()?.translationY(0f)?.setDuration(duration)?.start()
        rightEye?.animate()?.translationY(0f)?.setDuration(duration)?.start()
        gestureHandler.removeCallbacks(menuHideRunnable)
    }

    private fun addPetOverlay() {
        try {
            if (petView?.parent == null) windowManager?.addView(petView, petParams)
        } catch (e: Exception) {
            Log.e("OverlayService", "Error adding pet overlay", e)
        }
    }

    private fun removeOverlays() {
        if (petView?.parent != null) windowManager?.removeView(petView)
        if (menuView?.parent != null) windowManager?.removeView(menuView)
        if (timeSelectorView?.parent != null) windowManager?.removeView(timeSelectorView)
    }

    // ... (Helpers: blink, happySquint, notification, etc. same as before) ...
    private fun blink() {
        val duration = 150L
        leftEye?.animate()?.scaleY(0.1f)?.setDuration(duration)?.withEndAction {
            leftEye?.animate()?.scaleY(1f)?.setDuration(duration)?.start()
        }?.start()
        rightEye?.animate()?.scaleY(0.1f)?.setDuration(duration)?.withEndAction {
            rightEye?.animate()?.scaleY(1f)?.setDuration(duration)?.start()
        }?.start()
    }
    
    private fun happySquint() {
        val duration = 200L
        leftEye?.animate()?.scaleY(0.2f)?.setDuration(duration)?.withEndAction {
             Handler(Looper.getMainLooper()).postDelayed({
                 leftEye?.animate()?.scaleY(1f)?.setDuration(duration)?.start()
             }, 500)
        }?.start()
        rightEye?.animate()?.scaleY(0.2f)?.setDuration(duration)?.withEndAction {
             Handler(Looper.getMainLooper()).postDelayed({
                 rightEye?.animate()?.scaleY(1f)?.setDuration(duration)?.start()
             }, 500)
        }?.start()
        bubbleCore?.animate()?.translationY(-20f)?.setDuration(150)?.withEndAction {
            bubbleCore?.animate()?.translationY(0f)?.setInterpolator(OvershootInterpolator())?.setDuration(300)?.start()
        }?.start()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.overlay_notification_channel), NotificationManager.IMPORTANCE_MIN)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun getWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun dpToPx(dp: Int): Float = dp * resources.displayMetrics.density

    private fun toggleMenu() {
        if (isMenuVisible) hideMenu() else showMenu()
    }

    private fun scheduleMenuAutoHide() {
        gestureHandler.removeCallbacks(menuHideRunnable)
        if (!voiceNoteActive) gestureHandler.postDelayed(menuHideRunnable, 5000L) // 5s
    }

    private fun performClipAction() {
        if (ScreenRecorderPermissionStore.hasPermission()) {
            if (!AppMonitorService.isConnected()) {
                Toast.makeText(applicationContext, "服务未连接", Toast.LENGTH_SHORT).show()
            }
            ScreenRecorderService.requestClip(this@OverlayService)
        } else {
            val intent = Intent(this@OverlayService, ScreenRecorderPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
    
    companion object {
        private const val CHANNEL_ID = "flashpick_overlay"
        private const val NOTIFICATION_ID = 1001
        
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, OverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }
    }
}

