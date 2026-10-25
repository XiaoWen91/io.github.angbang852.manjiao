package io.github.angbang852.manjiao.hook

import android.content.Context
import io.github.angbang852.manjiao.util.Logger
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 对齐原 KsSpark SilentSender 的静默发送器。
 *
 * 主路径：
 *   TextMsg(int, target, text)
 *   -> IMClients.of(subBiz)
 *   -> client.k() -> manager
 *   -> manager.send([conversation], null, null)
 *
 * 不再使用 jfj.b3 / jfj.f2 旧兜底。
 */
object SparkSender {
    private const val CLS_KWAI_MSG = "com.kwai.imsdk.msg.KwaiMsg"
    private const val CLS_TEXT_MSG = "com.kwai.imsdk.msg.TextMsg"
    private const val CLS_KMP_CLIENTS = "com.kwai.kmp.imsdk.client.IMClients"

    private val KMP_CONVERTER_CANDIDATES = arrayOf("ajj.a", "tgj.a")
    private val KMP_CONV_METHOD_CANDIDATES = arrayOf("f")
    private val KMP_SEND_CANDIDATES = arrayOf("d", "E")
    private val KMP_MGR_OF_CANDIDATES = arrayOf("k")

    private var loader: ClassLoader? = null

    private var cKwaiMsg: Class<*>? = null
    private var cTextMsg: Class<*>? = null
    private var ctorMsg: Constructor<*>? = null

    private var cKmpClients: Class<*>? = null
    private var mKmpClientsOf: Method? = null
    private var mKmpMgrOf: Method? = null
    private var mKmpSend: Method? = null

    private var sConv: Any? = null
    private var mConvMsg: Method? = null

    @Volatile
    private var resolved = false
    @Volatile
    private var ready = false
    @Volatile
    private var reason = ""

    fun attach(cl: ClassLoader) {
        loader = cl
        resolved = false
        ready = false
        reason = ""
    }

    fun send(
        context: Context,
        targetId: String,
        message: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ): Boolean {
        resolve()
        if (!ready) return fail(onResult, "unavailable:$reason")
        if (targetId.trim().isEmpty()) return fail(onResult, "empty_target")

        return try {
            val msg = buildMessage(targetId.trim(), message, 5)
                ?: return fail(onResult, "build_null")
            val subBiz = readSubBiz(msg).ifEmpty { "0" }
            preSend(msg)

            val conversation = mConvMsg?.invoke(sConv, msg)
                ?: return fail(onResult, "conv_null")

            var clients = mKmpClientsOf?.invoke(null, subBiz)
            if (clients == null) clients = mKmpClientsOf?.invoke(null, "0")
                ?: return fail(onResult, "clients_null")

            val manager = mKmpMgrOf?.invoke(clients)
                ?: return fail(onResult, "mgr_null")

            val send = mKmpSend ?: return fail(onResult, "no_kmp_send")
            send.invoke(manager, listOf(conversation), null, null)

            Logger.d("spark_sender kmp sent target=$targetId subBiz=$subBiz method=${send.name}")
            onResult?.invoke(true, "sent:kmp.${send.name}")
            true
        } catch (t: Throwable) {
            Logger.d("spark_sender failed target=$targetId err=${t.message}")
            onResult?.invoke(false, "exception:${t.message ?: t.javaClass.simpleName}")
            false
        }
    }

    private fun fail(cb: ((Boolean, String) -> Unit)?, reason: String): Boolean {
        cb?.invoke(false, reason)
        return false
    }

    private fun resolve() {
        if (resolved) return
        synchronized(this) {
            if (resolved) return
            resolved = true
            val cl = loader
            if (cl == null) {
                reason = "no_classloader"
                return
            }

            try {
                cKwaiMsg = Class.forName(CLS_KWAI_MSG, false, cl)
                cTextMsg = Class.forName(CLS_TEXT_MSG, false, cl)
                ctorMsg = cTextMsg?.getConstructor(
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    String::class.java
                )
            } catch (t: Throwable) {
                reason = "msg_ctor:${t.message}"
                return
            }

            try {
                cKmpClients = Class.forName(CLS_KMP_CLIENTS, false, cl)
                mKmpClientsOf = findStaticStringFactory(cKmpClients)
                    ?: throw IllegalStateException("clients_of")

                val clientCls = mKmpClientsOf!!.returnType
                mKmpMgrOf = findMgrOf(clientCls)
                    ?: throw IllegalStateException("mgr_of")

                val mgrCls = mKmpMgrOf!!.returnType
                mKmpSend = findKmpSend(mgrCls)
                    ?: throw IllegalStateException("kmp_send")
            } catch (t: Throwable) {
                reason = "kmp:${t.message}"
                return
            }

            try {
                resolveConverter(cl)
            } catch (t: Throwable) {
                reason = "converter:${t.message}"
                return
            }

            ready = true
            Logger.d("spark_sender resolved kmp clientsOf=${mKmpClientsOf?.name} mgrOf=${mKmpMgrOf?.name} send=${mKmpSend?.name} conv=${mConvMsg?.name}")
        }
    }

