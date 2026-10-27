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
import io.github.angbang852.manjiao.util.Logger

object MainInjectedPanel {
    private var panel: Dialog? = null

    fun showPanel(act: Activity) {
        try {
            if (panel?.isShowing == true) return
            val d = Dialog(act)
            d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            d.window?.setDimAmount(0.35f)
            d.window?.setLayout((act.resources.displayMetrics.widthPixels * 0.92f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            d.window?.setGravity(android.view.Gravity.CENTER)
            val box = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(act, 20).toFloat()
                    setColor(0xFFF8FAFC.toInt())
                    setStroke(dp(act, 1), 0xFFE2E8F0.toInt())
                }
            }
            box.addView(TextView(act).apply {
                text = "快手助手"
                textSize = 22f
                setTextColor(0xFF0F172A.toInt())
            })
            box.addView(TextView(act).apply {
                text = "KiraTools"
                textSize = 12.5f
                setTextColor(0xFF64748B.toInt())
                setPadding(0, dp(act, 2), 0, dp(act, 10))
            })

            val content = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            content.addView(section(act, "功能开关", true, listOf(
                toggleRow(act, "点赞前确认", Prefs.K_CONFIRM_LIKE),
                toggleRow(act, "收藏前确认", Prefs.K_CONFIRM_COLLECT),
                toggleRow(act, "关注前确认", Prefs.K_CONFIRM_FOLLOW),
                toggleRow(act, "去除金币/红包按钮", Prefs.K_BEAUTY_NO_COIN),
                toggleRow(act, "去除全屏观看按钮", Prefs.K_BEAUTY_NO_FULLSCREEN),
                toggleRow(act, "官方时间/IP", Prefs.K_BEAUTY_OFFICIAL_DATE)
            )))
            content.addView(space(act))
            content.addView(section(act, "界面显示", true, listOf(
                actionRow(act, "隐藏 Tab / 视频控件", "选择") { ScanHideHooks.showPanel(act) },
                actionRow(act, "区域透明度", "设置") { RegionAlphaHooks.showPanel(act) },
                toggleRow(act, "全屏隐藏状态栏", Prefs.K_BEAUTY_HIDE_STATUS_BAR),
                toggleRow(act, "底栏自适应", Prefs.K_BEAUTY_BOTTOM_AUTO),
                toggleRow(act, "底栏透明", Prefs.K_BEAUTY_BOTTOM_TRANSPARENT)
            )))
            content.addView(space(act))
            content.addView(section(act, "视频功能", false, listOf(
                actionRow(act, "下载当前视频", "下载") { },
                actionRow(act, "分享面板隐藏项", "设置") { showShareHidePanel(act) }
            )))

            val scroll = ScrollView(act).apply { isVerticalScrollBarEnabled = false }
            scroll.addView(content)
            box.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(act, 520)))
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
            Logger.d("kira main panel miss: ${t.message}")
        }
    }

    private fun showShareHidePanel(act: Activity) {
        try {
            val d = Dialog(act)
            d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            d.window?.setDimAmount(0.35f)
            d.window?.setLayout((act.resources.displayMetrics.widthPixels * 0.92f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            d.window?.setGravity(android.view.Gravity.CENTER)
            val box = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(act, 20).toFloat()
                    setColor(0xFFF8FAFC.toInt())
                    setStroke(dp(act, 1), 0xFFE2E8F0.toInt())
                }
            }
            box.addView(TextView(act).apply {
                text = "分享面板隐藏项"
                textSize = 17f
                setTextColor(0xFF0F172A.toInt())
                setPadding(0, 0, 0, dp(act, 10))
            })
            val items = listOf("保存到相册", "下载", "分享到微信", "朋友圈", "QQ", "微博", "复制链接", "举报", "不感兴趣")
            val set = Prefs.str(Prefs.K_BEAUTY_SHARE_HIDE, "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            for (label in items) {
                val row = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(act, 12), dp(act, 8), dp(act, 12), dp(act, 8))
                }
                row.addView(TextView(act).apply {
                    text = label
                    textSize = 14f
                    setTextColor(0xFF334155.toInt())
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Switch(act).apply {
                    showText = false
                    isChecked = set.contains(label)
                    thumbTintList = ColorStateList.valueOf(0xFFFF6A00.toInt())
                    trackTintList = ColorStateList.valueOf(0xFFE2E8F0.toInt())
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) set.add(label) else set.remove(label)
                        Prefs.setStr(Prefs.K_BEAUTY_SHARE_HIDE, set.joinToString(","))
                        ShareItemHideHook.apply(act.window?.decorView)
                    }
                })
                box.addView(row)
            }
            box.addView(TextView(act).apply {
                text = "关闭"
                textSize = 14f
                setTextColor(0xFFFF6A00.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(act, 12), 0, 0)
                setOnClickListener { d.dismiss() }
            })
            d.setContentView(box)
            d.show()
        } catch (t: Throwable) {
            Logger.d("kira share hide panel miss: ${t.message}")
        }
    }

    private fun section(act: Activity, title: String, expanded: Boolean, rows: List<View>): View {
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(act, 18).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(act, 1), 0xFFE2E8F0.toInt())
            }
        }
        val header = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(act, 16), dp(act, 14), dp(act, 16), dp(act, 14))
            isClickable = true
        }
        header.addView(TextView(act).apply {
            text = title
            textSize = 15.5f
            setTextColor(0xFF0F172A.toInt())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val chevron = TextView(act).apply {
            text = if (expanded) "⌃" else "⌄"
            textSize = 14f
            setTextColor(0xFF64748B.toInt())
        }
        header.addView(chevron)
        val body = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (expanded) View.VISIBLE else View.GONE
        }
        for (r in rows) body.addView(r)
        header.setOnClickListener {
            val show = body.visibility != View.VISIBLE
            body.visibility = if (show) View.VISIBLE else View.GONE
            chevron.text = if (show) "⌃" else "⌄"
        }
        card.addView(header)
        card.addView(body)
        card.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return card
    }

    private fun toggleRow(act: Activity, title: String, key: String): View {
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(act, 16), dp(act, 12), dp(act, 16), dp(act, 12))
        }
        row.addView(TextView(act).apply {
            text = title
            textSize = 15f
            setTextColor(0xFF334155.toInt())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Switch(act).apply {
            showText = false
            isChecked = Prefs.bool(key, false)
            thumbTintList = ColorStateList.valueOf(0xFFFF6A00.toInt())
            trackTintList = ColorStateList.valueOf(0xFFE2E8F0.toInt())
            setOnCheckedChangeListener { _, checked -> Prefs.setBool(key, checked) }
        })
        return row
    }

    private fun actionRow(act: Activity, title: String, buttonText: String, onClick: () -> Unit): View {
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(act, 16), dp(act, 12), dp(act, 16), dp(act, 12))
        }
        row.addView(TextView(act).apply {
            text = title
            textSize = 15f
            setTextColor(0xFF334155.toInt())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(pillButton(act, buttonText, onClick))
        return row
    }

    private fun pillButton(act: Activity, text: String, onClick: () -> Unit): TextView =
        TextView(act).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF334155.toInt())
            setPadding(dp(act, 14), dp(act, 8), dp(act, 14), dp(act, 8))
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = dp(act, 10).toFloat()
                setColor(0xFFF1F5F9.toInt())
                setStroke(dp(act, 1), 0xFFE2E8F0.toInt())
            }
            setOnClickListener { onClick() }
        }

    private fun space(act: Activity): View =
        View(act).apply { layoutParams = LinearLayout.LayoutParams(1, dp(act, 12)) }

    private fun dp(ctx: android.content.Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
