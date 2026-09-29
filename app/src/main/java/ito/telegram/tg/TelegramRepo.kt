package ito.telegram.tg

import android.content.Context
import ito.telegram.core.Prefs
import ito.telegram.core.text.TextPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * تنها نقطه‌ای که بقیه‌ی اپ با تلگرام حرف می‌زند.
 * انتخاب بین هسته‌ی واقعی و حالت نمایشی، و اعمال پایپ‌لاین متن، اینجا اتفاق می‌افتد.
 */
object TelegramRepo {

    private lateinit var appContext: Context
    private lateinit var prefs: Prefs

    private val _gateway = MutableStateFlow<TelegramGateway>(DemoGateway("هنوز راه نیفتاده"))
    val gateway: StateFlow<TelegramGateway> = _gateway

    val current: TelegramGateway get() = _gateway.value

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = Prefs.get(appContext)
        restart()
    }

    fun restart() {
        try {
            current.stop()
        } catch (_: Throwable) {
        }
        val apiId = prefs.getString(Prefs.API_ID).trim().toIntOrNull() ?: 0
        val apiHash = prefs.getString(Prefs.API_HASH).trim()
        val gw: TelegramGateway = when {
            !Td.available -> DemoGateway("هسته‌ی نیتیو TDLib در این بیلد موجود نیست")
            apiId <= 0 || apiHash.isEmpty() -> DemoGateway("api_id / api_hash وارد نشده")
            else -> TdLibGateway(appContext)
        }
        _gateway.value = gw
        gw.start(apiId, apiHash)
    }

    /** متن خروجی از پایپ‌لاینِ «روی پیام ارسالی» رد می‌شود. */
    fun sendProcessed(chatId: Long, raw: String) {
        val text = try {
            TextPipeline.run(prefs, TextPipeline.SCOPE_OUT, raw)
        } catch (e: Throwable) {
            raw
        }
        if (text.isBlank()) return
        current.send(chatId, text)
    }

    /** متن ورودی از پایپ‌لاینِ «روی پیام دریافتی» رد می‌شود. */
    fun renderIncoming(raw: String): String = try {
        TextPipeline.run(prefs, TextPipeline.SCOPE_IN, raw)
    } catch (e: Throwable) {
        raw
    }
}
