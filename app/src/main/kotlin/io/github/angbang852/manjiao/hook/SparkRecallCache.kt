package io.github.angbang852.manjiao.hook

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * KsSpark RecallCache 的轻量移植：缓存原消息文本、摘要、contentBytes/extra、本地路径。
 * 仅用于撤回提示行的 best-effort 恢复。
 */
object SparkRecallCache {
    data class Item(
        val sender: String,
        val extra: String,
        val time: Long,
        val text: String,
        val summary: String,
        val payload: ByteArray?,
        val localPath: String?,
        val cls: String,
        val msgType: Int
    )

    private const val MAX_ITEMS = 320
    private val items = ConcurrentHashMap<String, Item>()
    private val order = java.util.ArrayDeque<String>()

    @Synchronized
    fun remember(origin: Any?): Boolean {
        if (origin == null) return false
        try {
            val sender = str(origin, "getSender")
            val extra = str(origin, "getExtra")
            val time = num(origin, "getSentTime").let { if (it > 0L) it else num(origin, "getCreateTime") }
            val text = firstNonEmpty(origin, "getText")
            val summary = firstNonEmpty(origin, "getSummary")
            val payload = bytes(origin, "getContentBytes") ?: bytes(origin, "getExtra")
            val local = localPathOf(origin, extra, payload)
            if (sender.isEmpty() && extra.isEmpty() && text.isEmpty() && summary.isEmpty() && payload == null) return false
            val key = key(sender, extra, time)
            items[key] = Item(sender, extra, time, text, summary, payload, local, origin.javaClass.name, num(origin, "getMsgType").toInt())
            synchronized(order) {
                order.remove(key)
                order.addLast(key)
                while (order.size > MAX_ITEMS) {
                    items.remove(order.removeFirst())
                }
            }
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    @Synchronized
    fun resolveFrom(notice: Any?): Item? {
        if (notice == null) return null
        try {
            val sender = str(notice, "getSender")
            val extra = str(notice, "getExtra")
            val time = num(notice, "getSentTime").let { if (it > 0L) it else num(notice, "getCreateTime") }
            items[key(sender, extra, time)]?.let { return it }
            if (sender.isEmpty() && extra.isEmpty()) return null
            var best: Item? = null
            var bestDiff = Long.MAX_VALUE
            for (it in items.values) {
                if (sender.isNotEmpty() && it.sender != sender) continue
                if (extra.isNotEmpty() && it.extra.isNotEmpty() && it.extra != extra) continue
                val diff = if (time > 0L && it.time > 0L) kotlin.math.abs(it.time - time) else 0L
                if (diff < bestDiff) {
                    bestDiff = diff
                    best = it
                }
            }
            return if (bestDiff <= 10 * 60 * 1000L) best else null
        } catch (_: Throwable) {
            return null
        }
    }

    fun stat(): String = "items=${items.size}"

    private fun key(sender: String, extra: String, time: Long): String = "$sender|$extra|$time"

    private fun firstNonEmpty(obj: Any, name: String): String {
        val s = str(obj, name)
        return s
    }

    private fun str(obj: Any, name: String): String {
        return try {
            val v = call(obj, name)
            v?.toString()?.trim().orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun num(obj: Any, name: String): Long {
        return try {
            when (val v = call(obj, name)) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: 0L
                else -> 0L
            }
        } catch (_: Throwable) {
            0L
        }
    }

    private fun bytes(obj: Any, name: String): ByteArray? {
        return try {
            call(obj, name) as? ByteArray
        } catch (_: Throwable) {
            null
        }
    }

    private fun call(obj: Any, name: String): Any? {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val m = c.getDeclaredMethod(name)
                m.isAccessible = true
                return m.invoke(obj)
            } catch (_: Throwable) {
                c = c.superclass
            }
        }
        return null
    }

    private fun localPathOf(obj: Any, extra: String, payload: ByteArray?): String? {
        for (n in arrayOf("getAttachmentFilePath", "getAttachmentPath", "getLocalFilePath", "getFilePath")) {
            val p = str(obj, n)
            if (p.isNotEmpty() && looksLikeImagePath(p)) return p
        }
        val text = try {
            if (payload != null && payload.isNotEmpty()) String(payload, StandardCharsets.UTF_8) else extra
        } catch (_: Throwable) {
            extra
        }
        val m = Regex("""/(?:data|storage|sdcard|mnt)/[A-Za-z0-9\-._/@]{4,240}""").find(text)?.value
        return m?.let { cleanPath(it) }
    }

    private fun looksLikeImagePath(p: String): Boolean {
        val s = p.lowercase()
        return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".png") || s.endsWith(".webp")
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
}
