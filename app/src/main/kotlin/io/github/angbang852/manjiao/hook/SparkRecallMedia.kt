package io.github.angbang852.manjiao.hook

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.TextView
import io.github.angbang852.manjiao.util.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * 精简版 RecallMedia：只做撤回提示行上的贴纸/图片恢复。
 *
 * 不恢复原气泡布局，只从原消息 contentBytes/extra/localExtra 里提取
 * 本地路径或图片 URL，解码后挂到 TextView 的 compound drawable。
 */
object SparkRecallMedia {
    private val cache = ConcurrentHashMap<String, Drawable>()

    private val URL_RE = Regex("""https?://[^\s"']{10,500}""")
    private val PATH_RE = Regex("""/(?:data|storage|sdcard|mnt)/[A-Za-z0-9\-._/@]{4,240}""")

    fun attach(tv: TextView, origin: Any?) {
        if (origin == null) return
        try {
            val payload = payloadOf(origin)
            if (payload == null || payload.isEmpty()) return

            val text = try {
                String(payload, StandardCharsets.UTF_8)
            } catch (_: Throwable) {
                ""
            }

            val local = PATH_RE.find(text)?.value?.let { cleanPath(it) }
            if (local != null && loadFile(tv, local)) return

            val url = URL_RE.find(text)?.value
            if (url != null) loadUrl(tv, url)
        } catch (t: Throwable) {
            Logger.d("spark recall media failed: ${t.message}")
        }
    }

    private fun payloadOf(origin: Any): ByteArray? {
        for (name in arrayOf("getContentBytes", "getExtra", "getLocalExtra")) {
            try {
                val m = origin.javaClass.getMethod(name)
                val v = m.invoke(origin)
                if (v is ByteArray && v.isNotEmpty()) return v
            } catch (_: Throwable) {
            }
        }
        try {
            val m = origin.javaClass.getMethod("getLocalExtraMap")
            val map = m.invoke(origin) as? Map<*, *>
            if (map != null && map.isNotEmpty()) {
                val sb = StringBuilder()
                for ((k, v) in map) {
                    sb.append(k).append('=').append(v).append('\n')
                }
                return sb.toString().toByteArray(StandardCharsets.UTF_8)
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun cleanPath(path: String): String? {
        val idx = when {
            path.contains("/data/") -> path.indexOf("/data/")
            path.contains("/storage/") -> path.indexOf("/storage/")
            path.contains("/sdcard/") -> path.indexOf("/sdcard/")
            path.contains("/mnt/") -> path.indexOf("/mnt/")
            else -> -1
        }
        if (idx < 0) return null
        var s = path.substring(idx)
        val q = s.indexOf('?')
        if (q > 0) s = s.substring(0, q)
        return s
    }

    private fun loadFile(tv: TextView, path: String): Boolean {
        return try {
            val f = File(path)
            if (!f.isFile || f.length() <= 0L) return false
            val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return false
            applyDrawable(tv, BitmapDrawable(tv.resources, bmp))
            Logger.d("spark recall media local path=$path")
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun loadUrl(tv: TextView, url: String) {
        cache[url]?.let {
            applyDrawable(tv, it)
            return
        }
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    val d = BitmapDrawable(tv.resources, bmp)
                    cache[url] = d
                    tv.post { applyDrawable(tv, d) }
                    Logger.d("spark recall media url=${url.take(80)}")
                }
            } catch (_: Throwable) {
            }
        }.start()
    }

    private fun applyDrawable(tv: TextView, drawable: Drawable) {
        val density = tv.resources.displayMetrics.density
        val max = (160 * density).toInt()
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w <= 0 || h <= 0) return
        val scale = min(1f, min(max.toFloat() / w, max.toFloat() / h))
        val dw = (w * scale).toInt().coerceAtLeast(1)
        val dh = (h * scale).toInt().coerceAtLeast(1)
        drawable.setBounds(0, 0, dw, dh)
        tv.setCompoundDrawables(null, drawable, null, null)
        tv.compoundDrawablePadding = (4 * density).toInt()
    }
}
