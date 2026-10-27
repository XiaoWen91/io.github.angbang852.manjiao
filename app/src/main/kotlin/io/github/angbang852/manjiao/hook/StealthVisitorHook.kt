package io.github.angbang852.manjiao.hook

import io.github.angbang852.manjiao.data.Prefs
import io.github.angbang852.manjiao.util.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

object StealthVisitorHook {
    fun hook(module: XposedModule, cl: ClassLoader) {
        hookReturnNull(module, cl, "j6l.u7", "b", arrayOf(String::class.java), "kira.stealth.visitor.path")
        hookReturnTrue(module, cl, "a4l.j0", "lj", emptyArray(), "kira.stealth.visitor.entrance.lj")
        hookReturnTrue(module, cl, "a4l.j0", "nj", emptyArray(), "kira.stealth.visitor.entrance.nj")
        hookReturnTrue(module, cl, "a4l.j0", "oj", emptyArray(), "kira.stealth.visitor.entrance.oj")
    }

    private fun hookReturnNull(
        module: XposedModule,
        cl: ClassLoader,
        className: String,
        methodName: String,
        paramTypes: Array<Class<*>>,
        id: String
    ) {
        try {
            val c = Class.forName(className, false, cl)
            val m = c.getDeclaredMethod(methodName, *paramTypes)
            module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId(id)
                .intercept { chain ->
                    if (Prefs.bool(Prefs.K_SPARK_STEALTH_VISITOR, false)) {
                        Logger.d("stealth visitor: suppress $className.$methodName")
                        null
                    } else {
                        chain.proceed()
                    }
                }
        } catch (t: Throwable) {
            Logger.d("stealth visitor hook miss $className.$methodName: ${t.message}")
        }
    }

    private fun hookReturnTrue(
        module: XposedModule,
        cl: ClassLoader,
        className: String,
        methodName: String,
        paramTypes: Array<Class<*>>,
        id: String
    ) {
        try {
            val c = Class.forName(className, false, cl)
            val m = c.getDeclaredMethod(methodName, *paramTypes)
            module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId(id)
                .intercept { chain ->
                    if (Prefs.bool(Prefs.K_SPARK_STEALTH_VISITOR, false)) {
                        Logger.d("stealth visitor: force true $className.$methodName")
                        true
                    } else {
                        chain.proceed()
                    }
                }
        } catch (t: Throwable) {
            Logger.d("stealth visitor hook miss $className.$methodName: ${t.message}")
        }
    }
}
