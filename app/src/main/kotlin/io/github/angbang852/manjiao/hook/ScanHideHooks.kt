package io.github.angbang852.manjiao.hook

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.ui.FrostedKit
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

object ScanHideHooks {
    private const val TAG_OVERLAY = "kira_scan_overlay"
    private val hiddenByUs = java.util.WeakHashMap<View, String>()
    private val originalVis = java.util.WeakHashMap<View, Int>()
    private var panel: Dialog? = null

    data class Elem(val token: String, val label: String, val x: Int, val y: Int, val w: Int, val h: Int)

    fun hook(module: XposedModule, cl: ClassLoader) {
        for (name in arrayOf("com.yxcorp.gifshow.detail.PhotoDetailActivity", "com.yxcorp.gifshow.HomeActivity")) {
            try {
                val c = Class.forName(name, false, cl)
                val m = c.declaredMethods.firstOrNull { it.name == "onResume" && it.parameterCount == 0 } ?: continue
                install(module, m, "kira.scan.resume.$name") { chain ->
                    val r = chain.proceed()
                    try {
                        val a = chain.thisObject as? Activity
                        if (a != null) {
                            ActivityBridge.install(a)
                            apply(a.window?.decorView)
                            a.window?.decorView?.postDelayed({ apply(a.window?.decorView) }, 600L)
                        }
                    } catch (_: Throwable) {}
                    r
                }
            } catch (t: Throwable) {
                Logger.d("kira scan hook miss $name: ${t.message}")
            }
        }
    }

