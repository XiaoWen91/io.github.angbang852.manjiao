package io.github.angbang852.manjiao

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.data.SyncService
import io.github.angbang852.manjiao.hook.AntiAntiHook
import io.github.angbang852.manjiao.hook.CfhState
import io.github.angbang852.manjiao.hook.ContentFilterHook
import io.github.angbang852.manjiao.hook.GestureHook
import io.github.angbang852.manjiao.hook.GoldFloatHook
import io.github.angbang852.manjiao.hook.ImmersiveHook
import io.github.angbang852.manjiao.hook.PerfHook
import io.github.angbang852.manjiao.hook.PlaybackHook
import io.github.angbang852.manjiao.hook.PurifyHook

import io.github.angbang852.manjiao.hook.SharePanelHook
import io.github.angbang852.manjiao.hook.VideoDownloaderHook
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicBoolean

class Module : XposedModule {
    private val init = AtomicBoolean(false)
    // ★ 性能优化-进程级一次初始化：快手插件化框架（nebula 等）会在主进程多个线程
    // 反复 new Application 并调 onCreate——不幂等的话 receiver 会重复注册十几遍、
    // 全部 hook 重复装载（ANR 根因之一，实测单次启动重复 13 次）
    private val onCreateDone = AtomicBoolean(false)

    constructor() { }

    // ★ libxposed 的 ModuleLoadedParam.processName 是权威进程名（实测三进程各自正确）；
    // 而 ctx.applicationInfo.processName 在快手子进程里错误地返回主包名（不带 :messagesdk），
    // 导致"仅主进程注入"判定失效——子进程照样全量注入（本次实测抓到）
    private var realProcName: String? = null

    /**
     * 诊断落盘（2026-09 排障用）：**不经过任何日志门控与去重**。
     *
     * 为什么需要它：排查「打开模块菜单没反应」时，`Logger.once` 出现了自相矛盾的现象 ——
     * 位置更靠后的 `DLHOOK installed`（once）打出来了，而更靠前的注册埋点（同为 once）
     * 没有输出。怀疑 LSPosed 多 ClassLoader 场景下 `Logger.onceTags` 静态去重表
     * 被跨加载器污染。写文件是唯一不受影响的观测手段。
     *
     * 路径与 `AuditMirror` 同域（模块媒体目录），adb 可直接读取核对。
     */
    private fun diagWrite(msg: String) {
        // 路径选择：**不能用** `/sdcard/Android/media/<模块包名>/` ——
        // 该目录属主是模块（u0_aXXX，组 media_rw，other 无权限），而本代码跑在
        // **快手进程**（另一个 uid）里，写入必然 EACCES（与心跳法踩的是同一个坑）。
        // 用 `/sdcard/Download/ManJiao/.sys/` —— AuditMirror 已实测该目录两进程都可写。
        try {
            val f = java.io.File("/sdcard/Download/ManJiao/.sys", "boot_diag.txt")
            f.parentFile?.mkdirs()
            // ★★ 上限 + 轮转（2026-10 收尾）：原先这里是**纯 append、无上限、无轮转**，
            //   而它每次冷启写 1~2 行 ⇒ 随冷启次数**单调增长**，且落在 world-writable
            //   公共目录里（任意 App 可读）。改为走 [Logger.appendCapped] ——
            //   **复用 Logger 既有的「保留一代 .1」滚动约定**，不另造一套轮转实现。
            //
            //   上限取值依据：实测 734 行 / 18.3KB（≈25 字节/行，含「recv=OK」与
            //   「RECV OPEN_MENU」两类）。取 **256KB** ⇒ 256KB ÷ 25B ≈ 10400 行
            //   ≈ **5000+ 次冷启**的存量（本文件就是为排障保留的，这个量级足够）。
            //   之所以不取 8MiB（与 evidence 同级）：它只记「广播到没到 / 接收器注册
            //   成没成」，诊断价值集中在最近几次冷启，不需要 8MiB 的历史。
            //   轮转保留一代 ⇒ 稳态峰值 2 × 256KB = 512KB。
            //
            //   注：本方法**不经过任何日志门控**（这是它的设计目的，见上方 KDoc），
            //   本次只补上限，**不动门控语义**。
            Logger.appendCapped(f, msg + "\n", BOOT_DIAG_MAX_BYTES)
        } catch (_: Throwable) {}
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        Logger.init(this)
        // ★ XposedService 收编（2026-09）：两进程统一经 Service 拿共享配置（广播兜底）
        try { SyncService.init() } catch (_: Throwable) {}
        realProcName = param.processName
        Logger.d("onModuleLoaded process=" + param.processName)
    }

