package com.flashpick.app.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Toast
import com.flashpick.app.R

object ServiceWatcher : Handler.Callback {

    private const val MSG_CHECK = 1
    private const val INTERVAL_MS = 60_000L

    private var handler: Handler? = null
    private var appContext: Context? = null

    fun start(context: Context) {
        appContext = context.applicationContext
        if (handler == null) {
            handler = Handler(Looper.getMainLooper(), this)
        }
        handler?.removeMessages(MSG_CHECK)
        handler?.sendEmptyMessage(MSG_CHECK)
    }

    fun stop() {
        handler?.removeMessages(MSG_CHECK)
        handler = null
    }

    override fun handleMessage(msg: Message): Boolean {
        if (msg.what == MSG_CHECK) {
            val context = appContext ?: return true
            val enabled = AppMonitorService.isEnabled(context)
            if (!enabled) {
                Toast.makeText(context, R.string.accessibility_service_disabled, Toast.LENGTH_SHORT).show()
            }
            handler?.sendEmptyMessageDelayed(MSG_CHECK, INTERVAL_MS)
        }
        return true
    }
}

