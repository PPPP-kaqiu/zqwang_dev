package com.flashpick.app.recorder

import android.os.Bundle
import androidx.activity.ComponentActivity

class ScreenRecorderPermissionActivity : ComponentActivity() {

    private lateinit var controller: ScreenRecorderController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = ScreenRecorderController(this)
        requestPermission()
    }

    private fun requestPermission() {
        controller.requestMediaProjection { permission ->
            if (permission != null) {
                ScreenRecorderPermissionStore.update(permission)
                ScreenRecorderService.start(this)
            } else {
                ScreenRecorderPermissionStore.clear()
            }
            finish()
        }
    }
}

