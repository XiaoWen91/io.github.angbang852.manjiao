package io.github.angbang852.manjiao.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * 自绘滑动条：圆角轨道 + 蓝色进度 + 白色圆点 thumb。
 * 不用系统 SeekBar，避免默认橙色/方形质感。
 */
class KiraSlider(ctx: Context) : View(ctx) {
    var value: Int = 100
        set(v) {
            field = v.coerceIn(0, 100)
            invalidate()
        }

    var onChanged: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A84FF.toInt()
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val thumbStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A84FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val rect = RectF()

    private fun dp(v: Float): Float = v * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        val h = dp(44f).toInt()
        setMeasuredDimension(w, View.resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(10f)
        val right = width - dp(10f)
        val centerY = height / 2f
        val trackH = dp(6f)

        rect.set(left, centerY - trackH / 2f, right, centerY + trackH / 2f)
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, trackPaint)

        val p = value / 100f
        val progressRight = left + (right - left) * p
        rect.set(left, centerY - trackH / 2f, progressRight, centerY + trackH / 2f)
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, progressPaint)

        val cx = progressRight
        val r = dp(10f)
        canvas.drawCircle(cx, centerY, r, thumbPaint)
        canvas.drawCircle(cx, centerY, r, thumbStroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                update(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                update(event.x)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun update(x: Float) {
        val left = dp(10f)
        val right = width - dp(10f)
        if (right <= left) return
        val p = ((x - left) / (right - left)).coerceIn(0f, 1f)
        val nv = (p * 100f).roundToInt()
        if (nv != value) {
            value = nv
            onChanged?.invoke(value)
        }
    }
}
