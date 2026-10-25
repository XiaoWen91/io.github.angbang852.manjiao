package io.github.angbang852.manjiao.hook

import android.content.Context
import io.github.angbang852.manjiao.util.Logger
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 测试续火花用的静默发送器。
 *
 * 只做一件事：按快手 IM SDK 的常见形状构造文本消息并调用 Sender。
 * 成功与否通过返回值和回调给出，不做每日守卫、不写状态文件。
 */
object SparkSender {
    private var loader: ClassLoader? = null
    private var cKwai: Class<*>? = null
    private var cText: Class<*>? = null
    private var cFactory: Class<*>? = null
    private var cSender: Class<*>? = null

    @Volatile
    private var resolved = false

    fun attach(cl: ClassLoader) {
        loader = cl
        resolved = false
    }

    fun send(
        context: Context,
        targetId: String,
        message: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ): Boolean {
        resolve()
        if (loader == null) return fail(onResult, "no_classloader")
        return try {
            val msg = buildMessage(message) ?: return fail(onResult, "build_null")
            val sender = findSender(targetId) ?: return fail(onResult, "no_sender")
            val send = findSendMethod(sender.javaClass, msg.javaClass)
                ?: return fail(onResult, "no_send_method")
            val args = buildArgs(send, msg)
            send.invoke(sender, *args)
            Logger.d("spark_sender dispatched target=$targetId method=${send.name}")
            onResult?.invoke(true, "sent")
            true
        } catch (t: Throwable) {
            Logger.d("spark_sender failed target=$targetId err=${t.message}")
            onResult?.invoke(false, t.message ?: t.javaClass.simpleName)
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
            val cl = loader
            cKwai = opt(cl, "com.kwai.imsdk.msg.KwaiMsg")
            cText = opt(cl, "com.kwai.imsdk.msg.TextMsg")
            cFactory = opt(cl, "jfj.b3")
            cSender = opt(cl, "jfj.f2") ?: opt(cl, "jfj.z2")
            resolved = true
        }
    }

    private fun buildMessage(text: String): Any? {
        cText?.declaredConstructors?.forEach { c ->
            val p = c.parameterTypes
            if (p.size == 1 && p[0] == String::class.java) {
                c.isAccessible = true
                return c.newInstance(text)
            }
        }
        cKwai?.declaredConstructors?.forEach { c ->
            c.isAccessible = true
            return newInstanceWithDefaults(c, text)
        }
        return null
    }

    private fun findSender(targetId: String): Any? {
        cFactory?.methods?.forEach { m ->
            if (!Modifier.isStatic(m.modifiers)) return@forEach
            if (m.parameterCount != 1 || m.parameterTypes[0] != String::class.java) return@forEach
            if (m.returnType == Void.TYPE) return@forEach
            try {
                val sender = m.invoke(null, targetId)
                if (sender != null) return sender
            } catch (_: Throwable) {
            }
        }
        cSender?.let { cls ->
            try {
                return cls.getDeclaredConstructor().newInstance()
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun findSendMethod(cls: Class<*>, msgCls: Class<*>): Method? {
        val names = setOf(
            "g0", "e0", "Y", "c0",
            "send", "sendSingle", "sendBatch", "sendNew"
        )
        var best: Method? = null
        for (m in cls.methods) {
            if (m.name !in names) continue
            val p = m.parameterTypes
            if (p.isEmpty() || !p[0].isAssignableFrom(msgCls)) continue
            if (best == null || p.size < best.parameterCount) best = m
        }
        return best
    }

    private fun buildArgs(m: Method, msg: Any): Array<Any?> {
        val p = m.parameterTypes
        val args = arrayOfNulls<Any?>(p.size)
        for (i in p.indices) {
            args[i] = if (i == 0 && p[i].isAssignableFrom(msg.javaClass)) {
                msg
            } else {
                defaultValue(p[i])
            }
        }
        return args
    }

    private fun newInstanceWithDefaults(c: Constructor<*>, text: String): Any {
        val p = c.parameterTypes
        val args = arrayOfNulls<Any?>(p.size)
        for (i in p.indices) {
            args[i] = if (p[i] == String::class.java) text else defaultValue(p[i])
        }
        return c.newInstance(*args)
    }

    private fun defaultValue(t: Class<*>): Any? = when (t) {
        Boolean::class.javaPrimitiveType -> false
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }

    private fun opt(cl: ClassLoader?, name: String): Class<*>? {
        return try {
            if (cl == null) null else Class.forName(name, false, cl)
        } catch (_: Throwable) {
            null
        }
    }
}
