package io.github.angbang852.manjiao.hook

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.ui.ConfirmDialog
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

object ConfirmActionHooks {
    private val bypass = ThreadLocal<Boolean>()
    @Volatile private var showing = false

    fun hook(module: XposedModule, cl: ClassLoader) {
        try {
            val m = View::class.java.getDeclaredMethod("performClick")
            module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("confirm.action.click")
                .intercept { chain ->
                    val v = chain.thisObject as? View
                    if (v == null) return@intercept chain.proceed()
                    if (bypass.get() == true) return@intercept chain.proceed()
                    val action = actionOf(v)
                    if (action == null) return@intercept chain.proceed()
                    if (showing) return true
                    val act = unwrap(v.context)
                    if (act == null) return@intercept chain.proceed()
                    showing = true
                    ConfirmDialog.show(
                        act,
                        "确认${action.label}",
                        "确定要${action.label}这条视频吗？",
                        "确认",
                        onConfirm = {
                            bypass.set(true)
                            try {
                                v.performClick()
                            } catch (_: Throwable) {
                            } finally {
                                bypass.set(false)
                            }
                        },
                        onDismiss = { showing = false }
                    )
                    true
                }
        } catch (t: Throwable) {
            Logger.d("confirm action hook miss: ${t.message}")
        }
    }

    private data class Action(val kind: String, val label: String)

    private fun actionOf(v: View): Action? {
        val res = v.resources
        val likeId = res.getIdentifier("like_element_click_layout", "id", "com.smile.gifmaker")
        val collectId = res.getIdentifier("click_area_collect", "id", "com.smile.gifmaker")
        if (likeId != 0 && v.id == likeId && Prefs.bool(Prefs.K_CONFIRM_LIKE, false)) return Action("like", "点赞")
        if (collectId != 0 && v.id == collectId && Prefs.bool(Prefs.K_CONFIRM_COLLECT, false)) return Action("collect", "收藏")
        if (Prefs.bool(Prefs.K_CONFIRM_FOLLOW, false) && containsFollow(v, 3)) return Action("follow", "关注")
        return null
    }

    private fun containsFollow(v: View, depth: Int): Boolean {
        if (v.javaClass.name.contains("TransitionKwaiImageView")) return true
        if (depth <= 0) return false
        var p = v.parent
        var d = 0
        while (p is View && d < depth) {
            if (p.javaClass.name.contains("TransitionKwaiImageView")) return true
            p = p.parent
            d++
        }
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) {
                if (containsFollow(v.getChildAt(i), depth - 1)) return true
            }
        }
        return false
    }

    private fun unwrap(ctx: android.content.Context): Activity? {
        var c: android.content.Context? = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return c as? Activity
    }
}
