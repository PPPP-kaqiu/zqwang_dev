package com.flashpick.app.recorder

object ScreenRecorderPermissionStore {
    private var permission: ScreenRecorderController.ScreenRecorderPermission? = null

    fun update(permission: ScreenRecorderController.ScreenRecorderPermission?) {
        this.permission = permission
    }

    fun current(): ScreenRecorderController.ScreenRecorderPermission? = permission

    fun hasPermission(): Boolean = permission != null

    fun clear() {
        permission = null
    }
}

