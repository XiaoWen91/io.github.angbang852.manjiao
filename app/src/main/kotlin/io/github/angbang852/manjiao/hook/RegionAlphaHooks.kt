package io.github.angbang852.manjiao.hook

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.github.angbang852.manjiao.adapt.PlaybackState
import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.ui.FrostedKit
import io.github.angbang852.manjiao.ui.KiraSlider
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

object RegionAlphaHooks {
    private val applied = java.util.WeakHashMap<View, Float>()
    private var lastRoot: java.lang.ref.WeakReference<View>? = null
    private var listenerRegistered = false

    fun hook(module: XposedModule, cl: ClassLoader) {
        if (!listenerRegistered) {
            listenerRegistered = true
            PlaybackState.addListener { reapplyLast() }
        }
        for (name in arrayOf("com.yxcorp.gifshow.detail.PhotoDetailActivity", "com.yxcorp.gifshow.HomeActivity")) {
            try {
                val c = Class.forName(name, false, cl)
                val m = c.declaredMethods.firstOrNull { it.name == "onResume" && it.parameterCount == 0 } ?: continue
                module.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).setId("kira.region.alpha.$name").intercept { chain ->
                    val r = chain.proceed()
                    try {
                        val a = chain.thisObject as? Activity
                        if (a != null) {
                            apply(a.window?.decorView)
                            a.window?.decorView?.postDelayed({ apply(a.window?.decorView) }, 200L)
                            a.window?.decorView?.postDelayed({ apply(a.window?.decorView) }, 800L)
                            a.window?.decorView?.postDelayed({ apply(a.window?.decorView) }, 1500L)
                            a.window?.decorView?.postDelayed({ apply(a.window?.decorView) }, 2500L)
                        }
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
                try {
                    val root = (chain.thisObject as? View)?.rootView
                    apply(root)
                    root?.postDelayed({ apply(root) }, 150L)
                    root?.postDelayed({ apply(root) }, 500L)
                } catch (_: Throwable) {}
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
            val (d, box) = FrostedKit.dialog(act)
            d.window?.setLayout(
                (act.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                (act.resources.displayMetrics.heightPixels * 0.75f).toInt()
            )
            box.layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            box.addView(TextView(act).apply {
                text = "区域透明度"
                textSize = 17f
                setTextColor(Color.WHITE)
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
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(act, 6), 0, 0)
                }
                fun syncLabel(p: Int) { label.text = "$title  ${p}%" }
                val cur = ((Prefs.str(key, "1.00").toFloatOrNull() ?: 1f) * 100).toInt().coerceIn(0, 100)
                syncLabel(cur)
                val sb = KiraSlider(act).apply {
                    value = cur
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(act, 44)
                    )
                    onChanged = { p ->
                        syncLabel(p)
                        val v = p / 100f
                        val text = String.format(java.util.Locale.US, "%.2f", v)
                        Prefs.setStrSync(act, key, text)
                        previewAlpha(key, v, act.window?.decorView)
                        try {
                            val i = android.content.Intent(ActivityBridge.ACTION_SET_STR)
                            i.setPackage("com.smile.gifmaker")
                            i.putExtra("key", key)
                            i.putExtra("value", text)
                            act.sendBroadcast(i)
                        } catch (_: Throwable) {
                        }
                    }
                }
                box.addView(label)
                box.addView(sb)
            }
            val pauseRow = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(act, 8), 0, 0)
            }
            pauseRow.addView(TextView(act).apply {
                text = "暂停时恢复默认透明度"
                textSize = 13f
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            pauseRow.addView(FrostedKit.iosSwitch(act, Prefs.bool(Prefs.K_BEAUTY_ALPHA_PAUSE_RESET, false)) { checked ->
                Prefs.setBoolSync(act, Prefs.K_BEAUTY_ALPHA_PAUSE_RESET, checked)
                try {
                    val i = android.content.Intent(ActivityBridge.ACTION_SET_BOOL)
                    i.setPackage("com.smile.gifmaker")
                    i.putExtra("key", Prefs.K_BEAUTY_ALPHA_PAUSE_RESET)
                    i.putExtra("value", checked)
                    act.sendBroadcast(i)
                } catch (_: Throwable) {
                }
                apply(act.window?.decorView)
                reapplyLast()
            })
            box.addView(pauseRow)
            box.addView(TextView(act).apply {
                text = "关闭"
                textSize = 14f
                setTextColor(0xFF0A84FF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(act, 12), 0, 0)
                setOnClickListener { d.dismiss() }
            })
            d.setOnDismissListener { panel = null }
            panel = d
            d.show()
        } catch (t: Throwable) {
            Logger.d("kira region alpha panel miss: ${t.message}")
        }
    }

    @Volatile private var liveKey: String? = null
    @Volatile private var liveValue: Float = 1f

    private fun previewAlpha(key: String, v: Float, root: View?) {
        liveKey = key
        liveValue = v
        try {
            apply(root)
        } finally {
            liveKey = null
        }
    }

    fun reapplyLast() {
        lastRoot?.get()?.let { apply(it) }
    }

    fun apply(root: View?) {
        if (root == null) return
        if (isCommentOrMessageShown(root)) return
        if (!hasVideoMarker(root)) return
        lastRoot = java.lang.ref.WeakReference(root)
        try {
            applyKnown(root)
        } catch (t: Throwable) {
            Logger.d("kira region alpha apply miss: ${t.message}")
        }
    }

    private fun isPhotoDetail(root: View): Boolean {
        var c: android.content.Context? = root.context
        while (c is android.content.ContextWrapper) {
            if (c is android.app.Activity) break
            c = c.baseContext
        }
        val n = (c as? android.app.Activity)?.javaClass?.name.orEmpty()
        return n.contains("PhotoDetailActivity")
    }

    private fun hasVideoMarker(root: View): Boolean {
        if (findByName(root, "right_action_group") != null) return true
        if (findByName(root, "group_right_action_bar_root_layout") != null) return true
        if (findByName(root, "bottom_bar_shoot") != null) return true
        if (findByName(root, "caption_container") != null) return true
        if (findByName(root, "nasa_player_seekbar") != null) return true
        if (findByName(root, "photo_detail_placeholder_lottie") != null) return true
        if (findByName(root, "slide_play_like_image") != null) return true
        if (findByName(root, "like_button") != null) return true
        if (findByName(root, "comment_button") != null) return true
        if (findByName(root, "collect_button") != null) return true
        if (findByClass(root, "CaptionTextView") != null) return true
        if (findByClass(root, "UserNameLinearLayout") != null) return true
        if (findByClass(root, "BottomBarShootView") != null) return true
        return false
    }

    private fun findByClass(v: View, name: String): View? {
        if (v.javaClass.name.contains(name)) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) findByClass(v.getChildAt(i), name)?.let { return it }
        }
        return null
    }

    private fun isCommentOrMessageShown(root: View): Boolean {
        val c = findByName(root, "comment_container") ?: findByName(root, "comment_editor_container")
        if (c != null && c.isShown && c.width > 0 && c.height > 0) return true
        return false
    }

    private fun applyKnown(root: View): MutableSet<View> {
        val appliedNow = HashSet<View>()
        val playing = PlaybackState.isPlaying()
        val reset = Prefs.bool(Prefs.K_BEAUTY_ALPHA_PAUSE_RESET, false)

        fun setOne(v: View, key: String) {
            val a = if (!playing && reset) 1f else alpha(key)
            if (a < 0.999f) {
                applied[v] = a
                v.alpha = a
            } else if (applied.remove(v) != null) {
                v.alpha = 1f
            }
            appliedNow.add(v)
        }

        fun applyFirst(names: List<String>, key: String) {
            for (name in names) {
                val v = findByName(root, name)
                if (v != null) {
                    setOne(v, key)
                    break
                }
            }
        }

        applyFirst(listOf("bottom_bar_container", "bottom_bar", "bottom_bar_shoot"), Prefs.K_BEAUTY_ALPHA_BOTTOM)
        applyFirst(listOf("right_action_group", "group_right_action_bar_root_layout", "right_action_bar"), Prefs.K_BEAUTY_ALPHA_RIGHT)
        applyFirst(listOf("caption_container"), Prefs.K_BEAUTY_ALPHA_TEXT)
        applyFirst(listOf("top_bar_container", "alpha_top_bar", "title_root"), Prefs.K_BEAUTY_ALPHA_TOP)
        return appliedNow
    }

    private fun findByName(v: View, name: String): View? {
        if (resName(v) == name) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) findByName(v.getChildAt(i), name)?.let { return it }
        }
        return null
    }

    private fun walk(v: View, playing: Boolean, reset: Boolean, skip: Set<View>) {
        if (skip.contains(v)) return
        if (v.tag == "kira_scan_overlay") return
        val root = v.rootView
        if (root != null && root.width > 0 && root.height > 0 &&
            v.width >= root.width * 0.95f && v.height >= root.height * 0.95f) return
        val key = regionKey(v)
        if (key.isNotEmpty()) {
            val a = if (!playing && reset) 1f else alpha(key)
            if (a < 0.999f) {
                applied[v] = a
                v.alpha = a
            } else if (applied.remove(v) != null) {
                v.alpha = 1f
            }
            return
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), playing, reset, skip)
        }
    }

    private fun regionKey(v: View): String {
        val cls = v.javaClass.name.lowercase()
        val id = resName(v)?.lowercase().orEmpty()
        if (id.contains("comment") || id.contains("message") || cls.contains("comment") || cls.contains("message")) return ""
        if (cls.contains("captiontextview") || cls.contains("usernamelinearlayout") || id.contains("caption") || id.contains("username")) return Prefs.K_BEAUTY_ALPHA_TEXT
        if (id == "bottom_bar_container" || id == "bottom_bar" || id == "bottom_bar_shoot") return Prefs.K_BEAUTY_ALPHA_BOTTOM
        if (id == "right_action_group" || id == "group_right_action_bar_root_layout" || id == "right_action_bar") return Prefs.K_BEAUTY_ALPHA_RIGHT
        if (id == "top_bar_container" || id == "alpha_top_bar" || id == "title_root") return Prefs.K_BEAUTY_ALPHA_TOP
        return ""
    }

    private fun alpha(key: String): Float {
        if (key == liveKey) return liveValue
        return Prefs.str(key, "1.00").toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
    }

    private fun styleSeekBar(ctx: android.content.Context, sb: SeekBar) {
        try {
            val track = GradientDrawable().apply {
                cornerRadius = dp(ctx, 4).toFloat()
                setColor(0x33FFFFFF.toInt())
            }
            val progress = GradientDrawable().apply {
                cornerRadius = dp(ctx, 4).toFloat()
                setColor(0xFF0A84FF.toInt())
            }
            val clip = ClipDrawable(progress, Gravity.LEFT, ClipDrawable.HORIZONTAL)
            val layer = LayerDrawable(arrayOf(track, clip))
            layer.setId(0, android.R.id.background)
            layer.setId(1, android.R.id.progress)
            sb.progressDrawable = layer
            sb.thumb = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(ctx, 2), 0xFF0A84FF.toInt())
            }
            sb.thumbOffset = dp(ctx, 8)
            sb.setSplitTrack(false)
        } catch (_: Throwable) {
        }
    }

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
