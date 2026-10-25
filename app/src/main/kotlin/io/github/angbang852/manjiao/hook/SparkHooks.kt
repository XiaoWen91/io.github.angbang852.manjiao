package io.github.angbang852.manjiao.hook

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.data.SparkContacts
import io.github.angbang852.manjiao.ui.SparkSettingsEntry
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * KsSpark 功能 Hook 层。
 *
 * 只保留续火花相关能力：去开屏广告、免已读、精确时间（紧凑格式）、
 * 真实计数、时间不换行、好友列表采集、设置页入口。
 */
object SparkHooks {
    private val installed: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun hook(module: XposedModule, cl: ClassLoader) {
        SparkSender.attach(cl)
        hookNoSplash(module, cl)
        hookNoRead(module, cl)
        hookExactTime(module, cl)
        hookRealCount(module, cl)
        hookTimeNoWrap(module, cl)
        hookContacts(module, cl)
        hookSettingsEntry(module, cl)
        hookAntiRecall(module, cl)
        hookNoWatermark(module, cl)
    }

    fun selfTest(): String {
        val expected = listOf(
            "spark.no_splash.need",
            "spark.no_splash.show",
            "spark.no_read",
            "spark.exact_time.",
            "spark.real_count",
            "spark.time_nowrap1",
            "spark.time_nowrap2",
            "spark.contacts.",
            "spark.settings.entry",
            "spark.anti_recall.",
            "spark.no_wm."
        )
        val lines = expected.map { prefix ->
            val ok = installed.any { it == prefix || it.startsWith(prefix) }
            (if (ok) "✓" else "✗") + " " + prefix
        }
        val okCount = lines.count { it.startsWith("✓") }
        return "Hook 测试 $okCount/${lines.size}\n" + lines.joinToString("\n") +
            "\n好友缓存: ${SparkContacts.all().size}"
    }

    private fun install(
        module: XposedModule,
        m: Method,
        id: String,
        hooker: XposedInterface.Hooker
    ) {
        module.hook(m)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .setId(id)
            .intercept(hooker)
        installed.add(id)
    }

    private fun hookNoSplash(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.kuaishou.commercial.splash.t", false, cl)
            val m = c.getDeclaredMethod("O0")
            install(module, m, "spark.no_splash.need") { chain ->
                val r = chain.proceed()
                if (Prefs.bool(Prefs.K_SPARK_NO_SPLASH, true)) false else r
            }
        } catch (t: Throwable) {
            Logger.d("spark no_splash_need miss: ${t.message}")
        }

