package io.github.angbang852.manjiao.hook

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.github.angbang852.manjiao.data.Prefs
import java.lang.ref.WeakReference

object ActivityBridge {
    const val ACTION_SHOW_SCAN = "com.KiraTools.Wen.action.SHOW_SCAN"
    const val ACTION_SHOW_MAIN = "com.KiraTools.Wen.action.SHOW_MAIN"
    const val ACTION_APPLY_ALPHA = "com.KiraTools.Wen.action.APPLY_ALPHA"
    const val ACTION_APPLY_HIDE = "com.KiraTools.Wen.action.APPLY_HIDE"
    const val ACTION_SET_STR = "com.KiraTools.Wen.action.SET_STR"
    const val ACTION_SET_BOOL = "com.KiraTools.Wen.action.SET_BOOL"

    private var current: WeakReference<Activity>? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val act = current?.get() ?: return
            val i = intent ?: return
            when (i.action) {
                ACTION_SHOW_SCAN -> act.runOnUiThread { ScanHideHooks.showPanel(act) }
                ACTION_SHOW_MAIN -> act.runOnUiThread { io.github.angbang852.manjiao.ui.MainMenuDialog.show(act) }
                ACTION_APPLY_ALPHA -> act.runOnUiThread { RegionAlphaHooks.apply(act.window?.decorView) }
                ACTION_APPLY_HIDE -> act.runOnUiThread {
                    ScanHideHooks.apply(act.window?.decorView)
                    BottomBarAligner.align(act.window?.decorView)
                }
                ACTION_SET_STR -> act.runOnUiThread {
                    val k = i.getStringExtra("key")
                    val v = i.getStringExtra("value")
                    if (k != null && v != null) {
                        Prefs.setStrSync(act, k, v)
                        RegionAlphaHooks.apply(act.window?.decorView)
                    }
                }
                ACTION_SET_BOOL -> act.runOnUiThread {
                    val k = i.getStringExtra("key")
                    if (k != null) {
                        Prefs.setBoolSync(act, k, i.getBooleanExtra("value", false))
                        RegionAlphaHooks.apply(act.window?.decorView)
                    }
                }
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
                addAction(ACTION_APPLY_ALPHA)
                addAction(ACTION_APPLY_HIDE)
                addAction(ACTION_SET_STR)
                addAction(ACTION_SET_BOOL)
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
