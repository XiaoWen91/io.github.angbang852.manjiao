package io.github.angbang852.manjiao.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView

object ConfirmDialog {
    fun show(
        ctx: Context,
        title: String,
        message: String,
        confirmText: String = "确认",
        onConfirm: () -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        val activity = unwrap(ctx)
        if (activity == null) {
            onDismiss()
            return
        }
        activity.runOnUiThread {
            try {
                val dialog = Dialog(activity)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCanceledOnTouchOutside(true)
                val glass = GlassPanel(activity, dp(activity, 28).toFloat(), washOverride = 0xB32F3036.toInt())
                val col = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 16))
                }
                col.addView(TextView(activity).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    textSize = 17f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                col.addView(TextView(activity).apply {
                    text = message
                    setTextColor(0xB3FFFFFF.toInt())
                    textSize = 14f
                    setPadding(0, dp(activity, 10), 0, 0)
                    lineSpacing(dp(activity, 3).toFloat(), 1f)
                })
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    setPadding(0, dp(activity, 18), 0, 0)
                }
                row.addView(btn(activity, "取消", 0x22FFFFFF, 0xE6FFFFFF) { dialog.dismiss() })
                row.addView(btn(activity, confirmText, 0xFFFF6A00, Color.WHITE) {
                    dialog.dismiss()
                    try { onConfirm() } catch (_: Throwable) {}
                })
                col.addView(row)
                glass.addView(col, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                dialog.setContentView(glass)
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.84f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                dialog.window?.setDimAmount(0.35f)
                dialog.setOnDismissListener { onDismiss() }
                dialog.show()
            } catch (_: Throwable) {
                onDismiss()
            }
        }
    }

    private fun btn(ctx: Context, text: String, bg: Int, color: Int, onClick: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 18), dp(ctx, 10))
        background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 14).toFloat()
            setColor(bg)
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(ctx, 10) }
    }

    private fun unwrap(ctx: Context): Activity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return c as? Activity
    }

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
}
