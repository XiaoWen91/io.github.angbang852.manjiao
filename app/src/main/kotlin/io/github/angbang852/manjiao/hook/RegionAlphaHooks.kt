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
import android.widget.SeekBar
import android.widget.TextView
import io.github.angbang852.manjiao.adapt.PlaybackState
import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

object RegionAlphaHooks {
    private val applied = java.util.WeakHashMap<View, Float>()

    fun hook(module: XposedModule, cl: ClassLoader) {
        for (name in arrayOf("com.yxcorp.gifshow.detail.PhotoDetailActivity", "com.yxcorp.gifshow.HomeActivity")) {
            try {
                val c = Class.forName(name, false, cl)
                val m = c.declaredMethods.firstOrNull { it.name == "onResume" && it.parameterCount == 0 } ?: continue
                module.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).setId("kira.region.alpha.$name").intercept { chain ->
                    val r = chain.proceed()
                    try {
                        val a = chain.thisObject as? Activity
                        if (a != null) apply(a.window?.decorView)
                    } catch (_: Throwable) {}
                    r
                }
            } catch (t: Throwable) {
                Logger.d("kira region alpha hook miss $name: ${t.message}")
            }
        }
        try {
            val c = Class.forName("com.kwai.slide.play.detail.common.information.caption.CaptionTextView", false, cl)
            val m = c.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            module.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).setId("kira.region.alpha.caption").intercept { chain ->
                val r = chain.proceed()
                try { apply((chain.thisObject as? View)?.rootView) } catch (_: Throwable) {}
                r
            }
        } catch (t: Throwable) {
            Logger.d("kira region alpha caption miss: ${t.message}")
        }
    }

    private var panel: Dialog? = null

    fun showPanel(act: Activity) {
        try {
            if (panel?.isShowing == true) return
            val d = Dialog(act)
            val box = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(act, 18), dp(act, 18), dp(act, 18), dp(act, 14))
                background = GradientDrawable().apply {
                    cornerRadius = dp(act, 20).toFloat()
                    setColor(0xFFF8FAFC.toInt())
                    setStroke(dp(act, 1), 0xFFE2E8F0.toInt())
                }
            }
            box.addView(TextView(act).apply {
                text = "区域透明度"
                textSize = 17f
                setTextColor(0xFF0F172A.toInt())
                setPadding(0, 0, 0, dp(act, 10))
            })
            val specs = listOf(
                "顶栏" to Prefs.K_BEAUTY_ALPHA_TOP,
                "右侧信息" to Prefs.K_BEAUTY_ALPHA_RIGHT,
                "左下角信息" to Prefs.K_BEAUTY_ALPHA_TEXT,
                "底栏" to Prefs.K_BEAUTY_ALPHA_BOTTOM
            )
            for ((title, key) in specs) {
                val label = TextView(act).apply {
                    textSize = 13f
                    setTextColor(0xFF334155.toInt())
                    setPadding(0, dp(act, 6), 0, 0)
                }
                fun syncLabel(p: Int) { label.text = "$title  ${p}%" }
                val cur = ((Prefs.str(key, "1.00").toFloatOrNull() ?: 1f) * 100).toInt().coerceIn(0, 100)
                syncLabel(cur)
                val sb = SeekBar(act).apply { max = 100; progress = cur }
                sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        syncLabel(p)
                        Prefs.setStr(key, String.format(java.util.Locale.US, "%.2f", p / 100f))
                        apply(act.window?.decorView)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
                box.addView(label)
                box.addView(sb)
            }
            val cb = CheckBox(act).apply {
                text = "暂停时恢复默认透明度"
                textSize = 13f
                setTextColor(0xFF334155.toInt())
                isChecked = Prefs.bool(Prefs.K_BEAUTY_ALPHA_PAUSE_RESET, false)
            }
            cb.setOnCheckedChangeListener { _, checked ->
                Prefs.setBool(Prefs.K_BEAUTY_ALPHA_PAUSE_RESET, checked)
                apply(act.window?.decorView)
            }
            box.addView(cb)
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
            Logger.d("kira region alpha panel miss: ${t.message}")
        }
    }

    fun apply(root: View?) {
        if (root == null) return
        try {
            walk(root, PlaybackState.isPlaying(), Prefs.bool(Prefs.K_BEAUTY_ALPHA_PAUSE_RESET, false))
        } catch (t: Throwable) {
            Logger.d("kira region alpha apply miss: ${t.message}")
        }
    }

    private fun walk(v: View, playing: Boolean, reset: Boolean) {
        if (v.tag == "kira_scan_overlay") return
        val key = regionKey(v)
        if (key.isNotEmpty()) {
            val a = if (!playing && reset) 1f else alpha(key)
            if (a < 0.999f) {
                applied[v] = a
                v.alpha = a
            } else if (applied.remove(v) != null) {
                v.alpha = 1f
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), playing, reset)
        }
    }

    private fun regionKey(v: View): String {
        val cls = v.javaClass.name.lowercase()
        val id = resName(v)?.lowercase().orEmpty()
        if (cls.contains("caption") || cls.contains("username") || id.contains("caption")) return Prefs.K_BEAUTY_ALPHA_TEXT
        if (cls.contains("bottom") || id.contains("bottom")) return Prefs.K_BEAUTY_ALPHA_BOTTOM
        if (cls.contains("right") || cls.contains("actionbar") || id.contains("right")) return Prefs.K_BEAUTY_ALPHA_RIGHT
        if (cls.contains("top") || cls.contains("tabstrip") || id.contains("top")) return Prefs.K_BEAUTY_ALPHA_TOP
        return ""
    }

    private fun alpha(key: String): Float =
        Prefs.str(key, "1.00").toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f

    private fun dp(ctx: android.content.Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()

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
