package io.github.angbang852.manjiao.hook

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.lang.ref.WeakReference

object ActivityBridge {
    const val ACTION_SHOW_SCAN = "com.KiraTools.Wen.action.SHOW_SCAN"
    const val ACTION_SHOW_MAIN = "com.KiraTools.Wen.action.SHOW_MAIN"

    private var current: WeakReference<Activity>? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val act = current?.get() ?: return
            when (intent?.action) {
                ACTION_SHOW_SCAN -> act.runOnUiThread { ScanHideHooks.showPanel(act) }
                ACTION_SHOW_MAIN -> act.runOnUiThread { MainInjectedPanel.showPanel(act) }
            }
        }
    }

    fun install(act: Activity) {
        current = WeakReference(act)
        if (registered) return
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_SHOW_SCAN)
                addAction(ACTION_SHOW_MAIN)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                act.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                act.applicationContext.registerReceiver(receiver, filter)
            }
            registered = true
        } catch (_: Throwable) {
        }
    }
}
