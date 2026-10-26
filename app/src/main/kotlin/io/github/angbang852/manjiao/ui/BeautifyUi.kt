package io.github.angbang852.manjiao.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.hook.BeautifyHooks

/**
 * 美化页：可点击行 + 自绘暗色弹窗 + 原模块同款 iOS 开关。
 */
object BeautifyUi {
    private val BOTTOM_ITEMS = listOf("首页", "发现", "拍摄", "消息", "我", "同城", "精选")
    private val RIGHT_COMPONENTS = listOf(
        "头像" to "avatar",
        "点赞" to "like",
        "评论" to "comment",
        "收藏" to "collect",
        "分享" to "share",
        "音乐" to "music"
    )

    fun build(ctx: Context): LinearLayout {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 8), 0, dp(ctx, 8), dp(ctx, 8))
        }

        root.addView(section(ctx, "全局"))
        root.addView(row(ctx, "全局 DPI", value(ctx, Prefs.K_BEAUTY_DPI, "默认")) { dpiDialog(ctx) })

        root.addView(section(ctx, "底栏"))
        root.addView(row(ctx, "隐藏组件", value(ctx, Prefs.K_BEAUTY_BOTTOM_HIDE, "未设置")) {
            checklist(ctx, "底栏隐藏组件", Prefs.K_BEAUTY_BOTTOM_HIDE, BOTTOM_ITEMS)
        })
        root.addView(row(ctx, "替换文字", value(ctx, Prefs.K_BEAUTY_BOTTOM_TEXT, "未设置")) {
            replaceDialog(ctx, "底栏替换文字", Prefs.K_BEAUTY_BOTTOM_TEXT, BOTTOM_ITEMS)
        })
        root.addView(toggleRow(ctx, "底栏美化（加号放右，其他放左）", Prefs.K_BEAUTY_BOTTOM_STYLE, false))
        root.addView(toggleRow(ctx, "底栏透明", Prefs.K_BEAUTY_BOTTOM_TRANSPARENT, false))

        root.addView(section(ctx, "视频页右侧"))
        root.addView(row(ctx, "隐藏组件", value(ctx, Prefs.K_BEAUTY_RIGHT_HIDE, "未设置")) {
            checklist(ctx, "右侧隐藏组件", Prefs.K_BEAUTY_RIGHT_HIDE, RIGHT_COMPONENTS.map { it.first })
        })
        root.addView(row(ctx, "修改偏移 / 缩放", "整体") { rightOffsetDialog(ctx) })

        root.addView(section(ctx, "文案信息"))
        root.addView(toggleRow(ctx, "实验性：官方时间/IP（create_date_tv）", Prefs.K_BEAUTY_OFFICIAL_DATE, false))

        root.addView(section(ctx, "操作确认"))
        root.addView(toggleRow(ctx, "点赞前确认", Prefs.K_CONFIRM_LIKE, false))
        root.addView(toggleRow(ctx, "收藏前确认", Prefs.K_CONFIRM_COLLECT, false))
        root.addView(toggleRow(ctx, "关注前确认", Prefs.K_CONFIRM_FOLLOW, false))

        root.addView(section(ctx, "播放 UI"))
        root.addView(row(ctx, "播放/暂停 UI 透明度", alphaSummary(ctx)) { alphaDialog(ctx) })
        root.addView(toggleRow(ctx, "去除全屏观看按钮", Prefs.K_BEAUTY_NO_FULLSCREEN, false))
        root.addView(toggleRow(ctx, "去除金币/红包按钮", Prefs.K_BEAUTY_NO_COIN, false))

        return root
    }

    private fun section(ctx: Context, title: String): TextView =
        TextView(ctx).apply {
            text = title
            setTextColor(0x80FFFFFF.toInt())
            textSize = 12f
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 4))
        }

    private fun row(ctx: Context, title: String, value: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            isClickable = true
            isFocusable = true
            background = rippleBg(ctx)
        }
        row.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = value
            setTextColor(0x99FFFFFF.toInt())
            textSize = 12f
            maxLines = 1
            maxWidth = dp(ctx, 140)
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(ctx).apply {
            text = "›"
            setTextColor(0x80FFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24))
        })
        row.setOnClickListener { onClick() }
        return row
    }

    private fun toggleRow(ctx: Context, title: String, key: String, def: Boolean): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
            isClickable = true
            isFocusable = true
        }
        row.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val sw = iosSwitch(ctx, Prefs.bool(key, def)) { on ->
            Prefs.setBoolSync(ctx, key, on)
            BeautifyHooks.reapply()
        }
        row.addView(sw, LinearLayout.LayoutParams(dp(ctx, 52), dp(ctx, 30)))
        row.setOnClickListener { sw.performClick() }
        return row
    }

    private fun iosSwitch(ctx: Context, checked: Boolean, onToggle: (Boolean) -> Unit): View {
        val trackW = dp(ctx, 52)
        val trackH = dp(ctx, 30)
        val knobSize = dp(ctx, 24)
        val pad = (trackH - knobSize) / 2
        val colorOn = 0xCC4CAF50.toInt()
        val colorOff = 0xCC757575.toInt()
        val container = FrameLayout(ctx)
        val trackBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = trackH / 2f
            setColor(if (checked) colorOn else colorOff)
        }
        container.addView(View(ctx).apply { background = trackBg }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val knob = View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            elevation = dp(ctx, 3).toFloat()
        }
        val lp = FrameLayout.LayoutParams(knobSize, knobSize, Gravity.CENTER_VERTICAL)
        lp.leftMargin = if (checked) trackW - knobSize - pad else pad
        container.addView(knob, lp)
        var on = checked
        container.setOnClickListener {
            on = !on
            trackBg.setColor(if (on) colorOn else colorOff)
            lp.leftMargin = if (on) trackW - knobSize - pad else pad
            knob.layoutParams = lp
            try {
                onToggle(on)
            } catch (t: Throwable) {
                android.util.Log.e("BeautifyUi", "switch action failed", t)
            }
        }
        return container
    }

    private fun checklist(ctx: Context, title: String, key: String, items: List<String>) {
        val selected = Prefs.str(key, "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4)) }
        val boxes = ArrayList<CheckBox>()
        for (item in items) {
            val cb = CheckBox(ctx).apply {
                text = item
                setTextColor(Color.WHITE)
                isChecked = selected.contains(item)
            }
            boxes.add(cb)
            col.addView(cb)
        }
        showDialog(ctx, title, ScrollView(ctx).apply { addView(col) }, "保存", {
            val out = boxes.filter { it.isChecked }.map { it.text.toString() }
            Prefs.setStrSync(ctx, key, out.joinToString(","))
            BeautifyHooks.reapply()
        })
    }

    private fun replaceDialog(ctx: Context, title: String, key: String, items: List<String>) {
        val map = HashMap<String, String>()
        Prefs.str(key, "").split(",").forEach { part ->
            val p = part.split("|")
            if (p.size == 2) map[p[0].trim()] = p[1]
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4)) }
        val edits = ArrayList<Pair<String, EditText>>()
        for (item in items) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(ctx, 6), 0, dp(ctx, 6)) }
            row.addView(TextView(ctx).apply {
                text = item
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 72), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            val et = styledEdit(ctx, map[item] ?: "", "留空不改")
            edits.add(item to et)
            row.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            col.addView(row)
        }
        showDialog(ctx, title, ScrollView(ctx).apply { addView(col) }, "保存", {
            val out = ArrayList<String>()
            for ((item, et) in edits) {
                val v = et.text?.toString()?.trim().orEmpty()
                if (v.isNotEmpty()) out.add("$item|$v")
            }
            Prefs.setStrSync(ctx, key, out.joinToString(","))
            BeautifyHooks.reapply()
        })
    }

    private fun dpiDialog(ctx: Context) {
        val current = Prefs.str(Prefs.K_BEAUTY_DPI, "").toIntOrNull() ?: 0
        val value = if (current in 320..640) current else 420
        val bar = SeekBar(ctx).apply { max = 320; progress = value - 320 }
        val label = TextView(ctx).apply { text = "$value dpi"; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { label.text = "${progress + 320} dpi" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4)); addView(label); addView(bar) }
        showDialog(ctx, "全局 DPI", col, "保存", {
            Prefs.setStrSync(ctx, Prefs.K_BEAUTY_DPI, (bar.progress + 320).toString())
            BeautifyHooks.reapply()
        }, neutralText = "恢复默认", onNeutral = {
            Prefs.setStrSync(ctx, Prefs.K_BEAUTY_DPI, "")
            BeautifyHooks.reapply()
        })
    }

    private fun rightOffsetDialog(ctx: Context) {
        val x = Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_X, "").toIntOrNull() ?: 0
        val y = Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_Y, "").toIntOrNull() ?: 0
        val s = (Prefs.str(Prefs.K_BEAUTY_RIGHT_SCALE, "").toFloatOrNull() ?: 1f).times(100).toInt().coerceIn(50, 150)
        val (xRow, xBar) = seekIntRow(ctx, "X 偏移", -50, 50, x)
        val (yRow, yBar) = seekIntRow(ctx, "Y 偏移", -50, 50, y)
        val (sRow, sBar) = seekIntRow(ctx, "缩放 (x0.01)", 50, 150, s)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4)); addView(xRow); addView(yRow); addView(sRow) }
        showDialog(ctx, "右侧整体偏移 / 缩放", col, "保存", {
            Prefs.setStrSync(ctx, Prefs.K_BEAUTY_RIGHT_OFFSET_X, (xBar.progress - 50).toString())
            Prefs.setStrSync(ctx, Prefs.K_BEAUTY_RIGHT_OFFSET_Y, (yBar.progress - 50).toString())
            Prefs.setStrSync(ctx, Prefs.K_BEAUTY_RIGHT_SCALE, String.format(java.util.Locale.US, "%.2f", sBar.progress / 100f))
            BeautifyHooks.reapply()
        })
    }

    private fun alphaDialog(ctx: Context) {
        val specs = listOf(
            Triple("播放时", Prefs.K_BEAUTY_ALPHA_PLAYING, 100),
            Triple("暂停时", Prefs.K_BEAUTY_ALPHA_PAUSED, 100)
        )
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4)) }
        val bars = ArrayList<Pair<String, SeekBar>>()
        for ((name, key, def) in specs) {
            val cur = (Prefs.str(key, "").toFloatOrNull()?.times(100)?.toInt() ?: def).coerceIn(0, 100)
            val (row, bar) = seekIntRow(ctx, name, 0, 100, cur)
            bars.add(key to bar)
            col.addView(row)
        }
        showDialog(ctx, "播放/暂停 UI 透明度", col, "保存", {
            for ((key, bar) in bars) Prefs.setStrSync(ctx, key, String.format("%.2f", bar.progress / 100f))
            BeautifyHooks.reapply()
        })
    }

    private fun seekIntRow(ctx: Context, name: String, min: Int, max: Int, current: Int): Pair<LinearLayout, SeekBar> {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(ctx, 8), 0, dp(ctx, 8)) }
        val label = TextView(ctx).apply { text = "$name: $current"; setTextColor(Color.WHITE); textSize = 14f }
        val bar = SeekBar(ctx).apply { this.max = max - min; progress = (current - min).coerceIn(0, max - min) }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { label.text = "$name: ${progress + min}" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        row.addView(label)
        row.addView(bar)
        return row to bar
    }

    private fun alphaSummary(ctx: Context): String {
        val playing = Prefs.str(Prefs.K_BEAUTY_ALPHA_PLAYING, "").ifBlank { "1.00" }
        val paused = Prefs.str(Prefs.K_BEAUTY_ALPHA_PAUSED, "").ifBlank { "1.00" }
        return "$playing/$paused"
    }

    private fun styledEdit(ctx: Context, text: String, hintText: String): EditText =
        EditText(ctx).apply {
            setText(text)
            hint = hintText
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0x66FFFFFF)
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 8).toFloat()
                setColor(0x22FFFFFF)
                setStroke(dp(ctx, 1), 0x33FFFFFF)
            }
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
        }

    private fun showDialog(
        ctx: Context,
        title: String,
        content: View,
        positiveText: String?,
        onPositive: (() -> Unit)?,
        neutralText: String? = null,
        onNeutral: (() -> Unit)? = null,
        negativeText: String? = "取消"
    ): Dialog {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 18), dp(ctx, 20), dp(ctx, 12))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 16).toFloat()
                setColor(0xF21E1E1E.toInt())
            }
        }
        root.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(ctx, 8), 0, 0) })
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(ctx, 12), 0, 0)
        }
        fun addBtn(text: String, color: Int, onClick: () -> Unit) {
            btnRow.addView(TextView(ctx).apply {
                this.text = text
                setTextColor(color)
                textSize = 14f
                setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    try {
                        onClick()
                    } catch (t: Throwable) {
                        android.util.Log.e("BeautifyUi", "dialog action failed", t)
                    }
                }
            })
        }
        neutralText?.takeIf { it.isNotEmpty() }?.let { addBtn(it, 0xB3FFFFFF.toInt()) { onNeutral?.invoke() } }
        negativeText?.takeIf { it.isNotEmpty() }?.let { addBtn(it, 0xB3FFFFFF.toInt()) { } }
        positiveText?.takeIf { it.isNotEmpty() }?.let { addBtn(it, 0xFFFF6600.toInt()) { onPositive?.invoke() } }
        root.addView(btnRow)
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.show()
        return dialog
    }

    private fun value(ctx: Context, key: String, fallback: String): String {
        val v = Prefs.str(key, "")
        return if (v.isBlank()) fallback else v
    }

    private fun rippleBg(ctx: Context): GradientDrawable =
        GradientDrawable().apply { cornerRadius = dp(ctx, 12).toFloat(); setColor(0x0FFFFFFF) }

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
}
