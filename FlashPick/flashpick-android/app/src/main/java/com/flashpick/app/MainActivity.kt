package com.flashpick.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.flashpick.app.data.AppDatabase
import com.flashpick.app.permissions.PermissionManager
import com.flashpick.app.recorder.ScreenRecorderController
import com.flashpick.app.recorder.ScreenRecorderPermissionStore
import com.flashpick.app.recorder.ScreenRecorderService
import com.flashpick.app.service.AppMonitorService
import com.flashpick.app.service.ServiceWatcher
import com.flashpick.app.ui.screens.home.GalleryScreen
import com.flashpick.app.ui.theme.FlashPickTheme
import com.flashpick.app.whitelist.WhitelistActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var screenRecorderController: ScreenRecorderController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this)
        screenRecorderController = ScreenRecorderController(this)
        
        // Handle share intent if app started via share
        handleShareIntent(intent)

        val runtimePermissions = arrayOf(Manifest.permission.RECORD_AUDIO)

        setContent {
            var runtimeGranted by remember {
                mutableStateOf(permissionManager.arePermissionsGranted(runtimePermissions))
            }
            var overlayGranted by remember {
                mutableStateOf(permissionManager.hasOverlayPermission())
            }
            var accessibilityEnabled by remember {
                mutableStateOf(AppMonitorService.isEnabled(this))
            }
            var recorderPermissionGranted by remember {
                mutableStateOf(ScreenRecorderPermissionStore.hasPermission())
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        accessibilityEnabled = AppMonitorService.isEnabled(this@MainActivity)
                        runtimeGranted = permissionManager.arePermissionsGranted(runtimePermissions)
                        overlayGranted = permissionManager.hasOverlayPermission()
                        recorderPermissionGranted = ScreenRecorderPermissionStore.hasPermission()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(accessibilityEnabled) {
                if (accessibilityEnabled) {
                    ServiceWatcher.start(this@MainActivity)
                } else {
                    ServiceWatcher.stop()
                }
            }

            FlashPickTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GalleryScreen(
                        runtimeGranted = runtimeGranted,
                        overlayGranted = overlayGranted,
                        accessibilityEnabled = accessibilityEnabled,
                        recorderPermissionGranted = recorderPermissionGranted,
                        onRequestRuntime = {
                            permissionManager.requestRuntimePermissions(runtimePermissions) { result ->
                                runtimeGranted = result.values.all { it }
                            }
                        },
                        onRequestOverlay = {
                            permissionManager.requestOverlayPermission { granted ->
                                overlayGranted = granted
                            }
                        },
                        onOpenAccessibility = { launchAccessibilitySettings() },
                        onRequestRecorder = {
                            screenRecorderController.requestMediaProjection { permission ->
                                ScreenRecorderPermissionStore.update(permission)
                                recorderPermissionGranted = permission != null
                            }
                        },
                        onManageWhitelist = {
                            startActivity(Intent(this@MainActivity, WhitelistActivity::class.java))
                        },
                        onUpdateCaptureWindow = { preMs, postMs ->
                            ScreenRecorderService.setWindow(this@MainActivity, preMs, postMs)
                        },
                        onUpdateOverlayDebug = { radius, size ->
                            getSharedPreferences("flashpick_overlay_prefs", MODE_PRIVATE)
                                .edit()
                                .putInt("overlay_radius", radius)
                                .putInt("overlay_size", size)
                                .apply()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            
            // Basic URL extraction regex
            val urlRegex = "(https?://\\S+)".toRegex()
            val match = urlRegex.find(sharedText)
            val url = match?.value ?: sharedText 
            
            if (url.isNotEmpty()) {
                lifecycleScope.launch {
                    val dao = AppDatabase.getDatabase(this@MainActivity).videoRecordDao()
                    val latest = dao.getLatestRecord()
                    
                    if (latest != null) {
                        // Check if it's recent (e.g., within 10 minutes)
                        val now = System.currentTimeMillis()
                        if (now - latest.createdAt < 10 * 60 * 1000) {
                            dao.updateRecordDetails(latest.id, latest.title ?: "", latest.summary ?: "", url)
                            Toast.makeText(this@MainActivity, "链接已关联到最新记忆", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "未找到最近的记忆 (10分钟内)", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "暂无记忆可关联", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun launchAccessibilitySettings() {
        val componentName = ComponentName(this, AppMonitorService::class.java)
        
        Toast.makeText(this, "请在列表中找到 FlashPick 并开启", Toast.LENGTH_LONG).show()

        val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = Uri.parse("package:$packageName")
            putExtra("android.intent.extra.COMPONENT_NAME", componentName.flattenToString())
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w("Accessibility", "Failed to launch details settings, falling back to list", e)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
