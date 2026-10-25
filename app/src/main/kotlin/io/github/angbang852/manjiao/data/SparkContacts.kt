package io.github.angbang852.manjiao.data

import io.github.angbang852.manjiao.util.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * 续火花好友缓存。
 *
 * 对齐原 KsSpark ContactStore：
 * - single.f#g2 的 arg0：字段 h 是目标 id
 * - single.f#a 的 uiState：字段 b 是名称持有对象，b.a 是昵称
 *
 * 同时写公共媒体目录，保证快手进程和模块 App 进程都能读到同一份好友缓存。
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
        val id = recentTarget
        if (id.isEmpty()) return

        // 原实现：uiState.b -> 名称持有对象；该对象.a -> 昵称字符串。
        val nameHolder = readField(uiState, "b")
        val name = readString(nameHolder, "a")

        val old = map[id]
        val finalName = name.ifEmpty { old?.name ?: id }
        map[id] = Contact(id, finalName, old?.avatar ?: "")
        persist()
        Logger.d("spark contact ui id=$id name=$finalName")
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
