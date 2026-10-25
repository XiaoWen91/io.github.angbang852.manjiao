package io.github.angbang852.manjiao.ui

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import io.github.angbang852.manjiao.data.Prefs

/**
 * 美化页：只负责界面和配置写入，具体 Hook 由后续美化 Hook 读取 Prefs。
 */
object BeautifyUi {
    fun build(ctx: Context): LinearLayout {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 8), 0, dp(ctx, 8), dp(ctx, 8))
        }

        root.addView(section(ctx, "全局"))
        root.addView(editRow(ctx, "全局 DPI", Prefs.K_BEAUTY_DPI, "留空不改，例如 420"))

        root.addView(section(ctx, "底栏"))
        root.addView(editRow(ctx, "隐藏组件", Prefs.K_BEAUTY_BOTTOM_HIDE, "首页,拍摄,消息,我"))
        root.addView(editRow(ctx, "替换文字", Prefs.K_BEAUTY_BOTTOM_TEXT, "首页|拍摄|消息|我"))
        root.addView(switchRow(ctx, "底栏美化（加号放右，其他放左）", Prefs.K_BEAUTY_BOTTOM_STYLE, false))
        root.addView(switchRow(ctx, "底栏透明", Prefs.K_BEAUTY_BOTTOM_TRANSPARENT, false))

        root.addView(section(ctx, "视频页右侧"))
        root.addView(editRow(ctx, "隐藏组件", Prefs.K_BEAUTY_RIGHT_HIDE, "点赞,收藏,评论,头像"))
        root.addView(editRow(ctx, "X 偏移", Prefs.K_BEAUTY_RIGHT_OFFSET_X, "例如 -8"))
        root.addView(editRow(ctx, "Y 偏移", Prefs.K_BEAUTY_RIGHT_OFFSET_Y, "例如 12"))
        root.addView(editRow(ctx, "缩放", Prefs.K_BEAUTY_RIGHT_SCALE, "例如 0.9"))

        root.addView(section(ctx, "播放 UI"))
        root.addView(editRow(ctx, "播放/暂停时 UI 透明度", Prefs.K_BEAUTY_PLAY_ALPHA, "0.0 - 1.0，例如 0.6"))
        root.addView(switchRow(ctx, "去除全屏观看按钮", Prefs.K_BEAUTY_NO_FULLSCREEN, false))
        root.addView(switchRow(ctx, "去除金币/红包按钮", Prefs.K_BEAUTY_NO_COIN, false))

        return root
    }

    private fun section(ctx: Context, title: String): TextView =
        TextView(ctx).apply {
            text = title
            setTextColor(0x80FFFFFF.toInt())
            textSize = 12f
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 4))
        }

    private fun switchRow(ctx: Context, title: String, key: String, def: Boolean): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
        }
        row.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(Switch(ctx).apply {
            isChecked = Prefs.bool(key, def)
            setOnCheckedChangeListener { _, checked -> Prefs.setBoolSync(ctx, key, checked) }
        })
        return row
    }

    private fun editRow(ctx: Context, title: String, key: String, hintText: String): LinearLayout {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
        }
        col.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
        })
        col.addView(EditText(ctx).apply {
            setText(Prefs.str(key, ""))
            hint = hintText
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0x66FFFFFF)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    Prefs.setStrSync(ctx, key, s?.toString()?.trim().orEmpty())
                }
            })
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(ctx, 4), 0, 0) })
        return col
    }

    private fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
