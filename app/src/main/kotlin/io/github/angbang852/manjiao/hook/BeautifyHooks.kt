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
 * 美化 Hook：底栏、右侧操作栏、挂件、全屏入口、DPI、播放/暂停透明度。
 * 全部 best-effort，命中就改，没命中不崩。
 */
object BeautifyHooks {
    private val tabRoles = java.util.WeakHashMap<View, String>()
    private val uiRoots = java.util.WeakHashMap<View, String>()
    private val bottomViews = java.util.WeakHashMap<View, Boolean>()
    private val bottomContainers = java.util.WeakHashMap<ViewGroup, Boolean>()
    private val rightContainers = java.util.WeakHashMap<ViewGroup, Boolean>()
    private val pendantRoots = java.util.WeakHashMap<View, Boolean>()
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

    fun reapply() {
        bottomViews.keys.toList().forEach { applyBottomView(it) }
        bottomContainers.keys.toList().forEach { reorderBottom(it) }
        rightContainers.keys.toList().forEach { applyRightContainer(it) }
        pendantRoots.keys.toList().forEach { applyPendant(it) }
        applyPlayAlpha(null)
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
            val onLayout = c.getDeclaredMethod(
                "onLayout", Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
            )
            install(module, onLayout, "beauty.bottom.layout") { chain ->
                val r = chain.proceed()
                (chain.thisObject as? View)?.let {
                    bottomViews[it] = true
                    applyBottomView(it)
                }
                r
            }
            for (m in c.declaredMethods) {
                val ps = m.parameterTypes
                if (ps.any { it.name == "o99.k" } && ps.any { it == ViewGroup::class.java }) {
                    install(module, m, "beauty.bottom.bind") { chain ->
                        val r = chain.proceed()
                        (chain.thisObject as? View)?.let {
                            bottomViews[it] = true
                            applyBottomView(it)
                        }
                        r
                    }
                }
            }
        } catch (t: Throwable) {
            Logger.d("beauty bottom miss: ${t.message}")
        }
    }

    private fun applyBottomView(self: View) {
        try {
            uiRoots[self] = "bottom"
            val bf = self.javaClass.getDeclaredField("b")
            bf.isAccessible = true
            val tv = bf.get(self) as? TextView
            val gf = try {
                self.javaClass.getDeclaredField("g").apply { isAccessible = true }
            } catch (_: Throwable) { null }
            val old = (gf?.get(self)?.toString() ?: tv?.text?.toString().orEmpty())
            val hide = hidden(Prefs.str(Prefs.K_BEAUTY_BOTTOM_HIDE, ""), old)
            self.visibility = if (hide) View.GONE else View.VISIBLE
            tv?.visibility = if (hide) View.GONE else View.VISIBLE
            val repl = replace(Prefs.str(Prefs.K_BEAUTY_BOTTOM_TEXT, ""), old)
            if (repl != old) {
                gf?.set(self, repl)
                tv?.text = repl
            }
            if (Prefs.bool(Prefs.K_BEAUTY_BOTTOM_TRANSPARENT, false)) {
                self.setBackgroundColor(Color.TRANSPARENT)
                (self.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
            }
        } catch (t: Throwable) {
            Logger.d("beauty bottom apply miss: ${t.message}")
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
                        bottomContainers[container] = true
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
                val role = tabRoles[ch]
                val text = textOf(ch)
                if (role == "center" || text.contains("拍摄") || text.contains("发布") || text.contains("+")) {
                    center.add(ch)
                }
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

    private fun hookRightActionBar(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("qrd.d", false, cl)
            val ctor = c.declaredConstructors.firstOrNull { it.parameterCount >= 5 }
            if (ctor != null) {
                installCtor(module, ctor, "beauty.right.ctor") { chain ->
                    val r = chain.proceed()
                    try {
                        val self = chain.thisObject
                        val ll = self?.javaClass?.getMethod("l")?.invoke(self) as? ViewGroup
                        if (ll != null) {
                            rightContainers[ll] = true
                            applyRightContainer(ll)
                        }
                    } catch (_: Throwable) {
                    }
                    r
                }
            }
            val bind = c.getDeclaredMethod("a", java.util.List::class.java)
            install(module, bind, "beauty.right.bind") { chain ->
                val r = chain.proceed()
                try {
                    val self = chain.thisObject
                    val ll = self?.javaClass?.getMethod("l")?.invoke(self) as? ViewGroup
                    if (ll != null) {
                        rightContainers[ll] = true
                        applyRightContainer(ll)
                    }
                } catch (_: Throwable) {
                }
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty right miss: ${t.message}")
        }
    }

    private fun applyRightContainer(ll: ViewGroup) {
        val hidden = Prefs.str(Prefs.K_BEAUTY_RIGHT_HIDE, "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (i in 0 until ll.childCount) {
            val child = ll.getChildAt(i)
            val label = labelOf(child)
            if (hidden.any { label.contains(it) }) child.visibility = View.GONE else child.visibility = View.VISIBLE
        }
        uiRoots[ll] = "right"
        ll.translationX = Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_X, "").toFloatOrNull() ?: 0f
        ll.translationY = Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_Y, "").toFloatOrNull() ?: 0f
        val s = Prefs.str(Prefs.K_BEAUTY_RIGHT_SCALE, "").toFloatOrNull() ?: 1f
        ll.scaleX = s
        ll.scaleY = s
    }

    private fun rightKey(label: String): String? = when {
        label.contains("头像") -> "avatar"
        label.contains("点赞") -> "like"
        label.contains("评论") -> "comment"
        label.contains("收藏") -> "collect"
        label.contains("分享") -> "share"
        label.contains("音乐") -> "music"
        else -> null
    }

    private fun hookPendant(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("xfh.a", false, cl)
            val m = c.getDeclaredMethod("doBindView", View::class.java)
            install(module, m, "beauty.pendant") { chain ->
                val r = chain.proceed()
                (chain.getArg(0) as? View)?.let {
                    pendantRoots[it] = true
                    applyPendant(it)
                }
                r
            }
            val onBind = c.getDeclaredMethod("onBind")
            install(module, onBind, "beauty.pendant.bind") { chain ->
                val r = chain.proceed()
                pendantRoots.keys.toList().forEach { applyPendant(it) }
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty pendant miss: ${t.message}")
        }
    }

    private fun applyPendant(root: View) {
        root.visibility = if (Prefs.bool(Prefs.K_BEAUTY_NO_COIN, false)) View.GONE else View.VISIBLE
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

    private fun hookPlaybackAlpha(module: XposedModule, cl: ClassLoader) {
        try {
            val j2 = Class.forName("t6h.j2", false, cl)
            val bind = j2.getDeclaredMethod("doBindView", View::class.java)
            install(module, bind, "beauty.play.alpha.bind") { chain ->
                val r = chain.proceed()
                try {
                    val self = chain.thisObject
                    val f = j2.getDeclaredField("c")
                    f.isAccessible = true
                    (f.get(self) as? View)?.let {
                        uiRoots[it] = "top"
                        applyPlayAlpha(null)
                    }
                } catch (_: Throwable) {
                }
                r
            }

            val i2 = Class.forName("t6h.i2", false, cl)
            val accept = i2.getDeclaredMethod("accept", Any::class.java)
            install(module, accept, "beauty.play.alpha.apply") { chain ->
                val observed = chain.getArg(0) as? Float
                val r = chain.proceed()
                applyPlayAlpha(observed)
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty play alpha miss: ${t.message}")
        }
    }

    private fun applyPlayAlpha(observed: Float?) {
        val playing = if (observed != null) observed < 0.99f else isPlaying()
        val key = if (playing) Prefs.K_BEAUTY_ALPHA_PLAYING else Prefs.K_BEAUTY_ALPHA_PAUSED
        var raw = Prefs.str(key, "")
        if (raw.isBlank()) raw = Prefs.str(Prefs.K_BEAUTY_PLAY_ALPHA, "")
        val alpha = raw.toFloatOrNull()?.coerceIn(0f, 1f) ?: return
        for (v in uiRoots.keys.toList()) {
            if (v.isAttachedToWindow) v.alpha = alpha else uiRoots.remove(v)
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

    private fun textOf(view: View): String = labelOf(view)

    private fun labelOf(view: View): String {
        val cd = view.contentDescription?.toString().orEmpty()
        if (cd.isNotEmpty()) return cd
        if (view is TextView) return view.text?.toString().orEmpty()
        return ""
    }

    private fun hidden(cfg: String, text: String): Boolean {
        if (cfg.isBlank()) return false
        return cfg.split(",").map { it.trim() }.any { it.isNotEmpty() && text.contains(it) }
    }

    private fun replace(cfg: String, text: String): String {
        if (cfg.isBlank()) return text
        for (part in cfg.split(",")) {
            val p = part.split("|")
            if (p.size == 2 && text == p[0].trim()) return p[1]
        }
        return text
    }
}
