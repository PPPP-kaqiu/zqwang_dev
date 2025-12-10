package com.flashpick.app.permissions

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: ComponentActivity) {

    private var runtimeCallback: ((Map<String, Boolean>) -> Unit)? = null
    private var overlayCallback: ((Boolean) -> Unit)? = null

    private val runtimeLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        runtimeCallback?.invoke(result)
        runtimeCallback = null
    }

    private val overlayLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayCallback?.invoke(hasOverlayPermission())
        overlayCallback = null
    }

    fun requestRuntimePermissions(
        permissions: Array<String>,
        callback: (Map<String, Boolean>) -> Unit
    ) {
        runtimeCallback = callback
        runtimeLauncher.launch(permissions)
    }

    fun arePermissionsGranted(permissions: Array<String>): Boolean =
        permissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(activity)

    fun requestOverlayPermission(callback: (Boolean) -> Unit) {
        if (hasOverlayPermission()) {
            callback(true)
            return
        }
        overlayCallback = callback
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        overlayLauncher.launch(intent)
    }
}

