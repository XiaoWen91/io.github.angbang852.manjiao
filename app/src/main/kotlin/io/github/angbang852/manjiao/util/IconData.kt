package io.github.angbang852.manjiao.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import io.github.angbang852.manjiao.R

object IconData {
    @Volatile private var cache: Drawable? = null

    @Synchronized
    fun get(ctx: Context): Drawable {
        cache?.let { return it }
        val srcDrawable = ContextCompat.getDrawable(ctx, R.drawable.kira_share_icon)
            ?: ColorDrawable(Color.TRANSPARENT)
        val src = drawableToBitmap(srcDrawable)
        val size = 192
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val m = Matrix()
        m.setScale(size / src.width.toFloat(), size / src.height.toFloat())
        shader.setLocalMatrix(m)
        p.shader = shader
        val r = size / 2f
        c.drawCircle(r, r, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.03f
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(r, r, r - p.strokeWidth / 2f, p)
        p.style = Paint.Style.FILL
        cache = BitmapDrawable(ctx.resources, out)
        return cache!!
    }

    private fun drawableToBitmap(d: Drawable): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val w = d.intrinsicWidth.coerceAtLeast(1)
        val h = d.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, w, h)
        d.draw(c)
        return bmp
    }
}
