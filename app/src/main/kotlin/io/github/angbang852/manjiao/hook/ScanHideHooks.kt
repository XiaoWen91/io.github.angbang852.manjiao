package io.github.angbang852.manjiao.hook

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.angbang852.manjiao.data.Prefs
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

    fun apply(root: View?) {
        if (root == null) return
        val hidden = hiddenSet()
        try {
            walkApply(root, hidden)
        } catch (t: Throwable) {
            Logger.d("kira scan apply miss: ${t.message}")
        }
    }

    private fun walkApply(v: View, hidden: Set<String>) {
        if (v.tag == TAG_OVERLAY) return
        val token = tokenFor(v)
        if (token.isNotEmpty()) {
            if (hidden.contains(token)) {
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
            val root = act.window?.decorView ?: return
            val d = Dialog(act)
            val box = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(act, 20).toFloat()
                    setColor(0xFFF8FAFC.toInt())
                    setStroke(dp(act, 1), 0xFFE2E8F0.toInt())
                }
            }
            val header = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(act).apply {
                text = "隐藏 Tab / 视频控件"
                textSize = 17f
                setTextColor(0xFF0F172A.toInt())
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val transBtn = TextView(act).apply {
                text = "透明度"
                textSize = 13f
                setTextColor(0xFF2563EB.toInt())
                setPadding(dp(act, 12), dp(act, 6), dp(act, 12), dp(act, 6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(act, 10).toFloat()
                    setColor(0xFFF1F5F9.toInt())
                    setStroke(dp(act, 1), 0xFFE2E8F0.toInt())
                }
                isClickable = true
                setOnClickListener { RegionAlphaHooks.showPanel(act) }
            }
            header.addView(transBtn)
            val scanBtn = TextView(act).apply {
                text = "⟳ 扫描"
                textSize = 13f
                setTextColor(0xFFFF6A00.toInt())
                setPadding(dp(act, 12), dp(act, 6), dp(act, 12), dp(act, 6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(act, 10).toFloat()
                    setColor(0xFFFFF7ED.toInt())
                    setStroke(dp(act, 1), 0xFFFFD7B5.toInt())
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
                        setTextColor(if (cat == selected) 0xFFFF6A00.toInt() else 0xFF64748B.toInt())
                        background = GradientDrawable().apply {
                            cornerRadius = dp(act, 12).toFloat()
                            setColor(if (cat == selected) 0xFFFFE8D6.toInt() else 0x00FFFFFF)
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
                        setTextColor(0xFF64748B.toInt())
                        setPadding(0, dp(act, 10), 0, dp(act, 10))
                    })
                }
                for (e in filtered) {
                    val row = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(act, 8), dp(act, 8), dp(act, 8), dp(act, 8))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(act, 14).toFloat()
                            setColor(Color.WHITE)
                            setStroke(dp(act, 1), 0xFFE2E8F0.toInt())
                        }
                    }
                    val cb = CheckBox(act).apply { isChecked = hidden.contains(e.token) }
                    val label = TextView(act).apply {
                        text = e.label + "\n" + e.token + " · ${e.w}×${e.h}"
                        textSize = 12f
                        setTextColor(0xFF334155.toInt())
                        setPadding(dp(act, 6), 0, 0, 0)
                    }
                    cb.setOnCheckedChangeListener { _, checked ->
                        setHidden(act, e.token, checked)
                        apply(act.window?.decorView)
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
            Logger.d("kira scan panel miss: ${t.message}")
        }
    }

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
