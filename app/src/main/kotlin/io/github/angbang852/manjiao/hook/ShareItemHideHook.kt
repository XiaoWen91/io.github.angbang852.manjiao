package io.github.angbang852.manjiao.hook

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

object ShareItemHideHook {
    private val hiddenByUs = java.util.WeakHashMap<View, Int>()

    fun hook(module: XposedModule, cl: ClassLoader) {
        try {
            val m = Dialog::class.java.getDeclaredMethod("show")
            module.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).setId("kira.share.hide").intercept { chain ->
                val r = chain.proceed()
                try {
                    val dlg = chain.thisObject as Dialog
                    val root = dlg.window?.decorView as? ViewGroup
                    if (root != null) {
                        root.post { apply(root) }
                        root.postDelayed({ apply(root) }, 300L)
                        root.postDelayed({ apply(root) }, 800L)
                        root.postDelayed({ apply(root) }, 1500L)
                    }
                } catch (_: Throwable) {}
                r
            }
        } catch (t: Throwable) {
            Logger.d("kira share hide hook miss: ${t.message}")
        }
    }

    fun apply(root: View?) {
        if (root == null) return
        val set = Prefs.str(Prefs.K_BEAUTY_SHARE_HIDE, "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (set.isEmpty()) return
        try {
            walk(root, set)
        } catch (t: Throwable) {
            Logger.d("kira share hide apply miss: ${t.message}")
        }
    }

    private fun walk(v: View, set: Set<String>) {
        if (v is TextView) {
            val t = v.text?.toString()?.trim()
            if (t != null && set.contains(t)) {
                var target: View = v
                var depth = 0
                while (depth < 3) {
                    val p = target.parent as? ViewGroup ?: break
                    if (p.childCount <= 6) {
                        target = p
                        depth++
                    } else {
                        break
                    }
                }
                if (!hiddenByUs.containsKey(target)) {
                    hiddenByUs[target] = target.visibility
                    target.visibility = View.GONE
                }
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), set)
        }
    }
}
