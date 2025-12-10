package com.flashpick.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
import com.flashpick.app.monitor.WhiteListManager
import com.flashpick.app.overlay.OverlayService
import com.flashpick.app.recorder.ScreenRecorderService

class AppMonitorService : AccessibilityService() {

    private var lastPackageName: String? = null
    private var inTargetApp: Boolean = false
    private var lastEventTime = 0L
    private var selfPackage: String? = null
    
    // URL Capture Throttle
    private var lastUrlScanTime = 0L
    private val URL_SCAN_INTERVAL = 1000L // 1 sec throttle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        selfPackage = applicationContext.packageName
        // Note: XML config usually overrides this, but setting it here ensures consistency
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.i(TAG, "AppMonitorService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        
        // 1. App Monitor Logic (Debounced Window State Change)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (packageName == selfPackage) return
            
            val now = SystemClock.elapsedRealtime()
            if (packageName != lastPackageName || now - lastEventTime > DEBOUNCE_MS) {
                lastEventTime = now
                lastPackageName = packageName
                activePackage = packageName
                
                val target = WhiteListManager.isTarget(this, packageName)
                
                if (target) {
                    currentPackage = packageName
                    // Always try to start/keep alive services when in target app
                    // This handles cases where OverlayService might have crashed or been killed
                    inTargetApp = true
                    OverlayService.start(this)
                    ScreenRecorderService.startCapture(this)
                } else {
                    currentPackage = null
                    if (inTargetApp) {
                        // Only stop if we were previously in a target app
                        inTargetApp = false
                        OverlayService.stop(this)
                        ScreenRecorderService.stopCapture(this)
                    }
                }
            }
        }

