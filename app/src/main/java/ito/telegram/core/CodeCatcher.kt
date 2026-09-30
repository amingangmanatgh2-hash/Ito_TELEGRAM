package ito.telegram.core

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Telephony
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * شکارچیِ کد ورود.
 *
 * تلگرام کد را یا با پیامک می‌فرستد یا داخل خودِ تلگرام. کاربر معمولاً کد را
 * کپی می‌کند و برمی‌گردد؛ ما دقیقاً همان لحظه کد را از کلیپ‌بورد برمی‌داریم و
 * خودمان می‌زنیم. اگر اجازه‌ی پیامک هم بدهد، حتی نیازی به کپی‌کردن نیست.
 *
 * نکته‌ی اندروید ۱۰ به بعد: خواندن کلیپ‌بورد فقط وقتی مجاز است که برنامه
 * روی صفحه و فوکوس باشد — برای همین صفحه‌ی ورود بعد از بازگشت کاربر
 * [pollClipboard] را صدا می‌زند، نه در پس‌زمینه.
 */
object CodeCatcher {

    /** کدی که تازه پیدا شده و هنوز مصرف نشده. */
    private val _found = MutableStateFlow<String?>(null)
    val found: StateFlow<String?> = _found

    /** از کجا آمد: «کلیپ‌بورد» یا «پیامک» — فقط برای نشان‌دادن به کاربر. */
    private val _source = MutableStateFlow("")
    val source: StateFlow<String> = _source

    private var lastClipSeen: String = ""
    private var receiver: BroadcastReceiver? = null

    /** رشته‌هایی که قطعاً کد نیستند، تا شماره‌ی تلفن را اشتباهی نزنیم. */
    private val codeRegex = Regex("(?<![0-9])([0-9]{5,6})(?![0-9])")

    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val normalized = TextLabDigits.toAscii(text)
        // اگر متن شبیه شماره‌ی تلفن است (با + شروع می‌شود یا خیلی رقم دارد) رد کن
        val digitsOnly = normalized.filter { it.isDigit() }
        if (digitsOnly.length > 12) {
            // متن طولانی است: دنبال کدِ داخلِ جمله بگرد (پیامکِ تلگرام)
            val hit = codeRegex.findAll(normalized)
                .map { it.groupValues[1] }
                .firstOrNull { it.length in 5..6 }
            return hit
        }
        if (normalized.trim().startsWith("+")) return null
        return codeRegex.find(normalized)?.groupValues?.get(1)
    }

    /** یک‌بار کلیپ‌بورد را نگاه می‌کند. فقط وقتی صفحه فوکوس دارد معنی می‌دهد. */
    fun pollClipboard(context: Context) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (!cm.hasPrimaryClip()) return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
            if (text == lastClipSeen) return
            lastClipSeen = text
            val code = extract(text) ?: return
            _source.value = "کلیپ‌بورد"
            _found.value = code
        } catch (_: Throwable) {
            // بعضی رام‌ها روی خواندن کلیپ‌بورد سخت‌گیرند؛ بی‌سروصدا رد شو
        }
    }

    /** گوش‌دادن به پیامک ورودی. اگر اجازه نداشته باشیم، بی‌صدا هیچ‌کاری نمی‌کند. */
    fun startSmsWatch(context: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                try {
                    if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
                    val body = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                        ?.joinToString(" ") { it.displayMessageBody ?: "" }
                        ?: return
                    val code = extract(body) ?: return
                    _source.value = "پیامک"
                    _found.value = code
                } catch (_: Throwable) {
                }
            }
        }
        try {
            val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            if (Build.VERSION.SDK_INT >= 33) {
                context.applicationContext.registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.applicationContext.registerReceiver(r, filter)
            }
            receiver = r
        } catch (_: Throwable) {
            receiver = null
        }
    }

    fun stopSmsWatch(context: Context) {
        val r = receiver ?: return
        try {
            context.applicationContext.unregisterReceiver(r)
        } catch (_: Throwable) {
        }
        receiver = null
    }

    /** کد مصرف شد؛ دوباره گزارشش نکن. */
    fun consume() {
        _found.value = null
        _source.value = ""
    }

    /** وقتی صفحه‌ی ورود باز می‌شود، حافظه را پاک کن تا کدِ قدیمی دوباره نچسبد. */
    fun reset() {
        lastClipSeen = ""
        _found.value = null
        _source.value = ""
    }
}

/** تبدیل ارقام فارسی/عربی به انگلیسی؛ کدِ تلگرام گاهی با فونت فارسی کپی می‌شود. */
internal object TextLabDigits {
    fun toAscii(s: String): String = buildString(s.length) {
        for (ch in s) {
            val idx = when (ch) {
                in '۰'..'۹' -> ch - '۰'
                in '٠'..'٩' -> ch - '٠'
                else -> -1
            }
            append(if (idx >= 0) ('0' + idx) else ch)
        }
    }
}
