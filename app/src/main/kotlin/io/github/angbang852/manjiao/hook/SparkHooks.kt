package io.github.angbang852.manjiao.hook

import android.content.Context
import android.widget.TextView
import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * KsSpark 功能 Hook 层：续火花增强开关对应的宿主 Hook。
 *
 * 当前先落地：去开屏广告、免已读、精确时间、真实计数、时间不换行。
 * 防撤回 / 去水印依赖更复杂的消息与媒体结构，后续单独补。
 */
object SparkHooks {
    fun hook(module: XposedModule, cl: ClassLoader) {
        hookNoSplash(module, cl)
        hookNoRead(module, cl)
        hookExactTime(module, cl)
        hookRealCount(module, cl)
        hookTimeNoWrap(module, cl)
    }

    private fun hookNoSplash(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.kuaishou.commercial.splash.t", false, cl)
            val m = c.getDeclaredMethod("O0")
            module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("spark.no_splash.need")
                .intercept { chain ->
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
            module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("spark.no_splash.show")
                .intercept { chain ->
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
            module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("spark.no_read")
                .intercept { chain ->
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
                module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("spark.exact_time.${m.name}")
                    .intercept { chain ->
                        if (!Prefs.bool(Prefs.K_SPARK_EXACT_TIME, false)) {
                            return@intercept chain.proceed()
                        }
                        val ts = chain.getArg(1) as? Long ?: 0L
                        if (ts <= 0L) chain.proceed()
                        else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ts))
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
            module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("spark.real_count")
                .intercept { chain ->
                    if (!Prefs.bool(Prefs.K_SPARK_REAL_COUNT, false)) {
                        return@intercept chain.proceed()
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
            module.hook(m1)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("spark.time_nowrap1")
                .intercept { chain ->
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
            module.hook(m2)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("spark.time_nowrap2")
                .intercept { chain ->
                    val r = chain.proceed()
                    nowrap(chain.thisObject, chain.getArg(0))
                    r
                }
        } catch (t: Throwable) {
            Logger.d("spark time_nowrap2 miss: ${t.message}")
        }
    }

    private fun nowrap(self: Any?, text: Any?) {
        if (!Prefs.bool(Prefs.K_SPARK_EXACT_TIME, false)) return
        if (self !is TextView || text !is CharSequence) return
        val s = text.toString()
        if (s.length !in 8..40) return
        if (!Regex("\\d{2}:\\d{2}:\\d{2}").containsMatchIn(s)) return
        self.setSingleLine(false)
        self.maxLines = 2
        self.setEllipsize(null)
        self.setHorizontallyScrolling(false)
    }
}
