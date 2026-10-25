package io.github.angbang852.manjiao.hook

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 美化 Hook：底栏、右侧操作栏、挂件、全屏入口、DPI。
 * 全部 best-effort，命中就改，没命中不崩。
 */
object BeautifyHooks {
    private val tabRoles = java.util.WeakHashMap<View, String>()
    private val uiRoots = java.util.WeakHashMap<View, String>()
    private var loader: ClassLoader? = null

    fun hook(module: XposedModule, cl: ClassLoader) {
        loader = cl
        hookDpi(module)
        hookBottomBar(module, cl)
        hookBottomReorder(module, cl)
        hookRightActionBar(module, cl)
        hookPendant(module, cl)
        hookFullscreen(module, cl)
        hookPlaybackAlpha(module, cl)
    }

    private fun install(module: XposedModule, m: Method, id: String, hooker: XposedInterface.Hooker) {
        module.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).setId(id).intercept(hooker)
    }

    private fun installCtor(module: XposedModule, c: Constructor<*>, id: String, hooker: XposedInterface.Hooker) {
        module.hook(c).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).setId(id).intercept(hooker)
    }

    private fun hookDpi(module: XposedModule) {
        try {
            val m = android.content.res.Resources::class.java.getDeclaredMethod("getDisplayMetrics")
            install(module, m, "beauty.dpi") { chain ->
                val r = chain.proceed()
                val target = Prefs.str(Prefs.K_BEAUTY_DPI, "").toIntOrNull()
                val dm = r as? android.util.DisplayMetrics
                if (target != null && target > 0 && dm != null) {
                    dm.densityDpi = target
                    dm.density = target / 160f
                    dm.scaledDensity = dm.density
                }
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty dpi miss: ${t.message}")
        }
    }

    private fun hookBottomBar(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.yxcorp.gifshow.homepage.kcube.actionbar.bottom.widget.BottomTabView", false, cl)
            val setText = c.getDeclaredMethod("setTextView", android.widget.CheckedTextView::class.java)
            install(module, setText, "beauty.bottom.text") { chain ->
                val tv = chain.getArg(0) as? TextView
                val old = tv?.text?.toString().orEmpty()
                if (tv != null) {
                    if (hidden(Prefs.str(Prefs.K_BEAUTY_BOTTOM_HIDE, ""), old)) tv.visibility = View.GONE
                    val repl = replace(Prefs.str(Prefs.K_BEAUTY_BOTTOM_TEXT, ""), old)
                    if (repl != old) tv.text = repl
                }
                chain.proceed()
            }
            val onLayout = c.getDeclaredMethod(
                "onLayout", Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
            )
            install(module, onLayout, "beauty.bottom.layout") { chain ->
                val r = chain.proceed()
                val self = chain.thisObject as? View
                if (self != null) {
                    uiRoots[self] = "bottom"
                    if (Prefs.bool(Prefs.K_BEAUTY_BOTTOM_TRANSPARENT, false)) {
                        self.setBackgroundColor(Color.TRANSPARENT)
                        (self.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
                    }
                }
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty bottom miss: ${t.message}")
        }
    }

    private fun hookBottomReorder(module: XposedModule, cl: ClassLoader) {
        try {
            val specs = arrayOf("hoi.g" to "normal", "hoi.i" to "center")
            for ((clsName, role) in specs) {
                val c = Class.forName(clsName, false, cl)
                val m = c.getDeclaredMethod("c", ViewGroup::class.java)
                install(module, m, "beauty.bottom.reorder.$role") { chain ->
                    val container = chain.getArg(0) as? ViewGroup
                    val r = chain.proceed()
                    val v = r as? View
                    if (container != null) {
                        if (Prefs.bool(Prefs.K_BEAUTY_BOTTOM_TRANSPARENT, false)) {
                            container.setBackgroundColor(Color.TRANSPARENT)
                        }
                    }
                    if (container != null && v != null) {
                        tabRoles[v] = role
                        container.postDelayed({ reorderBottom(container) }, 350L)
                    }
                    r
                }
            }
        } catch (t: Throwable) {
            Logger.d("beauty bottom reorder miss: ${t.message}")
        }
    }

    private fun reorderBottom(container: ViewGroup) {
        if (!Prefs.bool(Prefs.K_BEAUTY_BOTTOM_STYLE, false)) return
        try {
            val center = ArrayList<View>()
            for (i in 0 until container.childCount) {
                val ch = container.getChildAt(i)
                if (tabRoles[ch] == "center") center.add(ch)
            }
            if (center.isEmpty()) return
            for (v in center) {
                container.removeView(v)
                container.addView(v)
            }
        } catch (t: Throwable) {
            Logger.d("beauty bottom reorder apply miss: ${t.message}")
        }
    }

    private fun hookPlaybackAlpha(module: XposedModule, cl: ClassLoader) {
        try {
            val j2 = Class.forName("t6h.j2", false, cl)
            val bind = j2.getDeclaredMethod("doBindView", View::class.java)
            install(module, bind, "beauty.play.alpha.bind") { chain ->
                val r = chain.proceed()
                val self = chain.thisObject
                if (self != null) {
                    try {
                        val f = j2.getDeclaredField("c")
                        f.isAccessible = true
                        (f.get(self) as? View)?.let { uiRoots[it] = "top" }
                    } catch (_: Throwable) {
                    }
                }
                r
            }

            val i2 = Class.forName("t6h.i2", false, cl)
            val accept = i2.getDeclaredMethod("accept", Any::class.java)
            install(module, accept, "beauty.play.alpha.apply") { chain ->
                val r = chain.proceed()
                applyPlayAlpha()
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty play alpha miss: ${t.message}")
        }
    }

    private fun applyPlayAlpha() {
        val key = if (isPlaying()) Prefs.K_BEAUTY_ALPHA_PLAYING else Prefs.K_BEAUTY_ALPHA_PAUSED
        var raw = Prefs.str(key, "")
        if (raw.isBlank()) raw = Prefs.str(Prefs.K_BEAUTY_PLAY_ALPHA, "")
        val alpha = raw.toFloatOrNull()?.coerceIn(0f, 1f) ?: return
        val it = uiRoots.keys.iterator()
        while (it.hasNext()) {
            val v = it.next()
            if (v.isAttachedToWindow) v.alpha = alpha else it.remove()
        }
    }

    private fun isPlaying(): Boolean {
        val cl = loader ?: return true
        return try {
            val c = Class.forName("gih.w6", false, cl)
            val m = c.getDeclaredMethod("V")
            m.isAccessible = true
            (m.invoke(null) as? Boolean) ?: true
        } catch (_: Throwable) {
            true
        }
    }

    private fun hookRightActionBar(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("qrd.d", false, cl)
            val ctor = c.declaredConstructors.firstOrNull { it.parameterCount >= 5 }
            if (ctor != null) {
                installCtor(module, ctor, "beauty.right.ctor") { chain ->
                    val r = chain.proceed()
                    applyRight(chain.thisObject)
                    r
                }
            }
        } catch (t: Throwable) {
            Logger.d("beauty right miss: ${t.message}")
        }
    }

    private fun applyRight(self: Any?) {
        if (self == null) return
        try {
            val ll = self.javaClass.getMethod("l").invoke(self) as? ViewGroup ?: return
            uiRoots[ll] = "right"
            val hidden = Prefs.str(Prefs.K_BEAUTY_RIGHT_HIDE, "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (hidden.isNotEmpty()) {
                for (i in 0 until ll.childCount) {
                    val child = ll.getChildAt(i)
                    val label = child.contentDescription?.toString()
                        ?: (child as? TextView)?.text?.toString().orEmpty()
                    if (hidden.any { label.contains(it) }) child.visibility = View.GONE
                }
            }
            Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_X, "").toFloatOrNull()?.let { ll.translationX = it }
            Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_Y, "").toFloatOrNull()?.let { ll.translationY = it }
            Prefs.str(Prefs.K_BEAUTY_RIGHT_SCALE, "").toFloatOrNull()?.let {
                ll.scaleX = it
                ll.scaleY = it
            }
        } catch (t: Throwable) {
            Logger.d("beauty right apply miss: ${t.message}")
        }
    }

    private fun hookPendant(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("xfh.a", false, cl)
            val m = c.getDeclaredMethod("doBindView", View::class.java)
            install(module, m, "beauty.pendant") { chain ->
                val r = chain.proceed()
                if (Prefs.bool(Prefs.K_BEAUTY_NO_COIN, false)) {
                    (chain.getArg(0) as? View)?.visibility = View.GONE
                }
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty pendant miss: ${t.message}")
        }
    }

    private fun hookFullscreen(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("hch.b", false, cl)
            val m = c.getDeclaredMethod("kj", Boolean::class.javaPrimitiveType!!)
            install(module, m, "beauty.fullscreen") { chain ->
                if (Prefs.bool(Prefs.K_BEAUTY_NO_FULLSCREEN, false)) null else chain.proceed()
            }
        } catch (t: Throwable) {
            Logger.d("beauty fullscreen miss: ${t.message}")
        }
    }

    private fun hidden(cfg: String, text: String): Boolean {
        if (cfg.isBlank()) return false
        return cfg.split(",").map { it.trim() }.any { it.isNotEmpty() && text.contains(it) }
    }

    private fun replace(cfg: String, text: String): String {
        if (cfg.isBlank()) return text
        val parts = cfg.split("|")
        if (parts.size < 2) return text
        return if (text == parts[0]) parts[1] else text
    }
}
