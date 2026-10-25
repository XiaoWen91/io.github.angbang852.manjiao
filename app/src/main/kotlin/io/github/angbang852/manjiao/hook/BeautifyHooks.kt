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
    private val bottomLp = java.util.WeakHashMap<View, ViewGroup.LayoutParams>()
    private val bottomParents = java.util.WeakHashMap<View, ViewGroup>()
    private val bottomIndexes = java.util.WeakHashMap<View, Int>()
    private val bottomTargets = java.util.WeakHashMap<View, View>()
    private val bottomRegions = java.util.WeakHashMap<Any, Boolean>()
    private val bottomTabEntries = java.util.WeakHashMap<ViewGroup, MutableList<Triple<View, Int, ViewGroup.LayoutParams>>>()
    private val rightTagged = java.util.WeakHashMap<ViewGroup, Boolean>()
    private val bottomContainers = java.util.WeakHashMap<ViewGroup, Boolean>()
    private val rightContainers = java.util.WeakHashMap<ViewGroup, Boolean>()
    private val pendantRoots = java.util.WeakHashMap<View, Boolean>()
    private var loader: ClassLoader? = null
    private var lastPhoto: Any? = null

    fun hook(module: XposedModule, cl: ClassLoader) {
        loader = cl
        hookDpi(module)
        hookBottomBar(module, cl)
        hookBottomReorder(module, cl)
        hookBottomShoot(module, cl)
        hookRightActionBar(module, cl)
        hookPendant(module, cl)
        hookFullscreen(module, cl)
        hookHomeCoin(module, cl)
        hookUnionView(module, cl)
        hookCaptionInfo(module, cl)
        hookCaptionTextView(module, cl)
        hookPlaybackAlpha(module, cl)
    }

    fun reapply() {
        try {
            bottomViews.keys.toList().forEach { applyBottomView(it) }
            bottomContainers.keys.toList().forEach { reorderBottom(it) }
            bottomRegions.keys.toList().forEach { applyBottomContainer(it) }
            rightContainers.keys.toList().forEach { applyRightContainer(it) }
            pendantRoots.keys.toList().forEach { applyPendant(it) }
            applyPlayAlpha(null)
        } catch (t: Throwable) {
            Logger.d("beauty reapply miss: ${t.message}")
        }
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
            try {
                val tc = Class.forName("com.kwai.kcube.ext.actionbar.region.TabStripRegion", false, cl)
                val om = tc.getDeclaredMethod("o")
                install(module, om, "beauty.bottom.container") { chain ->
                    val r = chain.proceed()
                    val self = chain.thisObject
                    if (self != null) {
                        bottomRegions[self] = true
                        try {
                            if (Prefs.bool(Prefs.K_BEAUTY_BOTTOM_TRANSPARENT, false)) {
                                for (n in arrayOf("e", "i", "h", "j")) {
                                    try {
                                        val f = tc.getDeclaredField(n)
                                        f.isAccessible = true
                                        (f.get(self) as? View)?.background = null
                                    } catch (_: Throwable) {
                                    }
                                }
                            }
                            applyBottomContainer(self)
                        } catch (_: Throwable) {
                        }
                    }
                    r
                }
            } catch (_: Throwable) {
            }
        } catch (t: Throwable) {
            Logger.d("beauty bottom miss: ${t.message}")
        }
    }

    private fun hookBottomShoot(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.kwai.feature.api.feed.home.wrapper.kcube.widget.BottomBarShootView", false, cl)
            val m = c.getDeclaredMethod(
                "onLayout", Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
            )
            install(module, m, "beauty.bottom.shoot") { chain ->
                val r = chain.proceed()
                try {
                    val self = chain.thisObject as? View
                    if (self != null) {
                        if (Prefs.bool(Prefs.K_BEAUTY_BOTTOM_STYLE, false)) {
                            val p = self.parent as? ViewGroup
                            if (p != null && p.width > 0 && self.width > 0) {
                                val dx = centerGapTranslation(self, p)
                                try { self.translationX = dx } catch (_: Throwable) {}
                            }
                        } else {
                            try { self.translationX = 0f } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {
                }
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty bottom shoot miss: ${t.message}")
        }
    }

    private fun hookHomeCoin(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.yxcorp.gifshow.HomeActivity", false, cl)
            val m = c.getDeclaredMethod("onResume")
            install(module, m, "beauty.home.coin") { chain ->
                val r = chain.proceed()
                try {
                    val a = chain.thisObject as? android.app.Activity
                    if (a != null) applyHomeViews(a.window.decorView)
                } catch (_: Throwable) {
                }
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty home coin miss: ${t.message}")
        }
    }

    private fun applyHomeViews(v: View, path: String = "") {
        try {
            val cls = v.javaClass.name
            val text = deepText(v)
            if (Prefs.bool(Prefs.K_BEAUTY_NO_COIN, false)) {
                if (cls.contains("UnionView")) {
                    v.visibility = View.GONE
                } else if (cls.contains("TKYogaLayout") &&
                    (text.contains("红包") || text.contains("金币") || text.contains("奖励") || text.contains("现金"))) {
                    v.visibility = View.GONE
                }
            }
            if (Prefs.bool(Prefs.K_BEAUTY_BOTTOM_TRANSPARENT, false) &&
                path == "0/1/0/0/1/0/0/0/0/0/1/0/0" && cls.contains("ImageSwitchView")) {
                v.visibility = View.GONE
            }
            if (cls.contains("TabLayout\$TabView") || cls.contains("TabLayout\$Tabview")) {
                val hidden = Prefs.str(Prefs.K_BEAUTY_BOTTOM_HIDE, "")
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (hidden.any { text.contains(it) }) {
                    v.visibility = View.GONE
                    (v.parent as? View)?.requestLayout()
                    v.post { updateBottomShoot(v.rootView) }
                }
            }
            if (cls.contains("BottomBarShootView")) {
                v.post { updateBottomShoot(v.rootView) }
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    applyHomeViews(v.getChildAt(i), if (path.isEmpty()) i.toString() else "$path/$i")
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun centerGapTranslation(v: View, p: ViewGroup): Float {
        val center = v.left + v.width / 2f
        var leftMax = 0
        var rightMin = p.width
        var hasLeft = false
        var hasRight = false
        for (i in 0 until p.childCount) {
            val c = p.getChildAt(i)
            if (c === v || c.visibility != View.VISIBLE || c.width <= 0) continue
            val cc = c.left + c.width / 2f
            if (cc < center) {
                if (!hasLeft || c.right > leftMax) { leftMax = c.right; hasLeft = true }
            } else {
                if (!hasRight || c.left < rightMin) { rightMin = c.left; hasRight = true }
            }
        }
        val gapCenter = when {
            hasLeft && hasRight -> (leftMax + rightMin) / 2f
            hasLeft -> (leftMax + p.width) / 2f
            hasRight -> rightMin / 2f
            else -> p.width / 2f
        }
        val desiredLeft = gapCenter - v.width / 2f
        return desiredLeft - v.left
    }

    private fun updateBottomShoot(v: View?) {
        if (v == null) return
        try {
            if (v.javaClass.name.contains("BottomBarShootView") && Prefs.bool(Prefs.K_BEAUTY_BOTTOM_STYLE, false)) {
                val p = v.parent as? ViewGroup
                if (p != null && p.width > 0 && v.width > 0) {
                    v.translationX = centerGapTranslation(v, p)
                }
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) updateBottomShoot(v.getChildAt(i))
            }
        } catch (_: Throwable) {
        }
    }

    private fun hookUnionView(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.kuaishou.growth.pendant.coin.core.kds.unionwidget.UnionView", false, cl)
            val ctor = c.declaredConstructors.minByOrNull { it.parameterCount }
            if (ctor != null) {
                installCtor(module, ctor, "beauty.union.ctor") { chain ->
                    val r = chain.proceed()
                    try {
                        val self = chain.thisObject as? View
                        if (self != null && Prefs.bool(Prefs.K_BEAUTY_NO_COIN, false)) self.visibility = View.GONE
                    } catch (_: Throwable) {
                    }
                    r
                }
            }
            for (m in c.declaredMethods) {
                if (m.name == "onAttachedToWindow" || m.name == "onVisibilityChanged") {
                    install(module, m, "beauty.union.${m.name}") { chain ->
                        val r = chain.proceed()
                        try {
                            val self = chain.thisObject as? View
                            if (self != null && Prefs.bool(Prefs.K_BEAUTY_NO_COIN, false) && self.visibility != View.GONE) {
                                self.visibility = View.GONE
                            }
                        } catch (_: Throwable) {
                        }
                        r
                    }
                }
            }
        } catch (t: Throwable) {
            Logger.d("beauty union miss: ${t.message}")
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
            var old = (gf?.get(self)?.toString() ?: tv?.text?.toString().orEmpty())
            if (old.isBlank()) old = labelFromF(self)
            val hide = hidden(Prefs.str(Prefs.K_BEAUTY_BOTTOM_HIDE, ""), old)
            val repl = replace(Prefs.str(Prefs.K_BEAUTY_BOTTOM_TEXT, ""), old)
            if (!hide && repl != old) {
                gf?.set(self, repl)
                tv?.text = repl
            }
            if (Prefs.bool(Prefs.K_BEAUTY_BOTTOM_TRANSPARENT, false)) {
                self.background = null
                var p: View? = self.parent as? View
                var depth = 1
                while (p != null && depth < 12) {
                    p.background = null
                    p = p.parent as? View
                    depth++
                }
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
                        val parent = (v.parent as? ViewGroup) ?: container
                        bottomContainers[parent] = true
                        parent.postDelayed({ reorderBottom(parent) }, 350L)
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
            val nc = Class.forName("com.yxcorp.gifshow.detail.slideplay.nasa.vm.slide.n", false, cl)
            val b0 = nc.getDeclaredMethod("B0", android.widget.RelativeLayout::class.java)
            install(module, b0, "beauty.right.hook") { chain ->
                val r = chain.proceed()
                try {
                    val ll = r?.javaClass?.getMethod("l")?.invoke(r) as? ViewGroup
                    if (ll != null) {
                        rightTagged[ll] = true
                        rightContainers[ll] = true
                        applyRightContainer(ll)
                        ll.postDelayed({ applyRightContainer(ll) }, 300L)
                    }
                } catch (_: Throwable) {
                }
                r
            }
            val qc = Class.forName("qrd.d", false, cl)
            val bind = qc.getDeclaredMethod("a", java.util.List::class.java)
            install(module, bind, "beauty.right.bind") { chain ->
                val r = chain.proceed()
                try {
                    val self = chain.thisObject
                    val ll = self?.javaClass?.getMethod("l")?.invoke(self) as? ViewGroup
                    if (ll != null && rightTagged.containsKey(ll)) {
                        applyRightContainer(ll)
                        ll.postDelayed({ applyRightContainer(ll) }, 200L)
                    }
                } catch (_: Throwable) {
                }
                r
            }
            try {
                val iM = qc.getDeclaredMethod("i")
                install(module, iM, "beauty.right.layout") { chain ->
                    val r = chain.proceed()
                    try {
                        val self = chain.thisObject
                        val ll = self?.javaClass?.getMethod("l")?.invoke(self) as? ViewGroup
                        if (ll != null && rightTagged.containsKey(ll)) applyRightContainer(ll)
                    } catch (_: Throwable) {
                    }
                    r
                }
            } catch (_: Throwable) {
            }
        } catch (t: Throwable) {
            Logger.d("beauty right miss: ${t.message}")
        }
    }

    private fun applyRightContainer(ll: ViewGroup) {
        if (!rightTagged.containsKey(ll)) return
        if (ll.childCount < 2) return
        val hidden = Prefs.str(Prefs.K_BEAUTY_RIGHT_HIDE, "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        var matched = 0
        for (i in 0 until ll.childCount) {
            val child = ll.getChildAt(i)
            val label = labelOf(child)
            if (hidden.any { label.contains(it) }) {
                child.visibility = View.GONE
                matched++
            } else {
                child.visibility = View.VISIBLE
            }
        }
        // no index fallback: filtering by element id is safer
        if (ll.childCount < 4) return
        uiRoots[ll] = "right"
        setRightTranslationX(ll, Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_X, "").toFloatOrNull() ?: 0f)
        try {
            ll.translationY = Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_Y, "").toFloatOrNull() ?: 0f
        } catch (_: Throwable) {
        }
        val s = Prefs.str(Prefs.K_BEAUTY_RIGHT_SCALE, "").toFloatOrNull() ?: 1f
        try {
            ll.scaleX = s
            ll.scaleY = s
        } catch (_: Throwable) {
        }
    }

    private fun setRightTranslationX(ll: ViewGroup, x: Float) {
        try {
            val m = ll.javaClass.getMethod(
                "setTranslationX",
                String::class.java,
                Float::class.javaPrimitiveType!!
            )
            m.isAccessible = true
            m.invoke(ll, "manjiao_right", x)
        } catch (_: Throwable) {
            try {
                ll.translationX = x
            } catch (_: Throwable) {
            }
        }
    }

    private fun containsText(group: ViewGroup): Boolean {
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i)
            if (c is TextView && !c.text.isNullOrEmpty()) return true
            if (c is ViewGroup && containsText(c)) return true
        }
        return false
    }

    private fun applyBottomContainer(region: Any?) {
        if (region == null) return
        try {
            val f = region.javaClass.getDeclaredField("i")
            f.isAccessible = true
            val strip = f.get(region) ?: return
            val container = strip.javaClass.getMethod("getTabsContainer").invoke(strip) as? ViewGroup
            if (container != null) {
                uiRoots[container] = "bottom"
                if (Prefs.bool(Prefs.K_BEAUTY_BOTTOM_TRANSPARENT, false)) container.background = null
            }
            uiRoots[strip as View] = "bottom"
            if (Prefs.bool(Prefs.K_BEAUTY_BOTTOM_TRANSPARENT, false)) {
                (strip as View).background = null
                ((strip as View).parent as? View)?.background = null
            }
            val hidden = Prefs.str(Prefs.K_BEAUTY_BOTTOM_HIDE, "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val tabsField = findField(strip.javaClass, "e")
            val tabs = tabsField?.apply { isAccessible = true }?.get(strip) as? List<*>
            if (tabs != null && container != null) {
                var plusIndex = -1
                for (idx in tabs.indices) {
                    val item = tabs[idx] ?: continue
                    val text = try {
                        item.javaClass.getMethod("f").invoke(item)?.toString().orEmpty()
                    } catch (_: Throwable) { "" }
                    val child = container.getChildAt(idx)
                    if (child != null) {
                        child.visibility = if (hidden.any { text.contains(it) }) View.GONE else View.VISIBLE
                    }
                    if (text.contains("拍摄") || text.contains("发布") || text.contains("+")) plusIndex = idx
                }
                if (Prefs.bool(Prefs.K_BEAUTY_BOTTOM_STYLE, false) && plusIndex >= 0 && plusIndex != tabs.size - 1) {
                    val child = container.getChildAt(plusIndex)
                    if (child != null) {
                        container.removeViewAt(plusIndex)
                        container.addView(child)
                    }
                    (tabs as? MutableList<Any?>)?.let { list ->
                        val moving = list[plusIndex]
                        list.removeAt(plusIndex)
                        list.add(moving)
                    }
                }
            }
            container?.requestLayout()
        } catch (_: Throwable) {
        }
    }

    private fun findField(cls: Class<*>, name: String): java.lang.reflect.Field? {
        var c: Class<*>? = cls
        while (c != null) {
            try {
                return c.getDeclaredField(name)
            } catch (_: Throwable) {
                c = c.superclass
            }
        }
        return null
    }

    private fun deepText(v: View): String {
        val sb = StringBuilder()
        fun walk(x: View) {
            val cd = x.contentDescription?.toString().orEmpty()
            if (cd.isNotEmpty()) sb.append(cd).append(' ')
            if (x is TextView && !x.text.isNullOrEmpty()) sb.append(x.text).append(' ')
            if (x is ViewGroup) {
                for (i in 0 until x.childCount) walk(x.getChildAt(i))
            }
        }
        walk(v)
        return sb.toString()
    }

    private fun labelFromF(self: View): String {
        return try {
            val f = self.javaClass.getDeclaredField("f")
            f.isAccessible = true
            val k = f.get(self)
            val b = k?.javaClass?.getDeclaredField("b")
            b?.isAccessible = true
            b?.get(k)?.toString().orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun shouldHideRightElement(e: Any?): Boolean {
        if (e == null) return false
        val hidden = Prefs.str(Prefs.K_BEAUTY_RIGHT_HIDE, "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (hidden.isEmpty()) return false
        val id = try {
            e.javaClass.getMethod("I").invoke(e)?.toString().orEmpty().lowercase()
        } catch (_: Throwable) {
            e.javaClass.name.lowercase()
        }
        val map = mapOf(
            "头像" to listOf("avatar", "head", "user"),
            "点赞" to listOf("like"),
            "评论" to listOf("comment"),
            "收藏" to listOf("collect", "favorite"),
            "分享" to listOf("share"),
            "音乐" to listOf("music", "audio")
        )
        return hidden.any { name -> map[name]?.any { id.contains(it) } == true }
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

    private fun hookCaptionInfo(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("u9b.a", false, cl)
            val wf = Class.forName("wnb.f", false, cl)
            val m = c.getDeclaredMethod("b", wf, Boolean::class.javaPrimitiveType!!)
            install(module, m, "beauty.caption.time") { chain ->
                val r = chain.proceed()
                lastPhoto = chain.getArg(0)
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty caption time miss: ${t.message}")
        }
        try {
            val qc = Class.forName("com.yxcorp.gifshow.entity.QPhoto", false, cl)
            val gm = qc.getDeclaredMethod("getKCreatedTime")
            install(module, gm, "beauty.caption.photo") { chain ->
                val r = chain.proceed()
                lastPhoto = chain.thisObject
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty caption photo miss: ${t.message}")
        }
    }

    private fun hookCaptionTextView(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.kwai.slide.play.detail.common.information.caption.CaptionTextView", false, cl)
            val m = c.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            install(module, m, "beauty.caption.textview") { chain ->
                val r = chain.proceed()
                try {
                    val tv = chain.thisObject as? TextView
                    val photo = lastPhoto
                    if (tv != null && photo != null &&
                        (Prefs.bool(Prefs.K_BEAUTY_FORCE_TIME, false) || Prefs.bool(Prefs.K_BEAUTY_FORCE_IP, false))) {
                        val add = captionExtra(photo)
                        val old = tv.text?.toString().orEmpty()
                        if (add.isNotBlank() && !old.contains(add)) {
                            val sb = android.text.SpannableStringBuilder()
                            if (old.isNotEmpty()) sb.append(old).append('\n')
                            val start = sb.length
                            sb.append(add)
                            sb.setSpan(
                                android.text.style.ForegroundColorSpan(0xFF888888.toInt()),
                                start,
                                sb.length,
                                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            tv.text = sb
                        }
                    }
                } catch (_: Throwable) {
                }
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty caption textview miss: ${t.message}")
        }
    }

    private fun captionExtra(photo: Any?): String {
        if (photo == null) return ""
        val extra = ArrayList<String>()
        if (Prefs.bool(Prefs.K_BEAUTY_FORCE_TIME, false)) {
            val ts = callLong(photo, "getKCreatedTime")
            if (ts > 0L) extra.add(formatTimestamp(ts, Prefs.bool(Prefs.K_BEAUTY_TIME_SECONDS, true)))
        }
        if (Prefs.bool(Prefs.K_BEAUTY_FORCE_IP, false)) {
            val ip = callString(photo, "getCaptionIpLocation")
            if (ip.isNotBlank()) {
                val clean = ip.removePrefix("IP属地：").removePrefix("IP属地:").removePrefix("IP:")
                extra.add("IP:$clean")
            }
        }
        return extra.joinToString("  ")
    }

    private fun looksLikeTime(s: String): Boolean {
        if (s.isBlank()) return false
        return s.contains("前") || s.contains("昨天") || s.contains("刚刚") ||
            Regex("""\d{1,2}:\d{2}""").containsMatchIn(s) ||
            Regex("""\d{1,2}-\d{1,2}""").containsMatchIn(s)
    }

    private fun callString(obj: Any, name: String): String {
        return try {
            val m = obj.javaClass.getMethod(name)
            m.isAccessible = true
            m.invoke(obj)?.toString().orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun callLong(obj: Any, name: String): Long {
        return try {
            val m = obj.javaClass.getMethod(name)
            m.isAccessible = true
            (m.invoke(obj) as? Number)?.toLong() ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    private fun formatTimestamp(ts: Long, seconds: Boolean): String {
        val pattern = if (seconds) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd HH:mm"
        return java.text.SimpleDateFormat(pattern, java.util.Locale.US).format(java.util.Date(ts))
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
                    (f.get(self) as? View)?.let { v ->
                        uiRoots[v] = "top"
                        var p = v.parent as? View
                        var depth = 0
                        while (p != null && depth < 2) {
                            uiRoots[p] = "topParent"
                            p = p.parent as? View
                            depth++
                        }
                        applyPlayAlpha(null)
                    }
                } catch (_: Throwable) {
                }
                r
            }

            val i2 = Class.forName("t6h.i2", false, cl)
            val accept = i2.getDeclaredMethod("accept", Any::class.java)
            install(module, accept, "beauty.play.alpha.apply") { chain ->
                val playing = isPlaying()
                val key = if (playing) Prefs.K_BEAUTY_ALPHA_PLAYING else Prefs.K_BEAUTY_ALPHA_PAUSED
                var raw = Prefs.str(key, "")
                if (raw.isBlank()) raw = Prefs.str(Prefs.K_BEAUTY_PLAY_ALPHA, "")
                val target = raw.toFloatOrNull()?.coerceIn(0f, 1f)
                val r = if (target != null) chain.proceed(arrayOf<Any?>(target)) else chain.proceed()
                applyPlayAlpha(null)
                r
            }
        } catch (t: Throwable) {
            Logger.d("beauty play alpha miss: ${t.message}")
        }
    }

    private fun applyPlayAlpha(observed: Float?) {
        try {
            val playing = isPlaying()
            val key = if (playing) Prefs.K_BEAUTY_ALPHA_PLAYING else Prefs.K_BEAUTY_ALPHA_PAUSED
            var raw = Prefs.str(key, "")
            if (raw.isBlank()) raw = Prefs.str(Prefs.K_BEAUTY_PLAY_ALPHA, "")
            val alpha = raw.toFloatOrNull()?.coerceIn(0f, 1f) ?: return
            for (v in uiRoots.keys.toList()) {
                if (v.isAttachedToWindow) v.alpha = alpha else uiRoots.remove(v)
            }
        } catch (t: Throwable) {
            Logger.d("beauty play alpha apply miss: ${t.message}")
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

    private fun textOf(view: View): String {
        return try {
            val f = view.javaClass.getDeclaredField("g")
            f.isAccessible = true
            f.get(view)?.toString().orEmpty()
        } catch (_: Throwable) {
            labelOf(view)
        }
    }

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