    private fun buildMessage(targetId: String, text: String, scene: Int): Any? {
        return try {
            ctorMsg?.newInstance(scene, targetId, text)
        } catch (t: Throwable) {
            Logger.d("spark_sender buildMessage failed: ${t.message}")
            null
        }
    }

    private fun readSubBiz(msg: Any): String {
        return try {
            val m = msg.javaClass.getMethod("getSubBiz")
            m.invoke(msg) as? String ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun preSend(msg: Any) {
        try {
            val cl = loader ?: return
            val c = Class.forName("sej.w", false, cl)
            val kwai = cKwaiMsg ?: return
            val m = c.getDeclaredMethod("j", kwai, Int::class.javaPrimitiveType!!)
            m.isAccessible = true
            m.invoke(null, msg, 0)
        } catch (_: Throwable) {
        }
    }

    private fun findStaticStringFactory(cls: Class<*>): Method? {
        var fallback: Method? = null
        for (m in cls.declaredMethods) {
            if (!Modifier.isStatic(m.modifiers)) continue
            if (m.parameterCount != 1 || m.parameterTypes[0] != String::class.java) continue
            if (m.returnType == Void.TYPE) continue
            if (m.returnType.isPrimitive) continue
            if (m.returnType.name.startsWith("java.")) continue
            if (m.name == "a") return m
            if (fallback == null) fallback = m
        }
        return fallback
    }

    private fun findMgrOf(clientCls: Class<*>): Method? {
        var fallback: Method? = null
        for (m in clientCls.methods) {
            if (m.parameterCount != 0) continue
            val rt = m.returnType
            if (rt == Void.TYPE || rt.isPrimitive) continue
            if (rt.name.startsWith("java.") || rt.name.startsWith("kotlin.")) continue
            if (m.name in KMP_MGR_OF_CANDIDATES) return m
            if (fallback == null) fallback = m
        }
        return fallback
    }

    private fun findKmpSend(mgrCls: Class<*>): Method? {
        var fallback: Method? = null
        for (m in mgrCls.methods) {
            val p = m.parameterTypes
            if (p.size != 3) continue
            if (p[0] != List::class.java) continue
            if (m.returnType != Void.TYPE) continue
            if (p[1].isPrimitive || p[1].name.startsWith("java.")) continue
            if (p[2].isPrimitive || p[2].name.startsWith("java.")) continue
            if (m.name in KMP_SEND_CANDIDATES) return m
            if (fallback == null) fallback = m
        }
        return fallback
    }

    private fun resolveConverter(cl: ClassLoader) {
        for (name in KMP_CONVERTER_CANDIDATES) {
            val c = optClass(cl, name) ?: continue
            val instance = staticSelfInstance(c) ?: continue
            val method = findConvMethod(c) ?: continue
            sConv = instance
            mConvMsg = method
            return
        }
        throw IllegalStateException("no_converter")
    }

    private fun staticSelfInstance(cls: Class<*>): Any? {
        var c: Class<*>? = cls
        while (c != null) {
            for (f in c.declaredFields) {
                if (!Modifier.isStatic(f.modifiers)) continue
                if (f.type != cls) continue
                try {
                    f.isAccessible = true
                    val v = f.get(null)
                    if (v != null) return v
                } catch (_: Throwable) {
                }
            }
            c = c.superclass
        }
        return null
    }

    private fun findConvMethod(cls: Class<*>): Method? {
        for (m in cls.methods) {
            if (m.parameterCount != 1) continue
            if (m.parameterTypes[0] != cKwaiMsg) continue
            val rt = m.returnType
            if (rt == Void.TYPE || rt.isPrimitive) continue
            if (rt.name.startsWith("java.") || rt.name.startsWith("kotlin.")) continue
            if (m.name in KMP_CONV_METHOD_CANDIDATES) return m
        }
        return null
    }

    private fun optClass(cl: ClassLoader, name: String): Class<*>? {
        return try {
            Class.forName(name, false, cl)
        } catch (_: Throwable) {
            null
        }
    }
}
