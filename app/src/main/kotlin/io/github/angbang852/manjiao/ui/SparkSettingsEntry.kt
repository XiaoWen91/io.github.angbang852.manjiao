package io.github.angbang852.manjiao.ui

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import io.github.angbang852.manjiao.util.Logger

/**
 * 在快手设置页顶部注入 KsSpark 入口。
 * 保留原 MainMenuDialog 入口，不删除。
 */
object SparkSettingsEntry {
    private const val TAG = "spark_settings_entry"

    fun inject(activity: Activity) {
        try {
            if (activity.isFinishing || activity.isDestroyed) return
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            if (root.findViewWithTag<TextView>(TAG) != null) return

            val density = activity.resources.displayMetrics.density
            val padH = (20 * density).toInt()
            val padV = (16 * density).toInt()

            val row = TextView(activity).apply {
                tag = TAG
                text = "KiraTools"
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                setBackgroundColor(0xEE1A1A1A.toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener { MainMenuDialog.show(activity) }
            }

            val lp = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val statusBarId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarH = if (statusBarId > 0) activity.resources.getDimensionPixelSize(statusBarId) else 0
            val actionBarH = activity.actionBar?.height ?: (56 * density).toInt()
            lp.gravity = Gravity.TOP
            lp.topMargin = statusBarH + actionBarH
            row.elevation = 20f * density
            root.addView(row, lp)
            Logger.d("spark settings entry injected")
        } catch (t: Throwable) {
            Logger.d("spark settings entry failed: ${t.message}")
        }
    }
}
