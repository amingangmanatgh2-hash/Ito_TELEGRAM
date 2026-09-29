package ito.telegram.core

/**
 * زبانِ برنامه — سه رجیستر واقعی، نه سه تا اسم قشنگ.
 *
 * هر رشته‌ی UI از اینجا می‌آید، پس با عوض کردن رجیستر، کلِ لحنِ اپ عوض می‌شود.
 * رجیستر «adult» فقط بعد از پاس شدنِ گیتِ سنی در دسترس است.
 */
enum class Register { POLITE, CASUAL, ADULT }

object Lang {

    @Volatile
    var register: Register = Register.CASUAL

    fun fromKey(key: String): Register = when (key) {
        "polite" -> Register.POLITE
        "adult" -> Register.ADULT
        else -> Register.CASUAL
    }

    fun keyOf(r: Register): String = when (r) {
        Register.POLITE -> "polite"
        Register.CASUAL -> "casual"
        Register.ADULT -> "adult"
    }

    fun title(r: Register): String = when (r) {
        Register.POLITE -> "مؤدب و اداری"
        Register.CASUAL -> "خودمونی و باحال"
        Register.ADULT -> "بی‌پرده (+۱۸)"
    }

    /** polite / casual / adult */
    private val table: Map<String, Triple<String, String, String>> = mapOf(
        "app_tagline" to Triple(
            "کلاینت پیشرفته‌ی تلگرام",
            "تلگرامِ توپولِ خودمون",
            "تلگرام، بدون سانسورِ حوصله",
        ),
        "chats" to Triple("گفت‌وگوها", "چت‌ها", "چت‌ها"),
        "settings" to Triple("تنظیمات", "تنظیمات", "تنظیمات"),
        "features" to Triple("قابلیت‌ها", "قابلیت‌ها", "قابلیت‌ها"),
        "search" to Triple("جست‌وجو", "بگرد پیداش کن", "بگرد ببین چی داریم"),
        "empty_chats" to Triple(
            "هنوز گفت‌وگویی وجود ندارد.",
            "اینجا خالیه… یکی رو بزن به تور.",
            "اینجا از خالی هم خالی‌تره. یه پیام بده به کسی، نمیر که.",
        ),
        "login_title" to Triple("ورود به حساب", "بزن بریم تو", "بپر تو، معطل چی؟"),
        "login_hint" to Triple(
            "شماره را با کد کشور وارد کنید.",
            "شماره‌تو با کد کشور بنداز اینجا.",
            "شماره رو بنویس، قرار نیست کسی خواستگاری‌ت کنه.",
        ),
        "code_hint" to Triple(
            "کد تأیید ارسال‌شده را وارد کنید.",
            "کدی که اومد رو بکوب اینجا.",
            "کد رو بزن، تلگرام منتظره، ما هم همینطور.",
        ),
        "offline_banner" to Triple(
            "هسته‌ی شبکه در دسترس نیست؛ حالت نمایشی فعال است.",
            "هسته‌ی شبکه نیست، رفتیم رو حالت نمایشی.",
            "هسته نیست؛ فعلاً داریم ادا درمیاریم، ولی شیک.",
        ),
        "adult_locked" to Triple(
            "این بخش تا تأیید سن قفل است.",
            "تا سنتو ثابت نکنی، قفله.",
            "تا سنت ثابت نشه، این در باز نمی‌شه. قانون قانونه.",
        ),
        "adult_pass" to Triple(
            "تأیید سن انجام شد.",
            "تأیید شد، خوش اومدی.",
            "قبول شدی. حالا مثل آدم بزرگ‌ها رفتار کن.",
        ),
        "adult_fail" to Triple(
            "سن تخمینی کمتر از ۱۸ سال است؛ دسترسی داده نشد.",
            "تخمین سن زیر ۱۸ دراومد، نمی‌شه.",
            "زیر ۱۸ زدی داداش/آبجی. برو درستو بخون، بعداً بیا.",
        ),
        "no_face" to Triple(
            "چهره‌ای تشخیص داده نشد.",
            "چهره‌ای ندیدم.",
            "هیچی ندیدم؛ یا نوری نیست یا واقعاً نامرئی‌ای.",
        ),
        "saved" to Triple("ذخیره شد.", "ذخیره شد.", "سیو شد، خیالت تخت."),
        "honesty" to Triple(
            "شفافیتِ فهرست قابلیت‌ها",
            "صادقانه بگم چندتاش واقعیه",
            "بی‌تعارف: چندتاش واقعاً کار می‌کنه",
        ),
    )

    fun t(key: String): String {
        val row = table[key] ?: return key
        return when (register) {
            Register.POLITE -> row.first
            Register.CASUAL -> row.second
            Register.ADULT -> row.third
        }
    }
}