        // 2. URL Capture Logic (Throttled, only for browsers)
        if (inTargetApp && isBrowser(packageName)) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastUrlScanTime > URL_SCAN_INTERVAL) {
                lastUrlScanTime = now
                val root = rootInActiveWindow
                if (root != null) {
                    val url = findBrowserUrl(root, packageName)
                    if (!url.isNullOrEmpty()) {
                        currentUrl = url
                        Log.d(TAG, "Captured URL: $url")
                    }
                    // root.recycle() // AccessibilityService automatically recycles rootInActiveWindow usually? 
                    // Android docs say "The service must call recycle()", but some sources say rootInActiveWindow is special.
                    // Safe to not recycle here to avoid "called recycle on already recycled" if system does it.
                    // But standard practice is to recycle nodes obtained from queries.
                }
            }
        }
    }

    private fun isBrowser(packageName: String): Boolean {
        return packageName.contains("chrome") || 
               packageName.contains("browser") || 
               packageName.contains("firefox") || 
               packageName.contains("edge") ||
               packageName.contains("safari") // Just in case
    }

    private fun findBrowserUrl(root: AccessibilityNodeInfo, packageName: String): String? {
        // 1. Try Specific IDs first (Fastest)
        val idList = when {
            packageName.contains("chrome") -> listOf("com.android.chrome:id/url_bar", "com.google.android.apps.chrome:id/url_bar")
            packageName.contains("edge") -> listOf("com.microsoft.emmx:id/url_bar")
            packageName.contains("firefox") -> listOf("org.mozilla.firefox:id/url_bar_title")
            else -> listOf("url_bar", "address_bar", "location_bar") // Generic suffixes
        }

        for (id in idList) {
            val nodes = if (id.contains(":")) {
                root.findAccessibilityNodeInfosByViewId(id)
            } else {
                // If generic suffix, we can't use findAccessibilityNodeInfosByViewId efficiently without full ID.
                // We'll rely on recursion for generic cases or skip.
                emptyList()
            }
            
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString()
                    if (!text.isNullOrEmpty()) {
                        return text
                    }
                }
            }
        }

        // 2. Generic Search (Recursive, slower) - Limit depth?
        // Skip for now to save battery, relying on IDs is safer.
        // Or only search if no ID matched.
        return null
    }

    // Auto-Link Capture Logic
    fun triggerLinkAutoCapture() {
        val root = rootInActiveWindow
        if (root == null) {
             Log.w(TAG, "Root window is null")
             return
        }
        val targetPackage = root.packageName?.toString() ?: "unknown"
        Log.i(TAG, "Triggering Auto-Link Capture for $targetPackage")
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(applicationContext, "正在尝试自动抓取链接...", android.widget.Toast.LENGTH_SHORT).show()
        }

        debugDump(root) // Dump UI hierarchy for debugging
        
        // 1. Try to find "Share" button
        val shareNodes = findNodesByText(root, listOf("分享", "Share"))
        // Also try content description for icons
        val shareIconNodes = findNodesByDescription(root, listOf("分享", "Share"))
        // Try known IDs or generic share IDs
        val shareIdNodes = findNodesByIdKeyword(root, "share")

        val allShareNodes = (shareNodes + shareIconNodes + shareIdNodes).distinct()
        
        if (allShareNodes.isNotEmpty()) {
            // Pick the most likely one (e.g. clickable)
            val shareNode = allShareNodes.firstOrNull { it.isClickable } ?: allShareNodes.first()
            Log.i(TAG, "Found Share button: ${shareNode.viewIdResourceName} / ${shareNode.contentDescription}")
            
            if (shareNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.i(TAG, "Clicked Share button")
                
                // Wait for dialog (extended to 1.5s)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val newRoot = rootInActiveWindow
                    if (newRoot != null) {
                        debugDump(newRoot) // Dump share sheet hierarchy

                        val copyNodes = findNodesByText(newRoot, listOf("复制链接", "Copy Link", "复制"))
                        val copyNode = copyNodes.firstOrNull { it.isClickable } ?: copyNodes.firstOrNull()
                        
                        if (copyNode != null) {
                            Log.i(TAG, "Found Copy Link button: ${copyNode.viewIdResourceName} / ${copyNode.text}")
                            if (copyNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                Log.i(TAG, "Clicked Copy Link button")
                            }
                        } else {
                            Log.w(TAG, "Copy Link button not found in share sheet")
                        }
                    }
                }, 1500) 
            }
        } else {
            Log.w(TAG, "Share button not found")
        }
    }

    private fun debugDump(node: AccessibilityNodeInfo, depth: Int = 0) {
        if (depth > 5) return // Limit depth
        val prefix = "  ".repeat(depth)
        Log.d(TAG, "$prefix Node: ${node.className}, ID: ${node.viewIdResourceName}, Text: ${node.text}, Desc: ${node.contentDescription}, Clickable: ${node.isClickable}")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { debugDump(it, depth + 1) }
        }
    }

    private fun findNodesByIdKeyword(root: AccessibilityNodeInfo, keyword: String): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue
            if (node.viewIdResourceName != null && node.viewIdResourceName.contains(keyword, ignoreCase = true)) {
                list.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return list
    }

    private fun findNodesByText(root: AccessibilityNodeInfo, texts: List<String>): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        for (text in texts) {
            list.addAll(root.findAccessibilityNodeInfosByText(text))
        }
        return list
    }

    private fun findNodesByDescription(root: AccessibilityNodeInfo, descriptions: List<String>): List<AccessibilityNodeInfo> {
        // Recursive search for contentDescription
        val list = mutableListOf<AccessibilityNodeInfo>()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue
            if (node.contentDescription != null && descriptions.any { node.contentDescription.toString().contains(it) }) {
                list.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return list
    }

    override fun onInterrupt() {
        inTargetApp = false
        OverlayService.stop(this)
        ServiceWatcher.stop()
    }

    companion object {
        private const val TAG = "AppMonitorService"
        private const val DEBOUNCE_MS = 500L
        
        @Volatile var currentPackage: String? = null
        @Volatile var activePackage: String? = null
        @Volatile var currentUrl: String? = null // Captured URL
        
        private var instance: AppMonitorService? = null
        
        fun isConnected(): Boolean {
            return instance != null
        }

        fun triggerAutoAction() {
            if (instance == null) {
                Log.e(TAG, "Cannot trigger auto action: Service not connected")
                return
            }
            instance?.triggerLinkAutoCapture()
        }
        
        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            val component = ComponentName(context, AppMonitorService::class.java)
            val enabledServices = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            return enabledServices.any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                serviceInfo.packageName == component.packageName && serviceInfo.name == component.className
            }
        }
    }
}
