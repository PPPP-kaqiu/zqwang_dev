package com.flashpick.app.recorder

object RecorderPermissionChecker {
    fun isPointerValid(): Boolean = ScreenRecorderPermissionStore.hasPermission()
}