    private fun install(module: XposedModule, m: Method, id: String, hooker: XposedInterface.Hooker) {
        module.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).setId(id).intercept(hooker)
    }

    private fun isHiddenToken(v: View, token: String, hidden: Set<String>): Boolean {
        if (hidden.contains(token)) return true
        val label = labelFor(v)
        return label.isNotEmpty() && hidden.contains("text:$label")
    }

    fun apply(root: View?) {
        if (root == null) return
        val hidden = hiddenSet()
        try {
            walkApply(root, hidden)
            BottomBarAligner.align(root)
        } catch (t: Throwable) {
            Logger.d("kira scan apply miss: ${t.message}")
        }
    }

    private fun walkApply(v: View, hidden: Set<String>) {
        if (v.tag == TAG_OVERLAY) return
        val token = tokenFor(v)
        val isHidden = isHiddenToken(v, token, hidden)
        if (token.isNotEmpty()) {
            if (isHidden) {
                if (!hiddenByUs.containsKey(v)) {
                    originalVis[v] = v.visibility
                    hiddenByUs[v] = token
                }
                if (v.visibility != View.GONE) v.visibility = View.GONE
            } else if (hiddenByUs.containsKey(v)) {
                val ov = originalVis[v] ?: View.VISIBLE
                if (v.visibility != ov) v.visibility = ov
                hiddenByUs.remove(v)
                originalVis.remove(v)
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walkApply(v.getChildAt(i), hidden)
        }
    }

    fun showPanel(act: Activity) {
        try {
            if (panel?.isShowing == true) return
            val (d, box) = FrostedKit.dialog(act)
            val header = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(act).apply {
                text = "隐藏 Tab / 视频控件"
                textSize = 17f
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val scanBtn = TextView(act).apply {
                text = "⟳ 扫描"
                textSize = 13f
                setTextColor(0xFFFF6A00.toInt())
                setPadding(dp(act, 12), dp(act, 6), dp(act, 12), dp(act, 6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(act, 10).toFloat()
                    setColor(0x33FF6A00.toInt())
                    setStroke(dp(act, 1), 0x66FF6A00.toInt())
                }
                isClickable = true
                setOnClickListener { d.dismiss(); showScanResultPanel(act) }
            }
            header.addView(scanBtn)
            box.addView(header)
            val tabs = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(act, 8), 0, dp(act, 6)) }
            box.addView(tabs)
            val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            val scroll = ScrollView(act).apply { isVerticalScrollBarEnabled = false }
            scroll.addView(list)
            box.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(act, 420)))
            var selected = "底栏"
            val cats = listOf("顶栏", "底栏", "视频控件", "分享面板")
            fun refresh() {
                val hidden = hiddenSet()
                tabs.removeAllViews()
                for (cat in cats) {
                    tabs.addView(TextView(act).apply {
                        text = cat
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setPadding(dp(act, 10), dp(act, 6), dp(act, 10), dp(act, 6))
                        setTextColor(if (cat == selected) 0xFFFF6A00.toInt() else 0xB3FFFFFF.toInt())
                        background = GradientDrawable().apply {
                            cornerRadius = dp(act, 12).toFloat()
                            setColor(if (cat == selected) 0x33FF6A00.toInt() else 0x00FFFFFF)
                        }
                        isClickable = true
                        setOnClickListener { selected = cat; refresh() }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(act, 4) })
                }
                list.removeAllViews()
                for (label in presetsFor(selected)) {
                    val token = presetToken(label)
                    val row = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(act, 10), dp(act, 10), dp(act, 10), dp(act, 10))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(act, 14).toFloat()
                            setColor(0x22FFFFFF.toInt())
                            setStroke(dp(act, 1), 0x33FFFFFF.toInt())
                        }
                    }
                    row.addView(TextView(act).apply {
                        text = label
                        textSize = 14f
                        setTextColor(Color.WHITE)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(FrostedKit.iosSwitch(act, hidden.contains(token)) { checked ->
                        setHidden(act, token, checked)
                        apply(act.window?.decorView)
                        BottomBarAligner.align(act.window?.decorView)
                    })
                    list.addView(row)
                    list.addView(View(act).apply { layoutParams = LinearLayout.LayoutParams(1, dp(act, 6)) })
                }
            }
            refresh()
            box.addView(TextView(act).apply {
                text = "关闭"
                textSize = 14f
                setTextColor(0xFFFF6A00.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(act, 12), 0, 0)
                setOnClickListener { d.dismiss() }
            })
            d.setContentView(box)
            d.setOnDismissListener { panel = null }
            panel = d
            d.show()
        } catch (t: Throwable) {
            Logger.d("kira scan preset miss: ${t.message}")
        }
    }

    fun showScanResultPanel(act: Activity) {
        try {
            if (panel?.isShowing == true) return
            val (d, box) = FrostedKit.dialog(act)
            val header = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(act).apply {
                text = "扫描结果"
                textSize = 17f
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val scanBtn = TextView(act).apply {
                text = "⟳ 重新扫描"
                textSize = 13f
                setTextColor(0xFFFF6A00.toInt())
                setPadding(dp(act, 12), dp(act, 6), dp(act, 12), dp(act, 6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(act, 10).toFloat()
                    setColor(0x33FF6A00.toInt())
                    setStroke(dp(act, 1), 0x66FF6A00.toInt())
                }
                isClickable = true
            }
            header.addView(scanBtn)
            box.addView(header)
            val tabs = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(act, 8), 0, dp(act, 6)) }
            box.addView(tabs)
            val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            val scroll = ScrollView(act).apply { isVerticalScrollBarEnabled = false }
            scroll.addView(list)
            box.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(act, 420)))
            var selected = "底栏"
            val cats = listOf("顶栏", "底栏", "视频控件", "分享面板")
            fun refresh() {
                val curRoot = act.window?.decorView ?: return
                val items = scan(curRoot)
                val hidden = hiddenSet()
                tabs.removeAllViews()
                for (cat in cats) {
                    tabs.addView(TextView(act).apply {
                        text = cat
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setPadding(dp(act, 10), dp(act, 6), dp(act, 10), dp(act, 6))
                        setTextColor(if (cat == selected) 0xFFFF6A00.toInt() else 0xB3FFFFFF.toInt())
                        background = GradientDrawable().apply {
                            cornerRadius = dp(act, 12).toFloat()
                            setColor(if (cat == selected) 0x33FF6A00.toInt() else 0x00FFFFFF)
                        }
                        isClickable = true
                        setOnClickListener { selected = cat; refresh() }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(act, 4) })
                }
                list.removeAllViews()
                val filtered = items.filter { categoryFor(it, act) == selected }
                if (filtered.isEmpty()) {
                    list.addView(TextView(act).apply {
                        text = "没扫到这一类元素"
                        textSize = 12f
                        setTextColor(0xB3FFFFFF.toInt())
                        setPadding(0, dp(act, 10), 0, dp(act, 10))
                    })
                }
                for (e in filtered) {
                    val row = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(act, 10), dp(act, 10), dp(act, 10), dp(act, 10))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(act, 14).toFloat()
                            setColor(0x22FFFFFF.toInt())
                            setStroke(dp(act, 1), 0x33FFFFFF.toInt())
                        }
                    }
                    val cb = FrostedKit.iosSwitch(act, hidden.contains(e.token)) { checked ->
                        setHidden(act, e.token, checked)
                        apply(act.window?.decorView)
                        BottomBarAligner.align(act.window?.decorView)
                    }
                    val label = TextView(act).apply {
                        text = e.label + "\n" + e.token + " · ${e.w}×${e.h}"
                        textSize = 12f
                        setTextColor(Color.WHITE)
                        setPadding(dp(act, 6), 0, 0, 0)
                    }
                    row.addView(cb)
                    row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    list.addView(row)
                    list.addView(View(act).apply { layoutParams = LinearLayout.LayoutParams(1, dp(act, 6)) })
                }
            }
            scanBtn.setOnClickListener { refresh() }
            refresh()
            box.addView(TextView(act).apply {
                text = "关闭"
                textSize = 14f
                setTextColor(0xFFFF6A00.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(act, 12), 0, 0)
                setOnClickListener { d.dismiss() }
            })
            d.setContentView(box)
            d.setOnDismissListener { panel = null }
            panel = d
            d.show()
        } catch (t: Throwable) {
            Logger.d("kira scan result miss: ${t.message}")
        }
    }

    private fun presetsFor(cat: String): List<String> = when (cat) {
        "顶栏" -> listOf("分类", "侧边栏", "王者送福利", "游戏", "玩游戏")
        "底栏" -> listOf("首页", "发现", "拍摄", "消息", "我", "同城", "精选")
        "视频控件" -> listOf("头像", "点赞", "评论", "收藏", "分享", "音乐")
        "分享面板" -> listOf("保存到相册", "下载", "分享到微信", "朋友圈", "QQ", "微博", "复制链接", "举报", "不感兴趣")
        else -> emptyList()
    }

    private fun presetToken(label: String): String = "text:$label"

    private fun categoryFor(e: Elem, act: Activity): String {
        val s = (e.token + " " + e.label).lowercase()
        val h = act.resources.displayMetrics.heightPixels
        if (s.contains("share") || s.contains("分享")) return "分享面板"
        if (s.contains("bottom") || s.contains("底栏") || e.y > h * 0.7) return "底栏"
        if (s.contains("top") || s.contains("顶栏") || e.y < h * 0.2) return "顶栏"
        return "视频控件"
    }

    private fun scan(root: View): List<Elem> {
        val out = ArrayList<Elem>()
        val rootArea = root.width.coerceAtLeast(1) * root.height.coerceAtLeast(1)
        val seenToken = HashSet<String>()
        val seenRect = HashSet<String>()
        collect(root, rootArea, out, seenToken, seenRect)
        out.sortByDescending { it.w * it.h }
        return out.take(200)
    }

    private fun collect(v: View, rootArea: Int, out: MutableList<Elem>, seenToken: MutableSet<String>, seenRect: MutableSet<String>) {
        if (v.tag == TAG_OVERLAY) return
        val w = v.width
        val h = v.height
        if (v.isShown && w > 0 && h > 0 && w * h * 100 <= rootArea * 35) {
            val label = labelFor(v)
            val token = tokenFor(v)
            if (label.isNotEmpty() && token.isNotEmpty()) {
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                val rect = "${loc[0] / 8},${loc[1] / 8},${w / 8},${h / 8}"
                if (seenToken.add(token) && seenRect.add(rect)) {
                    out.add(Elem(token, label, loc[0], loc[1], w, h))
                }
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) collect(v.getChildAt(i), rootArea, out, seenToken, seenRect)
        }
    }

    private fun tokenFor(v: View): String {
        val id = v.id
        if (id != 0 && id != View.NO_ID) {
            try {
                val n = v.resources.getResourceEntryName(id)
                if (n.isNotBlank()) return "id:$n"
            } catch (_: Throwable) {}
        }
        val desc = v.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty() && desc.length <= 40) return "desc:$desc"
        if (v is TextView) {
            val t = v.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty() && t.length <= 30) return "text:$t"
        }
        return "cls:" + v.javaClass.name
    }

    private fun labelFor(v: View): String {
        if (v is TextView) {
            val t = v.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty() && t.length <= 12) return t
        }
        val d = v.contentDescription?.toString()?.trim().orEmpty()
        if (d.isNotEmpty() && d.length <= 12) return d
        val id = v.id
        if (id != 0 && id != View.NO_ID) {
            try {
                val n = v.resources.getResourceEntryName(id)
                if (n.isNotBlank()) return n
            } catch (_: Throwable) {}
        }
        return v.javaClass.simpleName
    }

    private fun hiddenSet(): Set<String> =
        Prefs.str(Prefs.K_BEAUTY_HIDDEN_ELEMS, "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun setHidden(ctx: android.content.Context, token: String, hide: Boolean) {
        val set = hiddenSet().toMutableSet()
        if (hide) set.add(token) else set.remove(token)
        Prefs.setStrSync(ctx, Prefs.K_BEAUTY_HIDDEN_ELEMS, set.joinToString(","))
    }

    private fun dp(ctx: android.content.Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
