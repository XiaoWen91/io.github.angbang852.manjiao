package io.github.angbang852.manjiao.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import io.github.angbang852.manjiao.data.Prefs

/**
 * 美化页：统一使用可点击行 + 二级弹窗，不再把原生输入框/开关直接铺在页面上。
 */
object BeautifyUi {
    private val BOTTOM_ITEMS = listOf("首页", "发现", "拍摄", "消息", "我", "同城", "精选")
    private val RIGHT_ITEMS = listOf("点赞", "收藏", "评论", "分享", "头像", "音乐", "更多")

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
            checklist(ctx, "右侧隐藏组件", Prefs.K_BEAUTY_RIGHT_HIDE, RIGHT_ITEMS)
        })
        root.addView(row(ctx, "修改偏移 / 缩放", offsetSummary(ctx)) { offsetDialog(ctx) })

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
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 120), ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
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
        val pill = TextView(ctx).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6))
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 52), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        fun paint(on: Boolean) {
            pill.text = if (on) "开启" else "关闭"
            pill.setTextColor(if (on) Color.WHITE else 0xB3FFFFFF.toInt())
            pill.background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 14).toFloat()
                setColor(if (on) 0xFFFF6600.toInt() else 0x22FFFFFF)
            }
        }
        var on = Prefs.bool(key, def)
        paint(on)
        pill.setOnClickListener {
            on = !on
            Prefs.setBoolSync(ctx, key, on)
            paint(on)
        }
        row.addView(pill)
        row.setOnClickListener { pill.performClick() }
        return row
    }

    private fun checklist(ctx: Context, title: String, key: String, items: List<String>) {
        val selected = Prefs.str(key, "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8)) }
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
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(ScrollView(ctx).apply { addView(col) })
            .setPositiveButton("保存") { _, _ ->
                val out = boxes.filter { it.isChecked }.map { it.text.toString() }
                Prefs.setStrSync(ctx, key, out.joinToString(","))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun replaceDialog(ctx: Context, title: String, key: String, items: List<String>) {
        val map = HashMap<String, String>()
        Prefs.str(key, "").split(",").forEach { part ->
            val p = part.split("|")
            if (p.size == 2) map[p[0].trim()] = p[1]
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8)) }
        val edits = ArrayList<Pair<String, EditText>>()
        for (item in items) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(ctx, 6), 0, dp(ctx, 6)) }
            row.addView(TextView(ctx).apply {
                text = item
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 72), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            val et = EditText(ctx).apply {
                setText(map[item] ?: "")
                hint = "留空不改"
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(0x66FFFFFF)
            }
            edits.add(item to et)
            row.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            col.addView(row)
        }
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(ScrollView(ctx).apply { addView(col) })
            .setPositiveButton("保存") { _, _ ->
                val out = ArrayList<String>()
                for ((item, et) in edits) {
                    val v = et.text?.toString()?.trim().orEmpty()
                    if (v.isNotEmpty()) out.add("$item|$v")
                }
                Prefs.setStrSync(ctx, key, out.joinToString(","))
            }
            .setNegativeButton("取消", null)
            .show()
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
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), dp(ctx, 8)); addView(label); addView(bar) }
        AlertDialog.Builder(ctx)
            .setTitle("全局 DPI")
            .setView(col)
            .setPositiveButton("保存") { _, _ -> Prefs.setStrSync(ctx, Prefs.K_BEAUTY_DPI, (bar.progress + 320).toString()) }
            .setNeutralButton("恢复默认") { _, _ -> Prefs.setStrSync(ctx, Prefs.K_BEAUTY_DPI, "") }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun offsetDialog(ctx: Context) {
        val x = Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_X, "").toIntOrNull() ?: 0
        val y = Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_Y, "").toIntOrNull() ?: 0
        val s = (Prefs.str(Prefs.K_BEAUTY_RIGHT_SCALE, "").toFloatOrNull() ?: 1f).times(100).toInt().coerceIn(50, 150)
        val (xRow, xBar) = seekIntRow(ctx, "X 偏移", -50, 50, x)
        val (yRow, yBar) = seekIntRow(ctx, "Y 偏移", -50, 50, y)
        val (sRow, sBar) = seekIntRow(ctx, "缩放 (x0.01)", 50, 150, s)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), dp(ctx, 8)); addView(xRow); addView(yRow); addView(sRow) }
        AlertDialog.Builder(ctx)
            .setTitle("右侧偏移 / 缩放")
            .setView(col)
            .setPositiveButton("保存") { _, _ ->
                Prefs.setStrSync(ctx, Prefs.K_BEAUTY_RIGHT_OFFSET_X, (xBar.progress - 50).toString())
                Prefs.setStrSync(ctx, Prefs.K_BEAUTY_RIGHT_OFFSET_Y, (yBar.progress - 50).toString())
                Prefs.setStrSync(ctx, Prefs.K_BEAUTY_RIGHT_SCALE, String.format("%.2f", (sBar.progress) / 100f))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun alphaDialog(ctx: Context) {
        val specs = listOf(
            Triple("播放时", Prefs.K_BEAUTY_ALPHA_PLAYING, 100),
            Triple("暂停时", Prefs.K_BEAUTY_ALPHA_PAUSED, 100)
        )
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), dp(ctx, 8)) }
        val bars = ArrayList<Pair<String, SeekBar>>()
        for ((name, key, def) in specs) {
            val cur = (Prefs.str(key, "").toFloatOrNull()?.times(100)?.toInt() ?: def).coerceIn(0, 100)
            val (row, bar) = seekIntRow(ctx, name, 0, 100, cur)
            bars.add(key to bar)
            col.addView(row)
        }
        AlertDialog.Builder(ctx)
            .setTitle("播放/暂停 UI 透明度")
            .setView(col)
            .setPositiveButton("保存") { _, _ ->
                for ((key, bar) in bars) Prefs.setStrSync(ctx, key, String.format("%.2f", bar.progress / 100f))
            }
            .setNegativeButton("取消", null)
            .show()
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

    private fun offsetSummary(ctx: Context): String {
        val x = Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_X, "").ifBlank { "0" }
        val y = Prefs.str(Prefs.K_BEAUTY_RIGHT_OFFSET_Y, "").ifBlank { "0" }
        val s = Prefs.str(Prefs.K_BEAUTY_RIGHT_SCALE, "").ifBlank { "1.00" }
        return "$x / $y / $s"
    }

    private fun alphaSummary(ctx: Context): String {
        val playing = Prefs.str(Prefs.K_BEAUTY_ALPHA_PLAYING, "").ifBlank { "1.00" }
        val paused = Prefs.str(Prefs.K_BEAUTY_ALPHA_PAUSED, "").ifBlank { "1.00" }
        return "$playing/$paused"
    }

    private fun value(ctx: Context, key: String, fallback: String): String {
        val v = Prefs.str(key, "")
        return if (v.isBlank()) fallback else v
    }

    private fun rippleBg(ctx: Context): GradientDrawable =
        GradientDrawable().apply { cornerRadius = dp(ctx, 12).toFloat(); setColor(0x0FFFFFFF) }

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
}
