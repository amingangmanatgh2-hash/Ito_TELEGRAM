package ito.telegram.tg

import ito.telegram.core.Prefs

/**
 * کلیدهای API که داخل خودِ برنامه هستند تا کاربر مجبور نباشد برود my.telegram.org.
 *
 * اینها همان کلیدهایی هستند که در مخزن‌های متن‌بازِ خود تلگرام و ابزارهای رسمی‌اش
 * منتشر شده‌اند. گاهی تلگرام روی یک کلیدِ پرمصرف خطای `API_ID_PUBLISHED_FLOOD`
 * می‌دهد؛ برای همین یک فهرست داریم و به‌محضِ دیدن آن خطا خودکار می‌رویم سراغ بعدی.
 * اگر کاربر کلید شخصی‌اش را در تنظیمات وارد کند، آن همیشه اولویت دارد.
 */
object ApiKeys {

    data class Key(val id: Int, val hash: String, val label: String)

    val builtIn: List<Key> = listOf(
        Key(2040, "b18441a1ff607e10a989891a5462e627", "دسکتاپ"),
        Key(94575, "a3406de8d171bb422bb6ddf3bbd800e2", "نمونه‌ی TDLib"),
        Key(6, "eb06d4abfb49dc3eeb1aeb98ae0f581e", "دسکتاپ قدیمی"),
        Key(4, "014b35b6184100b085b0d0572f9b5103", "اندروید"),
        Key(21724, "3e0cb5efcd52300aec5994fdfc5bdc16", "اندروید ایکس"),
    )

    /** کلیدی که همین حالا باید استفاده شود: اول کلید شخصی، بعد فهرست داخلی. */
    fun current(prefs: Prefs): Key {
        val ownId = prefs.getString(Prefs.API_ID).trim().toIntOrNull() ?: 0
        val ownHash = prefs.getString(Prefs.API_HASH).trim()
        if (ownId > 0 && ownHash.isNotBlank()) return Key(ownId, ownHash, "کلید خودت")
        val idx = prefs.getInt(Prefs.API_KEY_INDEX, 0).coerceIn(0, builtIn.lastIndex)
        return builtIn[idx]
    }

    fun isOwnKey(prefs: Prefs): Boolean =
        (prefs.getString(Prefs.API_ID).trim().toIntOrNull() ?: 0) > 0 &&
            prefs.getString(Prefs.API_HASH).trim().isNotBlank()

    /**
     * می‌رود سراغ کلید بعدی. اگر فهرست تمام شده باشد `false` برمی‌گرداند تا
     * صفحه‌ی ورود بتواند صادقانه بگوید «دیگر کلیدِ آماده نداریم، خودت یکی بساز».
     */
    fun rotate(prefs: Prefs): Boolean {
        if (isOwnKey(prefs)) return false
        val next = prefs.getInt(Prefs.API_KEY_INDEX, 0) + 1
        if (next > builtIn.lastIndex) return false
        prefs.setInt(Prefs.API_KEY_INDEX, next)
        return true
    }

    fun resetRotation(prefs: Prefs) = prefs.setInt(Prefs.API_KEY_INDEX, 0)

    /** آیا این پیام خطا یعنی «کلید API را عوض کن»؟ */
    fun isApiKeyProblem(message: String): Boolean {
        val m = message.uppercase()
        return "API_ID" in m || "API_HASH" in m || "API_KEY" in m
    }
}
