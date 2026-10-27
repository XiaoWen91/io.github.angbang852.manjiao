package io.github.angbang852.manjiao.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout

object FrostedKit {
    fun dialog(
        act: Activity,
        wash: Int = 0xB32F3036.toInt(),
        radiusDp: Int = 28
    ): Pair<Dialog, LinearLayout> {
        val d = Dialog(act)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setDimAmount(0.35f)
        d.window?.setLayout(
            (act.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        d.window?.setGravity(Gravity.CENTER)

        val glass = GlassPanel(act, dp(act, radiusDp).toFloat(), washOverride = wash)
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 12))
        }
        glass.addView(
            box,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        d.setContentView(glass)
        return d to box
    }

    fun iosSwitch(ctx: Context, checked: Boolean, onToggle: (Boolean) -> Unit): View {
        val trackW = dp(ctx, 56)
        val trackH = dp(ctx, 32)
        val knobSize = dp(ctx, 26)
        val pad = (trackH - knobSize) / 2
        val colorOn = 0xCC4CAF50.toInt()
        val colorOff = 0xCC757575.toInt()

        val container = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(trackW, trackH)
        }
        val trackBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = trackH / 2f
            setColor(if (checked) colorOn else colorOff)
        }
        val track = View(ctx).apply {
            background = trackBg
            elevation = dp(ctx, 1).toFloat()
        }
        container.addView(
            track,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val knob = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            elevation = dp(ctx, 4).toFloat()
        }
        val knobLp = FrameLayout.LayoutParams(knobSize, knobSize, Gravity.CENTER_VERTICAL)
        knobLp.leftMargin = if (checked) trackW - knobSize - pad else pad
        container.addView(knob, knobLp)

        var isOn = checked
        container.setOnClickListener {
            isOn = !isOn
            trackBg.setColor(if (isOn) colorOn else colorOff)
            track.background = trackBg
            val fromLeft = knobLp.leftMargin
            val targetLeft = if (isOn) trackW - knobSize - pad else pad
            val dx = (targetLeft - fromLeft).toFloat()
            knob.animate().scaleX(0.8f).scaleY(0.8f).setDuration(70).withEndAction {
                knob.animate().translationX(dx).scaleX(1f).scaleY(1f).setDuration(240)
                    .setInterpolator(OvershootInterpolator(2.4f)).withEndAction {
                        knobLp.leftMargin = targetLeft
                        knob.translationX = 0f
                        knob.layoutParams = knobLp
                    }.start()
            }.start()
            try {
                onToggle(isOn)
            } catch (_: Throwable) {
            }
        }
        return container
    }

    fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
