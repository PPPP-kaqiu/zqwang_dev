package com.flashpick.app.monitor

import android.content.Context

object WhiteListManager {
    private val defaultPackages = setOf(
        "com.xingin.xhs",
        "tv.danmaku.bili"
    )

    private const val PREF_NAME = "flashpick_whitelist"
    private const val KEY_PACKAGES = "packages"

    fun getPackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_PACKAGES, null)
        return stored?.toSet() ?: defaultPackages
    }

    fun savePackages(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    fun isTarget(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return getPackages(context).contains(packageName)
    }
}

