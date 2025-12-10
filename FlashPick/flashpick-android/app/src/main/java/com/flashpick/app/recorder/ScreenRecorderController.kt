package com.flashpick.app.recorder

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class ScreenRecorderController(
    private val activity: ComponentActivity
) {

    private val mediaProjectionManager: MediaProjectionManager by lazy {
        activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var permissionCallback: ((ScreenRecorderPermission?) -> Unit)? = null

    private val projectionLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                permissionCallback?.invoke(
                    ScreenRecorderPermission(
                        result.resultCode,
                        result.data!!
                    )
                )
            } else {
                permissionCallback?.invoke(null)
            }
            permissionCallback = null
        }

    fun requestMediaProjection(callback: (ScreenRecorderPermission?) -> Unit) {
        permissionCallback = callback
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        projectionLauncher.launch(intent)
    }

    data class ScreenRecorderPermission(
        val resultCode: Int,
        val data: Intent
    )
}

