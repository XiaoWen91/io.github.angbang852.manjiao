package io.github.angbang852.manjiao.data

import android.util.Base64
import io.github.angbang852.manjiao.util.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * 续火花好友缓存。
 *
 * 对齐原 KsSpark ContactStore：
 * - single.f#g2 的 arg0：字段 h 是目标 id
 * - single.f#a 的 uiState：优先从头像 URL 反推用户 id，b.a 是昵称
 *
 * 快手 14.8.40 的 single.f 已经没有 g2，所以头像 URL 反推 id 是主路径。
 */
object SparkContacts {
    data class Contact(val id: String, val name: String, val avatar: String)

    private const val FILE_NAME = "spark_contacts.json"
    private val map = ConcurrentHashMap<String, Contact>()

    @Volatile private var loaded = false
    @Volatile private var recentTarget = ""

    private fun mediaFile(): File = File("/sdcard/Download/ManJiao/.sys", FILE_NAME)

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true

            var raw = ""
            try {
                val f = mediaFile()
                if (f.isFile && f.length() > 0) {
                    raw = f.readText(StandardCharsets.UTF_8)
                }
            } catch (t: Throwable) {
                Logger.d("spark contacts media read failed: ${t.message}")
            }

            if (raw.isEmpty()) raw = Prefs.str(Prefs.K_SPARK_CONTACTS, "")
            if (raw.isEmpty()) return

            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isEmpty()) continue
                    map[id] = Contact(id, o.optString("name"), o.optString("avatar"))
                }
            } catch (t: Throwable) {
                Logger.d("spark contacts load failed: ${t.message}")
            }
        }
    }

    fun all(): List<Contact> {
        ensureLoaded()
        return map.values.sortedBy { it.name.ifEmpty { it.id } }
    }

    fun noteBindTarget(obj: Any?) {
        val id = readString(obj, "h")
        if (id.isEmpty()) return
        recentTarget = id
        val old = map[id]
        map[id] = Contact(id, old?.name ?: id, old?.avatar ?: "")
        persist()
    }

    fun noteBindFallbackName(obj: Any?) {
        val id = recentTarget
        if (id.isEmpty()) return
        val name = readString(obj, "d")
        if (name.isEmpty()) return
        val old = map[id]
        map[id] = Contact(id, name, old?.avatar ?: "")
        persist()
    }

    fun noteBindUiState(uiState: Any?, extra: Any?) {
        val avatar = extractAvatarUrl(uiState)

        // 原版优先从头像 URL 反推 id；14.8.40 没有 g2，这一步就是主路径。
        var id = if (avatar.isNotEmpty()) userIdFromAvatarUrl(avatar) else ""
        if (id.isEmpty()) id = recentTarget
        if (id.isEmpty()) return

        val nameHolder = readField(uiState, "b")
        val name = readString(nameHolder, "a")

        val old = map[id]
        val finalName = name.ifEmpty { old?.name ?: id }
        val finalAvatar = avatar.ifEmpty { old?.avatar ?: "" }
        map[id] = Contact(id, finalName, finalAvatar)
        persist()
        Logger.d("spark contact ui id=$id name=$finalName avatar=${finalAvatar.isNotEmpty()}")
    }

    fun selectedIds(): MutableSet<String> {
        return Prefs.str(Prefs.K_SPARK_TARGETS, "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
    }

    fun saveSelected(ids: Set<String>) {
        Prefs.setStr(Prefs.K_SPARK_TARGETS, ids.joinToString(","))
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            for (c in map.values) {
                arr.put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("avatar", c.avatar)
                })
            }
            val json = arr.toString()
            Prefs.setStr(Prefs.K_SPARK_CONTACTS, json)

            try {
                val f = mediaFile()
                f.parentFile?.mkdirs()
                f.writeText(json, StandardCharsets.UTF_8)
            } catch (t: Throwable) {
                Logger.d("spark contacts media write failed: ${t.message}")
            }
        } catch (t: Throwable) {
            Logger.d("spark contacts persist failed: ${t.message}")
        }
    }

    private fun extractAvatarUrl(obj: Any?): String {
        if (obj == null) return ""
        return try {
            val a = readField(obj, "a")
            if (a != null) {
                val direct = readField(a, "a")
                if (direct is String && looksLikeImageUrl(direct)) return direct

                val list = readField(a, "b")
                if (list is List<*> && list.isNotEmpty()) {
                    val item = list[0]
                    if (item != null) {
                        for (name in arrayOf("getUrl", "getCdnUrl", "getCDNUrl", "a")) {
                            try {
                                val m = item.javaClass.getMethod(name)
                                val v = m.invoke(item)
                                if (v is String && looksLikeImageUrl(v)) return v
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
            scanForImageUrl(obj, 0) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun scanForImageUrl(obj: Any?, depth: Int): String? {
        if (obj == null || depth > 3) return null
        if (obj is String) return if (looksLikeImageUrl(obj)) obj else null
        if (obj is Collection<*>) {
            for (e in obj) {
                val r = scanForImageUrl(e, depth + 1)
                if (r != null) return r
            }
            return null
        }
        val cn = obj.javaClass.name
        if (cn.startsWith("java.") || cn.startsWith("android.") ||
            cn.startsWith("androidx.") || cn.startsWith("kotlin.")
        ) return null

        var c: Class<*>? = obj.javaClass
        var visited = 0
        while (c != null && c != Any::class.java && visited < 80) {
            for (f in c.declaredFields) {
                if (++visited > 80) return null
                if (Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    val v = f.get(obj)
                    if (v is String && looksLikeImageUrl(v)) return v
                    if (v != null && v !== obj) {
                        val r = scanForImageUrl(v, depth + 1)
                        if (r != null) return r
                    }
                } catch (_: Throwable) {
                }
            }
            c = c.superclass
        }
        return null
    }

    private fun looksLikeImageUrl(s: String): Boolean {
        if (s.length < 8) return false
        val l = s.lowercase()
        return l.startsWith("http://") || l.startsWith("https://") ||
            l.contains("uhead") || l.contains("avatar") || l.contains("/head")
    }

    private fun userIdFromAvatarUrl(url: String): String {
        if (url.isEmpty()) return ""
        try {
            var s = url
            val q = s.indexOf('?')
            if (q > 0) s = s.substring(0, q)
            var slash = s.lastIndexOf('/')
            if (slash < 0) return ""
            s = s.substring(slash + 1)
            val dot = s.lastIndexOf('.')
            if (dot > 0) s = s.substring(0, dot)
            if (s.length > 2 && s[s.length - 2] == '_') {
                s = s.substring(0, s.length - 2)
            }
            for (i in 0..1) {
                if (i >= s.length) break
                val sub = s.substring(i)
                if (sub.length % 4 != 0) continue
                try {
                    val bytes = Base64.decode(sub, Base64.DEFAULT)
                    val text = String(bytes, StandardCharsets.UTF_8)
                    val parts = text.split("_")
                    if (parts.size >= 2 && parts[0].length == 14 && parts[1].matches(Regex("\\d{4,}"))) {
                        return parts[1]
                    }
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
        return ""
    }

    private fun readField(obj: Any?, field: String): Any? {
        if (obj == null) return null
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(field)
                f.isAccessible = true
                return f.get(obj)
            } catch (_: Throwable) {
                c = c.superclass
            }
        }
        return null
    }

    private fun readString(obj: Any?, field: String): String {
        val v = readField(obj, field)
        return v as? String ?: ""
    }
}
