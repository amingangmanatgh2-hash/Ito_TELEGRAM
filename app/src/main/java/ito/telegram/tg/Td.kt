package ito.telegram.tg

import java.lang.reflect.Array as RArray
import java.lang.reflect.Method

/**
 * پلِ بازتابی به TDLib.
 *
 * چرا بازتاب (reflection) به‌جای وابستگی مستقیم؟
 *   ۱) فایل‌های `org.drinkless.tdlib` هنگام بیلد دانلود می‌شوند؛ اگر دانلود شکست
 *      بخورد، کدِ ما باز هم کامپایل می‌شود و اپ به‌جای کرش می‌رود روی حالت آفلاین.
 *   ۲) نام فیلدهای TdApi بین نسخه‌ها عوض می‌شود؛ اینجا هر فیلدِ ناموجود بی‌سروصدا
 *      نادیده گرفته می‌شود، پس ارتقای نسخه‌ی TDLib چیزی را نمی‌شکند.
 */
object Td {

    const val PKG = "org.drinkless.tdlib"

    val available: Boolean by lazy {
        try {
            Class.forName("$PKG.Client")
            Class.forName("$PKG.TdApi")
            true
        } catch (e: Throwable) {
            false
        }
    }

    var lastError: String? = null
        private set

    fun cls(name: String): Class<*>? = try {
        Class.forName("$PKG.TdApi\$$name")
    } catch (e: Throwable) {
        null
    }

    fun new(name: String): Any? = try {
        cls(name)?.getDeclaredConstructor()?.newInstance()
    } catch (e: Throwable) {
        lastError = "new $name: ${e.message}"
        null
    }

    /** مقداردهی فیلد در صورت وجود؛ نبودِ فیلد خطا نیست. */
    fun set(obj: Any?, field: String, value: Any?): Boolean {
        if (obj == null) return false
        return try {
            val f = obj.javaClass.getField(field)
            f.isAccessible = true
            when {
                value == null -> f.set(obj, null)
                f.type == Int::class.javaPrimitiveType && value is Number -> f.setInt(obj, value.toInt())
                f.type == Long::class.javaPrimitiveType && value is Number -> f.setLong(obj, value.toLong())
                f.type == Boolean::class.javaPrimitiveType && value is Boolean -> f.setBoolean(obj, value)
                f.type == Double::class.javaPrimitiveType && value is Number -> f.setDouble(obj, value.toDouble())
                else -> f.set(obj, value)
            }
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun get(obj: Any?, field: String): Any? {
        if (obj == null) return null
        return try {
            obj.javaClass.getField(field).get(obj)
        } catch (e: Throwable) {
            null
        }
    }

    fun str(obj: Any?, field: String): String = get(obj, field) as? String ?: ""
    fun long(obj: Any?, field: String): Long = (get(obj, field) as? Number)?.toLong() ?: 0L
    fun int(obj: Any?, field: String): Int = (get(obj, field) as? Number)?.toInt() ?: 0
    fun bool(obj: Any?, field: String): Boolean = (get(obj, field) as? Boolean) ?: false

    fun simpleName(obj: Any?): String = obj?.javaClass?.simpleName ?: ""

    fun emptyArrayOf(className: String): Any? = try {
        cls(className)?.let { RArray.newInstance(it, 0) }
    } catch (e: Throwable) {
        null
    }

    /** ساخت TdApi.FormattedText با متن ساده. */
    fun formattedText(text: String): Any? {
        val ft = new("FormattedText") ?: return null
        set(ft, "text", text)
        set(ft, "entities", emptyArrayOf("TextEntity"))
        return ft
    }

    // ------------------------------------------------------------- client API

    private var clientClass: Class<*>? = null
    private var sendMethod: Method? = null

    fun createClient(onUpdate: (Any) -> Unit, onError: (Throwable) -> Unit): Any? {
        return try {
            val c = Class.forName("$PKG.Client")
            clientClass = c
            val resultHandler = Class.forName("$PKG.Client\$ResultHandler")
            val exceptionHandler = Class.forName("$PKG.Client\$ExceptionHandler")

            val updateProxy = java.lang.reflect.Proxy.newProxyInstance(
                c.classLoader, arrayOf(resultHandler)
            ) { _, method, args ->
                if (method.name == "onResult" && args != null && args.isNotEmpty() && args[0] != null) {
                    try {
                        onUpdate(args[0]!!)
                    } catch (t: Throwable) {
                        onError(t)
                    }
                }
                null
            }
            val exProxy = java.lang.reflect.Proxy.newProxyInstance(
                c.classLoader, arrayOf(exceptionHandler)
            ) { _, method, args ->
                if (method.name == "onException" && args != null && args.isNotEmpty()) {
                    onError(args[0] as? Throwable ?: RuntimeException("tdlib exception"))
                }
                null
            }

            val create = c.methods.firstOrNull { it.name == "create" && it.parameterTypes.size == 3 }
                ?: c.methods.firstOrNull { it.name == "create" }
                ?: return null
            val client = when (create.parameterTypes.size) {
                3 -> create.invoke(null, updateProxy, exProxy, exProxy)
                else -> create.invoke(null, updateProxy)
            }
            sendMethod = c.methods.firstOrNull { it.name == "send" && it.parameterTypes.size == 3 }
                ?: c.methods.firstOrNull { it.name == "send" && it.parameterTypes.size == 2 }
            client
        } catch (e: Throwable) {
            lastError = "createClient: ${e.message}"
            null
        }
    }

    fun send(client: Any?, query: Any?, onResult: ((Any) -> Unit)? = null) {
        if (client == null || query == null) return
        try {
            val m = sendMethod ?: return
            val resultHandler = Class.forName("$PKG.Client\$ResultHandler")
            val handler = java.lang.reflect.Proxy.newProxyInstance(
                client.javaClass.classLoader, arrayOf(resultHandler)
            ) { _, method, args ->
                if (method.name == "onResult" && args != null && args.isNotEmpty() && args[0] != null) {
                    try {
                        onResult?.invoke(args[0]!!)
                    } catch (_: Throwable) {
                    }
                }
                null
            }
            when (m.parameterTypes.size) {
                3 -> m.invoke(client, query, handler, null)
                else -> m.invoke(client, query, handler)
            }
        } catch (e: Throwable) {
            lastError = "send: ${e.message}"
        }
    }

    /** خاموش‌کردن لاگِ پرحرفِ TDLib روی logcat. */
    fun setLogVerbosity(level: Int) {
        try {
            val q = new("SetLogVerbosityLevel") ?: return
            set(q, "newVerbosityLevel", level)
            val c = Class.forName("$PKG.Client")
            val exec = c.methods.firstOrNull { it.name == "execute" } ?: return
            exec.invoke(null, q)
        } catch (_: Throwable) {
        }
    }
}
