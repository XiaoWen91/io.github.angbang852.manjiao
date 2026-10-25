package io.github.angbang852.manjiao.data

import io.github.angbang852.manjiao.util.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 续火花好友缓存。
 *
 * 来源不是网络接口，而是快手会话列表/绑定 UI 的运行时对象：
 * - single.f#g2 的 arg0 里有目标 id（字段 h）
 * - single.f#a 的 uiState 里有昵称/头像（字段 b/a）
 *
 * 这样不用用户手输每个快手 ID。
 */
object SparkContacts {
    data class Contact(val id: String, val name: String, val avatar: String)

    private val map = ConcurrentHashMap<String, Contact>()

    @Volatile private var loaded = false
    @Volatile private var recentTarget = ""

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            val raw = Prefs.str(Prefs.K_SPARK_CONTACTS, "")
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
        val id = readString(extra, "h").ifEmpty { recentTarget }
        if (id.isEmpty()) return
        val name = readString(uiState, "b")
        val avatar = readString(uiState, "a")
        val old = map[id]
        map[id] = Contact(
            id,
            name.ifEmpty { old?.name ?: id },
            avatar.ifEmpty { old?.avatar ?: "" }
        )
        persist()
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
            Prefs.setStr(Prefs.K_SPARK_CONTACTS, arr.toString())
        } catch (t: Throwable) {
            Logger.d("spark contacts persist failed: ${t.message}")
        }
    }

    private fun readString(obj: Any?, field: String): String {
        if (obj == null) return ""
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(field)
                f.isAccessible = true
                val v = f.get(obj)
                return v as? String ?: ""
            } catch (_: Throwable) {
                c = c.superclass
            }
        }
        return ""
    }
}
