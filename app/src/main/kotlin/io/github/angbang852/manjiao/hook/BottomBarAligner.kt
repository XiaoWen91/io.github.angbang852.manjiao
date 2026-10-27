package io.github.angbang852.manjiao.hook

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import kotlin.math.abs

object BottomBarAligner {
    private var aligning = false
    private var lastSig = ""
    private val originalLp = java.util.WeakHashMap<View, ViewGroup.LayoutParams>()

    fun hook(module: XposedModule, cl: ClassLoader) {
        try {
            val c = Class.forName("com.kwai.feature.api.feed.home.wrapper.kcube.widget.BottomBarShootView", false, cl)
            val m = c.getDeclaredMethod(
                "onLayout", Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
            )
            install(module, m, "kira.bottom.align.shoot") { chain ->
                val r = chain.proceed()
                try {
                    val self = chain.thisObject as? View
                    if (self != null) {
                        self.post { align(self.rootView) }
                    }
                } catch (_: Throwable) {}
                r
            }
        } catch (t: Throwable) {
            Logger.d("kira bottom align shoot miss: ${t.message}")
        }
        for (name in arrayOf("com.yxcorp.gifshow.HomeActivity", "com.yxcorp.gifshow.detail.PhotoDetailActivity")) {
            try {
                val c = Class.forName(name, false, cl)
                val m = c.declaredMethods.firstOrNull { it.name == "onResume" && it.parameterCount == 0 } ?: continue
                install(module, m, "kira.bottom.align.resume.$name") { chain ->
                    val r = chain.proceed()
                    try {
                        val a = chain.thisObject as? Activity
                        a?.window?.decorView?.post { align(a.window?.decorView) }
                    } catch (_: Throwable) {}
                    r
                }
            } catch (t: Throwable) {
                Logger.d("kira bottom align resume miss $name: ${t.message}")
            }
        }
    }

    private fun install(module: XposedModule, m: Method, id: String, hooker: XposedInterface.Hooker) {
        module.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).setId(id).intercept(hooker)
    }

    fun align(root: View?) {
        if (root == null) return
        if (!Prefs.bool(Prefs.K_BEAUTY_BOTTOM_AUTO, true)) return
        if (aligning) return
        aligning = true
        root.post {
            try {
                doAlign(root)
            } catch (t: Throwable) {
                Logger.d("kira bottom align miss: ${t.message}")
            } finally {
                aligning = false
            }
        }
    }

    private fun doAlign(root: View) {
        val container = findByResName(root, "bottom_bar_container") as? ViewGroup
            ?: (root as? ViewGroup) ?: return
        val tabLayout = findByResName(container, "tab_layout") as? ViewGroup ?: container
        val indicator = findIndicator(tabLayout) ?: findIndicator(container) ?: return

        var hidden = 0
        val visible = ArrayList<View>()
        for (i in 0 until indicator.childCount) {
            val ch = indicator.getChildAt(i)
            if (ch.visibility != View.VISIBLE) {
                hidden++
                continue
            }
            val lp = ch.layoutParams
            if (ch.width <= 0 || ch.height <= 0) continue
            if (lp != null && lp.width == 0 && lp.height == 0) continue
            visible.add(ch)
        }

        val shoot = findShoot(container)
        if (hidden == 0) {
            if (Prefs.str(Prefs.K_BEAUTY_HIDDEN_ELEMS, "").isBlank()) {
                for (i in 0 until indicator.childCount) {
                    val ch = indicator.getChildAt(i)
                    originalLp.remove(ch)?.let { ch.layoutParams = it }
                }
                shoot?.translationX = 0f
                indicator.requestLayout()
                lastSig = ""
            }
            return
        }
        if (visible.isEmpty()) return

        val shootVisible = shoot != null && shoot.visibility == View.VISIBLE
        val n = visible.size + if (shootVisible) 1 else 0
        val barWidth = container.width.takeIf { it > 0 } ?: indicator.width
        if (barWidth <= 0 || n <= 0) return
        val cell = barWidth / n
        val shootIndex = n / 2
        val sig = "$hidden/$n/$barWidth"
        val same = sig == lastSig
        if (!same) lastSig = sig

        if (!same) {
            var layoutChanged = false
            for ((idx, v) in visible.withIndex()) {
                val lp = v.layoutParams ?: continue
                if (!originalLp.containsKey(v)) originalLp[v] = lp
                if (lp.width != cell) {
                    lp.width = cell
                    v.layoutParams = lp
                    layoutChanged = true
                }
                val mlp = lp as? ViewGroup.MarginLayoutParams
                if (mlp != null) {
                    val left = if (shootVisible && idx >= shootIndex) cell else 0
                    if (mlp.leftMargin != left || mlp.rightMargin != 0) {
                        mlp.leftMargin = left
                        mlp.rightMargin = 0
                        v.layoutParams = mlp
                        layoutChanged = true
                    }
                }
            }
            if (layoutChanged) indicator.requestLayout()
        }

        if (shootVisible && shoot != null) {
            shoot.post {
                val target = shootIndex * cell + cell / 2f
                val current = shoot.left + shoot.width / 2f
                val dx = target - current
                shoot.translationX = if (abs(dx) > 0.5f) dx else 0f
            }
        }
    }

    private fun findByResName(v: View, name: String): View? {
        if (resName(v) == name) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findByResName(v.getChildAt(i), name)?.let { return it }
            }
        }
        return null
    }

    private fun findIndicator(v: View): ViewGroup? {
        if (v is ViewGroup && v.javaClass.name.contains("Indicator")) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findIndicator(v.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findShoot(v: View): View? {
        val cls = v.javaClass.name
        val desc = v.contentDescription?.toString().orEmpty()
        if (cls.contains("ShootView") || resName(v) == "bottom_bar_shoot" ||
            desc.contains("拍摄") || desc.contains("发布")) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findShoot(v.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun resName(v: View): String? {
        val id = v.id
        if (id == 0 || id == View.NO_ID) return null
        return try {
            v.resources.getResourceEntryName(id)
        } catch (_: Throwable) {
            null
        }
    }
}