        try {
            val c = Class.forName(
                "com.yxcorp.gifshow.homepage.presenter.splash.ThanosSplashPresenter",
                false,
                cl
            )
            val m = c.getDeclaredMethod("jj")
            install(module, m, "spark.no_splash.show") { chain ->
                val r = chain.proceed()
                if (Prefs.bool(Prefs.K_SPARK_NO_SPLASH, true)) false else r
            }
        } catch (t: Throwable) {
            Logger.d("spark no_splash_show miss: ${t.message}")
        }
    }

    private fun hookNoRead(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName(
                "com.kwai.kmp.imsdk.internal.logic.conversation.IMConversationManager",
                false,
                cl
            )
            var target: Method? = null
            var fallback: Method? = null
            for (m in c.declaredMethods) {
                val p = m.parameterTypes
                if (m.returnType != Void.TYPE || p.size != 2) continue
                if (p[0] != Long::class.javaPrimitiveType || p[1] != Long::class.javaPrimitiveType) continue
                if (m.name == "U") {
                    target = m
                    break
                }
                if (fallback == null) fallback = m
            }
            val m = target ?: fallback ?: return
            install(module, m, "spark.no_read") { chain ->
                if (Prefs.bool(Prefs.K_SPARK_NO_READ, false)) null else chain.proceed()
            }
        } catch (t: Throwable) {
            Logger.d("spark no_read miss: ${t.message}")
        }
    }

    private fun hookExactTime(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.yxcorp.gifshow.util.DateUtils", false, cl)
            for (m in c.declaredMethods) {
                val p = m.parameterTypes
                if (m.returnType != String::class.java || p.size != 2) continue
                if (p[0] != Context::class.java || p[1] != Long::class.javaPrimitiveType) continue
                install(module, m, "spark.exact_time.${m.name}") { chain ->
                    if (!Prefs.bool(Prefs.K_SPARK_EXACT_TIME, false)) {
                        return@install chain.proceed()
                    }
                    val ts = chain.getArg(1) as? Long ?: 0L
                    if (ts <= 0L) chain.proceed() else formatTime(ts)
                }
            }
        } catch (t: Throwable) {
            Logger.d("spark exact_time miss: ${t.message}")
        }
    }

    private fun hookRealCount(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.yxcorp.utility.TextUtils", false, cl)
            val m = c.getDeclaredMethod(
                "v",
                Locale::class.java,
                Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            install(module, m, "spark.real_count") { chain ->
                if (!Prefs.bool(Prefs.K_SPARK_REAL_COUNT, false)) {
                    return@install chain.proceed()
                }
                val n = chain.getArg(1) as? Long ?: 0L
                if (n < 0L) chain.proceed()
                else String.format(Locale.US, "%,d", n)
            }
        } catch (t: Throwable) {
            Logger.d("spark real_count miss: ${t.message}")
        }
    }

    private fun hookTimeNoWrap(module: XposedModule, cl: ClassLoader) {
        try {
            val m1 = TextView::class.java.getDeclaredMethod("setText", CharSequence::class.java)
            install(module, m1, "spark.time_nowrap1") { chain ->
                val r = chain.proceed()
                nowrap(chain.thisObject, chain.getArg(0))
                r
            }
        } catch (t: Throwable) {
            Logger.d("spark time_nowrap1 miss: ${t.message}")
        }

        try {
            val m2 = TextView::class.java.getDeclaredMethod(
                "setText",
                CharSequence::class.java,
                TextView.BufferType::class.java
            )
            install(module, m2, "spark.time_nowrap2") { chain ->
                val r = chain.proceed()
                nowrap(chain.thisObject, chain.getArg(0))
                r
            }
        } catch (t: Throwable) {
            Logger.d("spark time_nowrap2 miss: ${t.message}")
        }
    }

    private fun hookAntiRecall(module: XposedModule, cl: ClassLoader) {
        try {
            val k = Class.forName("com.yxcorp.gifshow.message.sdk.message.KRecalledMsg", false, cl)
            val summary = k.getDeclaredMethod("getSummary")
            install(module, summary, "spark.anti_recall.summary") { chain ->
                if (Prefs.bool(Prefs.K_SPARK_ANTI_RECALL, true)) {
                    "这条消息已撤回"
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            Logger.d("spark anti_recall summary miss: ${t.message}")
        }

        try {
            val r = Class.forName("com.kwai.imsdk.msg.RecalledMsg", false, cl)
            val origin = r.getDeclaredMethod("getOriginMsg")
            install(module, origin, "spark.anti_recall.origin") { chain ->
                chain.proceed()
            }
        } catch (t: Throwable) {
            Logger.d("spark anti_recall origin miss: ${t.message}")
        }
    }

    private fun hookNoWatermark(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.yxcorp.gifshow.photo.download.api.response.DownloadPhotoInfoResponse", false, cl)
            val m = c.getDeclaredMethod("afterDeserialize")
            install(module, m, "spark.no_wm.flag") { chain ->
                val r = chain.proceed()
                if (Prefs.bool(Prefs.K_SPARK_NO_WATERMARK, true)) {
                    setBoolField(c, chain.thisObject, "mNotNeedWaterMark", true)
                    setBoolField(c, chain.thisObject, "mHasWatermark", false)
                }
                r
            }
        } catch (t: Throwable) {
            Logger.d("spark no_wm flag miss: ${t.message}")
        }

        try {
            val c = Class.forName("com.yxcorp.gifshow.photo.download.api.response.DownloadPhotoInfoResponse", false, cl)
            for (m in c.declaredMethods) {
                if (m.name != "getDownloadUrl" || m.parameterCount != 1) continue
                install(module, m, "spark.no_wm.url") { chain ->
                    val r = chain.proceed()
                    if (!Prefs.bool(Prefs.K_SPARK_NO_WATERMARK, true)) return@install r
                    val urls = getField(chain.thisObject, "mVideoUrls")
                    if (urls is Array<*> && urls.isNotEmpty()) urls else r
                }
            }
        } catch (t: Throwable) {
            Logger.d("spark no_wm url miss: ${t.message}")
        }

        hookNoWmBoolArg(module, cl, "hqk.x1", arrayOf("i", "j"), 1, "spark.no_wm.img")
        hookNoWmBoolArg(module, cl, "hqk.c3", arrayOf("h"), 2, "spark.no_wm.album")
        hookNoWmBoolArg(module, cl, "f3m.b", arrayOf("a"), 0, "spark.no_wm.export")
    }

    private fun hookNoWmBoolArg(
        module: XposedModule,
        cl: ClassLoader,
        className: String,
        names: Array<String>,
        argIndex: Int,
        idPrefix: String
    ) {
        try {
            val c = Class.forName(className, false, cl)
            for (m in c.declaredMethods) {
                if (m.name !in names) continue
                val p = m.parameterTypes
                if (argIndex >= p.size || p[argIndex] != Boolean::class.javaPrimitiveType) continue
                install(module, m, "$idPrefix.${m.name}") { chain ->
                    val r = chain.proceed()
                    if (!Prefs.bool(Prefs.K_SPARK_NO_WATERMARK, true)) return@install r
                    val args = chain.args.toMutableList()
                    if (args.size > argIndex && args[argIndex] == true) {
                        args[argIndex] = false
                        return@install chain.proceed(args.toTypedArray())
                    }
                    r
                }
            }
        } catch (t: Throwable) {
            Logger.d("spark no_wm $idPrefix miss: ${t.message}")
        }
    }

    private fun setBoolField(cls: Class<*>, obj: Any?, name: String, value: Boolean) {
        try {
            val f = cls.getDeclaredField(name)
            f.isAccessible = true
            f.setBoolean(obj, value)
        } catch (_: Throwable) {
        }
    }

    private fun getField(obj: Any?, name: String): Any? {
        if (obj == null) return null
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(obj)
            } catch (_: Throwable) {
                c = c.superclass
            }
        }
        return null
    }

    private fun hookContacts(module: XposedModule, cl: ClassLoader) {
        val candidates = arrayOf(
            "com.yxcorp.gifshow.message.next.conversation.children.list_content.children.single.f",
            "com.yxcorp.gifshow.message.next.conversation.children.list_content.children.single.SingleConversationComponent"
        )
        for (cn in candidates) {
            try {
                val c = Class.forName(cn, false, cl)
                var hooked = false
                val methods = LinkedHashMap<String, Method>()
                var cur: Class<*>? = c
                while (cur != null && cur != Any::class.java) {
                    for (m in cur.declaredMethods) {
                        val key = m.name + "#" + m.parameterTypes.joinToString(",") { it.name }
                        methods.putIfAbsent(key, m)
                    }
                    cur = cur.superclass
                }
                for (m in methods.values) {
                    if (m.name == "g2") {
                        val hasArg = m.parameterTypes.isNotEmpty()
                        install(module, m, "spark.contacts.bind") { chain ->
                            val arg = if (hasArg) chain.getArg(0) else chain.thisObject
                            SparkContacts.noteBindTarget(arg)
                            SparkContacts.noteBindTarget(chain.thisObject)
                            val r = chain.proceed()
                            SparkContacts.noteBindFallbackName(chain.thisObject)
                            r
                        }
                        hooked = true
                    }
                    if (m.name == "a" && m.parameterTypes.isNotEmpty()) {
                        install(module, m, "spark.contacts.uistate") { chain ->
                            SparkContacts.noteBindUiState(chain.getArg(0), chain.thisObject)
                            SparkContacts.noteBindUiState(chain.thisObject, chain.thisObject)
                            chain.proceed()
                        }
                        hooked = true
                    }
                }
                if (hooked) Logger.d("spark contacts hooked: $cn")
            } catch (t: Throwable) {
                Logger.d("spark contacts miss $cn: ${t.message}")
            }
        }
    }

    private fun hookSettingsEntry(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.yxcorp.plugin.setting.activity.SettingsActivity", false, cl)
            val m = c.getDeclaredMethod("onCreate", Bundle::class.java)
            install(module, m, "spark.settings.entry") { chain ->
                val r = chain.proceed()
                val self = chain.thisObject
                if (self is Activity) SparkSettingsEntry.inject(self)
                r
            }
        } catch (t: Throwable) {
            Logger.d("spark settings entry miss: ${t.message}")
        }
    }

    private fun formatTime(ts: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ts }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val pattern = when {
            sameDay -> "HH:mm"
            sameYear -> "MM-dd HH:mm"
            else -> "yyyy-MM-dd"
        }
        return SimpleDateFormat(pattern, Locale.US).format(Date(ts))
    }

    private fun nowrap(self: Any?, text: Any?) {
        if (!Prefs.bool(Prefs.K_SPARK_EXACT_TIME, false)) return
        if (self !is TextView || text !is CharSequence) return
        val s = text.toString()
        if (s.length !in 8..40) return
        if (!Regex("\\d{2}:\\d{2}").containsMatchIn(s)) return
        self.setSingleLine(false)
        self.maxLines = 2
        self.setEllipsize(null)
        self.setHorizontallyScrolling(false)
    }
}