    companion object {
        /**
         * `boot_diag.txt` 体积上限（256KB）。
         *
         * 依据：实测 734 行 / 18.3KB ≈ 25 字节/行 ⇒ 256KB ≈ 10400 行 ≈ 5000+ 次冷启存量；
         * 轮转保留一代（`.1`）⇒ 稳态峰值 512KB。详见 [diagWrite] 的注释。
         */
        private const val BOOT_DIAG_MAX_BYTES = 256L * 1024

        /** app 侧「清空拦截记录」→ 快手进程执行（签名级权限保护，第三方不可伪造） */
        const val ACTION_AUDIT_CLEAR = "io.github.angbang852.manjiao.AUDIT_CLEAR"

        /** app 侧「重置统计」→ 快手进程执行 */
        const val ACTION_AUDIT_RESET = "io.github.angbang852.manjiao.AUDIT_RESET"

        // ★ ACTION_AUDIT_ALLOW（标为误拦）已随该功能移除（2026-09）。

        /**
         * app 侧「打开模块菜单」→ 快手进程弹出菜单（2026-09）。
         *
         * ★ 为什么必须走广播，而不是 app 直接弹：
         * 模块菜单是一个 `Dialog`，必须**依附在快手的 Activity 上**才能显示在快手界面里。
         * app 进程与快手进程是两个独立进程，app 无法往快手的窗口里塞 Dialog ——
         * 这不是没实现，是 Android 的进程/窗口模型决定的。
         *
         * 所以正确做法是：app 发这条广播 → 快手进程内本接收器收到 →
         * 在快手进程里用上下文调 `MainMenuDialog.show(act)`。
         * 走的是与「清空记录/重置统计」完全相同的**签名级权限**通道
         * （`Prefs.PERM_SYNC`，protectionLevel=signature），第三方 app 无法伪造。
         */
        const val ACTION_OPEN_MENU = "io.github.angbang852.manjiao.OPEN_MENU"

        /**
         * 读审计记录条数上限（2026-09 随「7 天保留」策略放宽）。
         *
         * ★ 存量迁移：UI 从未提供此设置项的控件（只能 adb 写），
         * 旧默认 200 几乎必然是「从未配置」而非「用户故意要 200」。
         * 用户实测「200 条不出 1 小时就能累计到」—— 因此读到旧默认值时
         * 直接采用新默认（10000），只有 adb 显式写过其它值才尊重该值。
         */
        fun auditLimitFromPrefs(): Int {
            val v = Prefs.int(Prefs.K_AUDIT_LIMIT, -1)
            return if (v <= 0 || v == 200) 10000 else v.coerceIn(20, 10000)
        }
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val pkg = param.packageName
        if (pkg != KsClass.PKG && pkg != KsClass.PKG_NEBULA) return
        if (!init.compareAndSet(false, true)) return

        Logger.d("onPackageLoaded $pkg")
        val cl = try { param.defaultClassLoader } catch (_: Throwable) { null }
        if (cl == null) { Logger.d("no classloader"); return }

        // ★ 版本自适应探测（2026-09）：**必须最早执行**——后续所有 hook 的路径选择
        // 都依赖 KsVersion.tier；太晚则首个 hook 已在无档位信息下装完。
        // 探测本身全链路容错：失败时降级为「结构指纹推断」，再失败则沿用默认档位
        // （= 既有实测路径 14.7.40 的行为），**不会因探测失败而少装任何 hook**。

        val appCls = Class.forName("android.app.Application", false, cl)
        hook(appCls.getDeclaredMethod("onCreate"))
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .setId("app.onCreate")
            .intercept { chain ->
                chain.proceed()
                try {
                    val ctx = chain.thisObject as Context
                    // ★ 版本号补齐（2026-09 真机实测）：onPackageLoaded 早期探测时
                    //   Context 可能还没就绪 → 版本号读到 null、档位退化为指纹推断。
                    //   此处 Context 一定可用，补一次让日志与设置页拿到真实版本号。
                    //   注意只补版本号、**不重算档位**（档位已在首次探测时定案，
                    //   中途改档会让已装好的 hook 与档位不一致）。
                    Prefs.init(ctx)
                    try { SyncService.armTargetSync() } catch (_: Throwable) {}
                    // ★ 性能优化-仅主进程注入：快手的 push_v3/messagesdk/kwv_sandboxed 等子进程
                    // 不装任何 hook、不注册 receiver、不发 query——子进程注入只会带来
                    // 启动变慢 + 内存浪费 + 广播风暴 + 主线程阻塞（ANR 根因之一）
                    // 进程名优先取 onModuleLoaded 的权威值；ctx.applicationInfo.processName
                    // 在快手子进程实测错误返回主包名（不带 :messagesdk），不可作判定依据
                    val procName = realProcName
                        ?: try { ctx.applicationInfo.processName } catch (_: Throwable) { null }
                    // ★ 默认 true→false（2026-09-30 用户定稿：全关，按需开启）。
                    //   false ⇒ 子进程（push_v3/messagesdk/kwv_sandboxed）也照常装全部 hook，
                    //   等于恢复「仅主进程注入」优化之前的行为（启动更慢/内存更高，见上方 :234 注释）。
                    Logger.once("boot.proc", "proc=$procName mainproc=" + Prefs.bool(Prefs.K_PERF_MAINPROC, false))
                    if (Prefs.bool(Prefs.K_PERF_MAINPROC, false) && procName != null && procName.contains(':')) {
                        Logger.once("boot.subproc", "skip sub process: $procName")
                        return@intercept null
                    }
                    if (!onCreateDone.compareAndSet(false, true)) {
                        // ★ 用 once 而非 always：Logger.always 受 quiet 门控（见 Logger.kt:67
                        //   `fun always(msg) { if (quiet) return; ... }`），而 quiet 默认 true，
                        //   所以排障期用 always 打的日志全部被静默 —— 这是我这次排查绕远路的根因。
                        //   once 不受 quiet 影响，且每进程只打一次。
                        Logger.once("boot.reinit2", "onCreate repeat, skip re-init (接收器不会注册)")
                        return@intercept null
                    }
                    Logger.once("boot.firstinit2", "first init: 继续注册接收器与 hooks")
                    Logger.d("prefs ready")
                    val prefsReceiver = object : BroadcastReceiver() {
                        override fun onReceive(c: Context, i: Intent) {
                            try {
                                if (i.action == Prefs.ACTION_PULL) {
                                    try { Prefs.replyPull(c) } catch (_: Throwable) {}
                                    return
                                }
                                val type = i.getStringExtra("type") ?: return
                                val key = i.getStringExtra("key") ?: return
                                when (type) {
                                    "bool" -> Prefs.applyRemote(key, i.getBooleanExtra("value", false))
                                    "strset" -> {
                                        val arr = try { i.getStringArrayExtra("value") } catch (_: Throwable) { null }
                                        Prefs.applyRemote(key, (arr ?: emptyArray()).toSet())
                                    }
                                    "int" -> Prefs.applyRemote(key, i.getIntExtra("value", 0))
                                    "str" -> Prefs.applyRemote(key, i.getStringExtra("value") ?: "")
                                }
                                Logger.quiet = Prefs.bool(Prefs.K_PERF_QUIET, false)
                                Logger.diag = Prefs.bool(Prefs.K_DIAG, false)
                                Logger.d("prefs sync $type $key")
                            } catch (t: Throwable) { Logger.d("prefs recv fail: ${t.message}") }
                        }
                    }
                    // ★ API 24/25 无带 flags 的 registerReceiver 重载（API 26+ 才有）：
                    // 原直接调用会 NoSuchMethodError 被外层 catch，导致全部 hook 静默不装
                    val prefsFilter = IntentFilter(Prefs.ACTION_UPDATE).apply {
                        addAction(Prefs.ACTION_PULL)
                    }
                    // ★ 诊断（2026-09）：注册结果同时写文件。
                    //   为什么不用 Logger.once：实测出现「DLHOOK installed（once，在更后面）
                    //   打出来了、而这行 once 没打」的矛盾，怀疑 onceTags 去重表在
                    //   LSPosed 多 ClassLoader 场景下被跨进程/跨加载器污染。
                    //   写文件不经过任何日志门控与去重，是唯一无歧义的手段。
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                            ctx.registerReceiver(prefsReceiver, prefsFilter, Prefs.PERM_SYNC, null, Context.RECEIVER_EXPORTED)
                        } else {
                            // ★ API 24/25 没有 flags 重载，但 (receiver, filter, permission, scheduler)
                            // 四参重载自 API 1 就存在——原 else 分支漏传 PERM_SYNC，低版本上任何本机
                            // app 都能伪造 ACTION_UPDATE 向快手进程注入配置（P1 安全）
                            ctx.registerReceiver(prefsReceiver, prefsFilter, Prefs.PERM_SYNC, null)
                        }
                        diagWrite("recv=OK sdk=" + android.os.Build.VERSION.SDK_INT +
                            " pid=" + android.os.Process.myPid())
                    } catch (t: Throwable) {
                        diagWrite("recv=FAIL $t")
                    }
                    Logger.safe("spark") { io.github.angbang852.manjiao.hook.SparkHooks.hook(this, cl) }
                    Logger.once("boot.installed", "INSTALLED: spark")
                } catch (t: Throwable) {
                    // ★ 2026-09：改 once 级别（不受 quiet 门控）。
                    // 原用 Logger.d，quiet 模式下被静默 ⇒ 「主进程只走到 onPackageLoaded
                    // 就没了下文」这种故障完全无痕，排查时看不见。
                    Logger.once("boot.hookfail", "onCreate hook FAILED: $t")
                }
                null
            }
    }
}
