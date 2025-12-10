package com.flashpick.app.ui.components

import android.content.Context

object AppInfoCache {
    private val cache = mutableMapOf<String, String>()
    
    fun getCachedName(packageName: String): String? {
        return cache[packageName]
    }
    
    fun resolveName(context: Context, packageName: String): String {
        if (cache.containsKey(packageName)) return cache[packageName]!!
        val name = try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            if (packageName == "unknown" || packageName.isEmpty()) "App" else packageName
        }
        cache[packageName] = name
        return name
    }
}

