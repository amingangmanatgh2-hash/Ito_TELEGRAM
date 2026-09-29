package ito.telegram.core.text

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

/**
 * آزمایشگاهِ متن.
 *
 * هر آیتم اینجا یک تابع خالصِ واقعی است (نه اسمِ خوشگل). همین‌ها هستند که در
 * کاتالوگ به‌صورت «روی پیام ارسالی / روی پیام دریافتی / ابزار سریع / کلیپ‌بورد»
 * قابل فعال‌سازی می‌شوند، و پایپ‌لاین [TextPipeline] آن‌ها را به ترتیب اجرا می‌کند.
 */
data class Transform(
    val id: String,
    val title: String,
    val desc: String,
    val fn: (String) -> String,
)

object TextLab {

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    private const val ZWNJ = '\u200C'
    private const val RLM = '\u200F'
    private const val LRM = '\u200E'

    // ---------------------------------------------------------------- helpers

    private fun mapDigits(s: String, to: CharArray): String = buildString(s.length) {
        for (ch in s) {
            val idx = when (ch) {
                in '0'..'9' -> ch - '0'
                in '۰'..'۹' -> ch - '۰'
                in '٠'..'٩' -> ch - '٠'
                else -> -1
            }
            append(if (idx >= 0) to[idx] else ch)
        }
    }

    private fun toAsciiDigits(s: String): String = buildString(s.length) {
        for (ch in s) {
            val idx = when (ch) {
                in '۰'..'۹' -> ch - '۰'
                in '٠'..'٩' -> ch - '٠'
                else -> -1
            }
            append(if (idx >= 0) ('0' + idx) else ch)
        }
    }

    private fun sha(algo: String, s: String): String =
        MessageDigest.getInstance(algo).digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun mapChars(s: String, base: Char, table: String, span: Int): String =
        buildString {
            for (ch in s) {
                val i = ch - base
                if (i in 0 until span) {
                    val cp = table.codePointAt(table.offsetByCodePoints(0, i))
                    appendCodePoint(cp)
                } else append(ch)
            }
        }

    private fun unicodeAlpha(s: String, upperStart: Int, lowerStart: Int, digitStart: Int = -1): String =
        buildString {
            for (ch in s) {
                when {
                    ch in 'A'..'Z' -> appendCodePoint(upperStart + (ch - 'A'))
                    ch in 'a'..'z' -> appendCodePoint(lowerStart + (ch - 'a'))
                    digitStart >= 0 && ch in '0'..'9' -> appendCodePoint(digitStart + (ch - '0'))
                    else -> append(ch)
                }
            }
        }

    private val MORSE = mapOf(
        'a' to ".-", 'b' to "-...", 'c' to "-.-.", 'd' to "-..", 'e' to ".", 'f' to "..-.",
        'g' to "--.", 'h' to "....", 'i' to "..", 'j' to ".---", 'k' to "-.-", 'l' to ".-..",
        'm' to "--", 'n' to "-.", 'o' to "---", 'p' to ".--.", 'q' to "--.-", 'r' to ".-.",
        's' to "...", 't' to "-", 'u' to "..-", 'v' to "...-", 'w' to ".--", 'x' to "-..-",
        'y' to "-.--", 'z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---",
        '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
        '8' to "---..", '9' to "----.",
    )

    private val FA_TO_FINGLISH = listOf(
        "خ" to "kh", "چ" to "ch", "ش" to "sh", "ژ" to "zh", "ق" to "gh", "غ" to "gh",
        "آ" to "a", "ا" to "a", "ب" to "b", "پ" to "p", "ت" to "t", "ث" to "s", "ج" to "j",
        "ح" to "h", "د" to "d", "ذ" to "z", "ر" to "r", "ز" to "z", "س" to "s", "ص" to "s",
        "ض" to "z", "ط" to "t", "ظ" to "z", "ع" to "'", "ف" to "f", "ک" to "k", "گ" to "g",
        "ل" to "l", "م" to "m", "ن" to "n", "و" to "v", "ه" to "h", "ی" to "i", "ء" to "",
    )

    private val ARABIC_FIX = listOf(
        'ي' to 'ی', 'ك' to 'ک', 'ۀ' to 'ه', 'ة' to 'ه', 'أ' to 'ا', 'إ' to 'ا', 'ؤ' to 'و',
    )

    private val LEET = mapOf(
        'a' to '4', 'e' to '3', 'i' to '1', 'o' to '0', 's' to '5', 't' to '7', 'b' to '8', 'g' to '9',
    )

    private val VERB_SUFFIX = listOf("ام", "ای", "است", "ایم", "اید", "اند", "ها", "تر", "ترین", "تری")

    /** نیم‌فاصله‌ی واقعی: «می‌» و «نمی‌» و پسوندها را می‌چسباند. */
    fun persianSpacing(input: String): String {
        var s = input
        s = Regex("(^|\\s)(ن?می) ([آ-ی])").replace(s) { m ->
            m.groupValues[1] + m.groupValues[2] + ZWNJ + m.groupValues[3]
        }
        for (suf in VERB_SUFFIX) {
            s = Regex("([آ-ی]) $suf(?=\\s|$|[.،!؟])").replace(s) { m ->
                m.groupValues[1] + ZWNJ + suf
            }
        }
        s = Regex(" +([،؛.!؟:])").replace(s, "$1")
        s = Regex("([،؛.!؟:])(?=[^\\s.،؛!؟:])").replace(s, "$1 ")
        return s
    }

    fun zalgo(s: String, intensity: Int = 3): String {
        val marks = (0x0300..0x036F).toList()
        var seed = 1234567L
        return buildString {
            for (ch in s) {
                append(ch)
                if (ch.isWhitespace()) continue
                repeat(intensity) {
                    seed = (seed * 6364136223846793005L + 1442695040888963407L)
                    appendCodePoint(marks[(abs(seed shr 20) % marks.size).toInt()])
                }
            }
        }
    }

    fun caesar(s: String, shift: Int): String = buildString {
        for (ch in s) {
            append(
                when (ch) {
                    in 'a'..'z' -> 'a' + ((ch - 'a' + shift + 26) % 26)
                    in 'A'..'Z' -> 'A' + ((ch - 'A' + shift + 26) % 26)
                    else -> ch
                }
            )
        }
    }

    private fun titleCase(s: String) = s.split(" ").joinToString(" ") { w ->
        if (w.isEmpty()) w else w[0].uppercase() + w.drop(1)
    }

    private const val B64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** پیاده‌سازی دستی تا هم روی اندروید ۷ کار کند هم در تست‌های خالصِ JVM. */
    private fun b64(s: String): String {
        val data = s.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(4 * ((data.size + 2) / 3))
        var i = 0
        while (i + 2 < data.size) {
            val n = ((data[i].toInt() and 0xFF) shl 16) or
                ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
            sb.append(B64_ALPHABET[(n shr 18) and 63]).append(B64_ALPHABET[(n shr 12) and 63])
                .append(B64_ALPHABET[(n shr 6) and 63]).append(B64_ALPHABET[n and 63])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val n = (data[i].toInt() and 0xFF) shl 16
                sb.append(B64_ALPHABET[(n shr 18) and 63]).append(B64_ALPHABET[(n shr 12) and 63]).append("==")
            }
            2 -> {
                val n = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8)
                sb.append(B64_ALPHABET[(n shr 18) and 63]).append(B64_ALPHABET[(n shr 12) and 63])
                    .append(B64_ALPHABET[(n shr 6) and 63]).append('=')
            }
        }
        return sb.toString()
    }

    private fun unb64(s: String): String = try {
        val clean = s.trim().filter { it in B64_ALPHABET || it == '=' }.trimEnd('=')
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            buffer = (buffer shl 6) or B64_ALPHABET.indexOf(c)
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        val res = String(out.toByteArray(), Charsets.UTF_8)
        if (res.isEmpty() && s.isNotEmpty()) s else res
    } catch (e: Exception) {
        s
    }

    // ----------------------------------------------------------- the registry

    val all: List<Transform> = buildList {
        fun t(id: String, title: String, desc: String, fn: (String) -> String) =
            add(Transform(id, title, desc, fn))

        // --- فارسی‌نویسی
        t("fa_spacing", "نیم‌فاصله‌ی خودکار", "«می رود» را می‌کند «می‌رود» و فاصله‌ی نشانه‌ها را درست می‌کند") { persianSpacing(it) }
        t("fa_arabic_fix", "اصلاح «ي» و «ك» عربی", "حروف عربی را به فارسی استاندارد تبدیل می‌کند") { s ->
            buildString { for (c in s) append(ARABIC_FIX.firstOrNull { it.first == c }?.second ?: c) }
        }
        t("fa_digits", "ارقام فارسی", "۰۱۲۳ به‌جای 0123") { mapDigits(it, PERSIAN_DIGITS) }
        t("ar_digits", "ارقام عربی", "٠١٢٣ به‌جای 0123") { mapDigits(it, ARABIC_DIGITS) }
        t("en_digits", "ارقام انگلیسی", "۰۱۲۳ را برمی‌گرداند به 0123") { toAsciiDigits(it) }
        t("fa_kashida_off", "حذف کشیدگی", "ـــ های تزئینی را پاک می‌کند") { it.replace("\u0640", "") }
        t("fa_diacritics_off", "حذف اعراب", "فتحه و کسره و ضمه را برمی‌دارد") { it.replace(Regex("[\u064B-\u0652\u0670]"), "") }
        t("fa_quotes", "گیومه‌ی فارسی", "\"متن\" را می‌کند «متن»") { s ->
            var i = 0
            buildString { for (c in s) if (c == '"') { append(if (i++ % 2 == 0) '«' else '»') } else append(c) }
        }
        t("fa_finglish", "فارسی به فینگلیش", "سلام → salam") { s ->
            var out = s
            for ((fa, en) in FA_TO_FINGLISH) out = out.replace(fa, en)
            out
        }
        t("fa_rtl_fix", "اصلاح جهت متن", "علامت راست‌به‌چپ به ابتدای خط اضافه می‌کند") { s ->
            s.lines().joinToString("\n") { if (it.isBlank()) it else RLM + it }
        }
        t("ltr_force", "قفلِ چپ‌به‌راست", "برای کد و لینک، جهت را چپ‌به‌راست می‌کند") { LRM + it }
        t("fa_ye_normalize", "یکدست‌سازی «ی» پایانی", "ي/ی/ى را یکی می‌کند") { it.replace('ى', 'ی').replace('ي', 'ی') }

        // --- پاکسازی
        t("trim_spaces", "حذف فاصله‌های اضافه", "چند فاصله‌ی پشت‌سرهم را یکی می‌کند") { it.replace(Regex("[ \\t]+"), " ").trim() }
        t("squeeze_lines", "فشرده‌سازی خطوط خالی", "بیش از دو خط خالی را جمع می‌کند") { it.replace(Regex("\n{3,}"), "\n\n") }
        t("strip_emoji", "حذف ایموجی", "متن را از شکلک پاک می‌کند") { it.replace(Regex("[\\p{So}\\p{Cn}]"), "") }
        t("strip_links", "حذف لینک", "هر URL را از متن برمی‌دارد") { it.replace(Regex("https?://\\S+"), "").trim() }
        t("strip_mentions", "حذف منشن", "@username ها را پاک می‌کند") { it.replace(Regex("@\\w+"), "").trim() }
        t("strip_hashtags", "حذف هشتگ", "#هشتگ‌ها را حذف می‌کند") { it.replace(Regex("#\\S+"), "").trim() }
        t("strip_html", "حذف تگ HTML", "<b>متن</b> → متن") { it.replace(Regex("<[^>]+>"), "") }
        t("strip_invisible", "حذف نویسه‌های نامرئی", "ZWJ/ZWNJ/RLM مخفی را پاک می‌کند") { it.replace(Regex("[\u200B-\u200F\u202A-\u202E\uFEFF]"), "") }
        t("dedupe_lines", "حذف خطوط تکراری", "خطوط تکراری را یکی می‌کند") { s -> s.lines().distinct().joinToString("\n") }
        t("sort_lines", "مرتب‌سازی خطوط", "خطوط را الفبایی می‌چیند") { s -> s.lines().sorted().joinToString("\n") }
        t("shuffle_lines", "به‌هم‌ریختن خطوط", "ترتیب خطوط را قاطی می‌کند") { s -> s.lines().shuffled().joinToString("\n") }
        t("number_lines", "شماره‌گذاری خطوط", "به هر خط شماره می‌دهد") { s ->
            s.lines().mapIndexed { i, l -> "${i + 1}. $l" }.joinToString("\n")
        }
        t("wrap_40", "شکستن خط در ۴۰ نویسه", "متن طولانی را می‌شکند") { s ->
            s.chunked(40).joinToString("\n")
        }

        // --- حالت حروف
        t("upper", "همه بزرگ", "SALAM") { it.uppercase(Locale.ROOT) }
        t("lower", "همه کوچک", "salam") { it.lowercase(Locale.ROOT) }
        t("title_case", "حروف اول بزرگ", "Salam Donya") { titleCase(it) }
        t("sentence_case", "جمله‌ای", "اول هر جمله بزرگ") { s ->
            Regex("(^|[.!?]\\s+)([a-z])").replace(s.lowercase()) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }
        }
        t("alternate_case", "یکی بزرگ یکی کوچک", "sAlAm — لحنِ تمسخر اینترنتی") { s ->
            var i = 0
            buildString { for (c in s) append(if (c.isLetter()) (if (i++ % 2 == 0) c.lowercaseChar() else c.uppercaseChar()) else c) }
        }
        t("swap_case", "جابه‌جایی بزرگ/کوچک", "aBc → AbC") { s ->
            buildString { for (c in s) append(if (c.isUpperCase()) c.lowercaseChar() else c.uppercaseChar()) }
        }
        t("snake", "snake_case", "فاصله‌ها آندرلاین") { it.trim().replace(Regex("\\s+"), "_").lowercase() }
        t("kebab", "kebab-case", "فاصله‌ها خط تیره") { it.trim().replace(Regex("\\s+"), "-").lowercase() }
        t("camel", "camelCase", "برای برنامه‌نویس‌ها") { s ->
            val p = s.trim().split(Regex("\\s+"))
            p.mapIndexed { i, w -> if (i == 0) w.lowercase() else w.replaceFirstChar { c -> c.uppercase() } }.joinToString("")
        }

        // --- کدگذاری
        t("base64", "Base64", "متن را کد می‌کند") { b64(it) }
        t("base64_dec", "رمزگشایی Base64", "کد را برمی‌گرداند به متن") { unb64(it) }
        t("hex", "هگزادسیمال", "بایت‌ها به hex") { s -> s.toByteArray().joinToString(" ") { "%02x".format(it) } }
        t("binary", "باینری", "متن به صفر و یک") { s -> s.toByteArray().joinToString(" ") { b -> Integer.toBinaryString(b.toInt() and 0xFF).padStart(8, '0') } }
        t("url_encode", "URL-encode", "برای لینک‌سازی") { java.net.URLEncoder.encode(it, "UTF-8") }
        t("url_decode", "URL-decode", "برگرداندن %20 ها") { s -> try { java.net.URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s } }
        t("rot13", "ROT13", "رمزِ کلاسیک") { caesar(it, 13) }
        t("caesar3", "رمز سزار (۳)", "سه حرف جابه‌جا") { caesar(it, 3) }
        t("md5", "اثر انگشت MD5", "هش متن") { sha("MD5", it) }
        t("sha1", "اثر انگشت SHA-1", "هش متن") { sha("SHA-1", it) }
        t("sha256", "اثر انگشت SHA-256", "هش امن متن") { sha("SHA-256", it) }
        t("morse", "مورس", "نقطه و خط") { s ->
            s.lowercase().map { MORSE[it] ?: if (it == ' ') "/" else "" }.filter { it.isNotEmpty() }.joinToString(" ")
        }
        t("morse_dec", "رمزگشایی مورس", "نقطه و خط به متن") { s ->
            val rev = MORSE.entries.associate { it.value to it.key }
            s.trim().split(" ").joinToString("") { if (it == "/") " " else (rev[it]?.toString() ?: "") }
        }
        t("reverse", "برعکس‌نویسی", "متن از آخر") { it.reversed() }
        t("reverse_words", "برعکس‌کردن ترتیب کلمات", "کلمه‌ها جابه‌جا") { s -> s.split(" ").reversed().joinToString(" ") }
        t("nato", "الفبای ناتو", "A → Alfa") { s ->
            val n = listOf("Alfa","Bravo","Charlie","Delta","Echo","Foxtrot","Golf","Hotel","India","Juliett","Kilo","Lima","Mike","November","Oscar","Papa","Quebec","Romeo","Sierra","Tango","Uniform","Victor","Whiskey","Xray","Yankee","Zulu")
            s.lowercase().mapNotNull { c -> if (c in 'a'..'z') n[c - 'a'] else if (c == ' ') "/" else null }.joinToString(" ")
        }

        // --- استایل یونیکد (هرکدام یک نگاشتِ واقعی است، نه تمِ رنگی)
        t("bold_math", "توپُر ریاضی", "𝐒𝐚𝐥𝐚𝐦") { unicodeAlpha(it, 0x1D400, 0x1D41A, 0x1D7CE) }
        t("italic_math", "ایتالیک ریاضی", "𝑆𝑎𝑙𝑎𝑚") { unicodeAlpha(it, 0x1D434, 0x1D44E) }
        t("script_math", "شکسته‌ی تزئینی", "𝒮𝒶𝓁𝒶𝓂") { unicodeAlpha(it, 0x1D4D0, 0x1D4EA) }
        t("fraktur", "گوتیک", "𝔖𝔞𝔩𝔞𝔪") { unicodeAlpha(it, 0x1D504, 0x1D51E) }
        t("double_struck", "توخالی", "𝕊𝕒𝕝𝕒𝕞") { unicodeAlpha(it, 0x1D538, 0x1D552, 0x1D7D8) }
        t("monospace", "تک‌عرض", "𝚂𝚊𝚕𝚊𝚖") { unicodeAlpha(it, 0x1D670, 0x1D68A, 0x1D7F6) }
        t("circled", "حروف دایره‌ای", "Ⓢⓐⓛⓐⓜ") { unicodeAlpha(it, 0x24B6, 0x24D0) }
        t("fullwidth", "عرض کامل", "Ｓａｌａｍ") { s -> buildString { for (c in s) if (c in '!'..'~') appendCodePoint(0xFF01 + (c - '!')) else append(c) } }
        t("small_caps", "کپیتال کوچک", "sᴀʟᴀᴍ") { s ->
            val map = "ᴀʙᴄᴅᴇғɢʜɪᴊᴋʟᴍɴᴏᴘQʀsᴛᴜᴠᴡxʏᴢ"
            mapChars(s.lowercase(), 'a', map, 26)
        }
        t("upside_down", "وارونه", "ɯɐlɐS") { s ->
            val from = "abcdefghijklmnopqrstuvwxyz"
            val to = "ɐqɔpǝɟƃɥıɾʞlɯuodbɹsʇnʌʍxʎz"
            buildString { for (c in s.lowercase().reversed()) { val i = from.indexOf(c); append(if (i >= 0) to[i] else c) } }
        }
        t("strike", "خط‌خورده", "S̶a̶l̶a̶m̶") { s -> buildString { for (c in s) { append(c); if (!c.isWhitespace()) append('\u0336') } } }
        t("underline_u", "زیرخط یونیکدی", "S̲a̲l̲a̲m̲") { s -> buildString { for (c in s) { append(c); if (!c.isWhitespace()) append('\u0332') } } }
        t("spaced_out", "ف ا ص ل ه د ا ر", "بین حروف فاصله") { it.toCharArray().joinToString(" ") }
        t("zalgo_light", "زالگوی ملایم", "متن کمی جن‌زده") { zalgo(it, 2) }
        t("zalgo_heavy", "زالگوی وحشی", "متن کاملاً جن‌زده") { zalgo(it, 6) }

        // --- فان و شوخی
        t("leet", "زبان لیت", "h4ck3r sp34k") { s -> buildString { for (c in s.lowercase()) append(LEET[c] ?: c) } }
        t("uwu", "لحن یوووو", "r و l می‌شوند w") { s -> s.replace("r", "w").replace("l", "w").replace("R", "W").replace("L", "W") + " uwu" }
        t("clap", "👏بین👏کلمات👏", "لحن توییتری") { s -> s.trim().split(Regex("\\s+")).joinToString(" 👏 ") }
        t("stutter", "لکنت شوخی", "س‌س‌سلام") { s -> s.split(" ").joinToString(" ") { w -> if (w.length > 2) "${w.take(1)}‌${w.take(1)}‌$w" else w } }
        t("drunk", "لحن خواب‌آلود", "حرف‌ها کش می‌آیند") { s -> s.replace("ا", "اا").replace("و", "ووو") }
        t("shout", "دادِ بلند", "متن + علامت تعجب") { it.uppercase(Locale.ROOT) + "!!!" }
        t("whisper", "پچ‌پچ", "متن داخل پرانتزِ آرام") { "( " + it.lowercase() + " …)" }
        t("emojify", "ایموجی‌پاشی", "کلمات کلیدی ایموجی می‌گیرند") { s ->
            var out = s
            mapOf("سلام" to "سلام 👋", "خنده" to "خنده 😂", "قلب" to "قلب ❤️", "پول" to "پول 💸", "قهوه" to "قهوه ☕", "شب" to "شب 🌙")
                .forEach { (k, v) -> out = out.replace(k, v) }
            out
        }
        t("sarcasm", "لحن طعنه", "sPoNgEbOb") { s ->
            var i = 0
            buildString { for (c in s) append(if (c.isLetter()) (if (i++ % 2 == 0) c.uppercaseChar() else c.lowercaseChar()) else c) }
        }
        t("formal_fa", "رسمی‌سازی سریع", "«میشه» → «امکان دارد»") { s ->
            s.replace("میشه", "امکان دارد").replace("نمیشه", "امکان‌پذیر نیست")
                .replace("باشه", "بسیار خوب").replace("مرسی", "سپاسگزارم").replace("چطوری", "حال شما چطور است")
        }
        t("casual_fa", "خودمونی‌سازی", "«سپاسگزارم» → «مرسی»") { s ->
            s.replace("سپاسگزارم", "مرسی").replace("امکان‌پذیر نیست", "نمیشه")
                .replace("بسیار خوب", "باشه").replace("استحضار", "خبر")
        }
        t("no_vowels", "حذف مصوت‌ها", "slm chtry") { it.replace(Regex("[aeiouAEIOU]"), "") }
        t("pirate", "لحن دزد دریایی", "Arr!") { it + " ☠️ Arr!" }

        // --- ضدرصد / حریم خصوصی متنی
        t("homoglyph", "جایگزینی هم‌شکل", "حروف لاتین با معادلِ سیریلیک، برای دور زدن جست‌وجوی خام") { s ->
            s.replace('a', 'а').replace('e', 'е').replace('o', 'о').replace('c', 'с').replace('p', 'р')
        }
        t("invisible_watermark", "واترمارک نامرئی", "امضای نامرئی داخل متن جاسازی می‌کند") { s ->
            s + "\u200B\u200C\u200B\u200D"
        }
        t("scramble_middle", "قاطی‌کردن وسط کلمات", "مغز باز هم می‌خواند") { s ->
            s.split(" ").joinToString(" ") { w ->
                if (w.length < 4) w else w.first() + w.drop(1).dropLast(1).toList().shuffled().joinToString("") + w.last()
            }
        }
        t("anonymize_numbers", "مخفی‌سازی اعداد", "شماره‌ها ستاره می‌شوند") { it.replace(Regex("\\d"), "*") }
        t("anonymize_names", "مخفی‌سازی منشن", "@ali → @a**") { s ->
            Regex("@(\\w+)").replace(s) { m -> "@" + m.groupValues[1].take(1) + "*".repeat(maxOf(1, m.groupValues[1].length - 1)) }
        }
        t("redact", "سانسور کامل", "همه‌چیز می‌شود ████") { s -> s.split(" ").joinToString(" ") { "█".repeat(it.length.coerceAtMost(8)) } }

        // --- ابزار
        t("count_stats", "آمار متن", "تعداد نویسه/کلمه/خط را ضمیمه می‌کند") { s ->
            val w = s.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
            "$s\n\n— ${s.length} نویسه، $w کلمه، ${s.lines().size} خط"
        }
        t("read_time", "زمان مطالعه", "تخمین زمان خواندن را اضافه می‌کند") { s ->
            val w = s.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
            "$s\n\n— حدود ${maxOf(1, w / 200)} دقیقه خواندن"
        }
        t("quote_block", "نقل‌قول‌سازی", "هر خط با «>» شروع می‌شود") { s -> s.lines().joinToString("\n") { "> $it" } }
        t("code_block", "قالب کد", "متن داخل ``` قرار می‌گیرد") { "```\n$it\n```" }
        t("bullets", "تبدیل خطوط به لیست", "هر خط یک گلوله") { s -> s.lines().filter { it.isNotBlank() }.joinToString("\n") { "• $it" } }
        t("checklist", "چک‌لیست", "هر خط یک تیک‌باکس") { s -> s.lines().filter { it.isNotBlank() }.joinToString("\n") { "☐ $it" } }
        t("hashtagify", "هشتگ‌سازی", "کلمات مهم هشتگ می‌شوند") { s ->
            s.split(" ").joinToString(" ") { if (it.length > 5) "#" + it.replace(Regex("\\s"), "_") else it }
        }
        t("slugify", "اسلاگ لینک", "برای آدرس اینترنتی") { s ->
            s.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
        }
        t("extract_links", "استخراج لینک‌ها", "فقط لینک‌ها را نگه می‌دارد") { s ->
            Regex("https?://\\S+").findAll(s).joinToString("\n") { it.value }.ifBlank { "— لینکی نبود" }
        }
        t("extract_numbers", "استخراج اعداد", "فقط اعداد را نگه می‌دارد") { s ->
            Regex("[\\d۰-۹]+").findAll(s).joinToString(" ") { it.value }.ifBlank { "— عددی نبود" }
        }
        t("extract_emails", "استخراج ایمیل", "فقط ایمیل‌ها") { s ->
            Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+").findAll(s).joinToString("\n") { it.value }.ifBlank { "— ایمیلی نبود" }
        }
        t("mirror_lines", "آینه‌ی خطوط", "ترتیب خطوط برعکس می‌شود") { s -> s.lines().reversed().joinToString("\n") }
        t("truncate_200", "کوتاه‌سازی به ۲۰۰ نویسه", "برای پیام‌های سرراست") { if (it.length <= 200) it else it.take(197) + "…" }
        t("expand_spaces", "کشدار کردن", "بین همه‌چیز فاصله‌ی بیشتر") { it.replace(" ", "   ") }
        t("ascii_box", "قاب دور متن", "متن را در جعبه می‌گذارد") { s ->
            val w = s.lines().maxOf { it.length }
            val top = "┌" + "─".repeat(w + 2) + "┐"
            val bot = "└" + "─".repeat(w + 2) + "┘"
            (listOf(top) + s.lines().map { "│ " + it.padEnd(w) + " │" } + listOf(bot)).joinToString("\n")
        }
        t("banner", "بنر ستاره‌دار", "متن با حاشیه‌ی ✦") { "✦✦✦ $it ✦✦✦" }
        t("timestamp", "مهر زمان", "زمان ارسال را ضمیمه می‌کند") { s ->
            s + "\n— " + java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date())
        }
        t("signature", "امضای شخصی", "امضای ثابتِ شما ته پیام") { "$it\n\n— فرستاده‌شده با ایتو" }

        // --- دور دوم: ابزارهای ساختاری
        t("csv_to_lines", "جداکردن با ویرگول", "a,b,c به سه خط") { it.split(",").joinToString("\n") { p -> p.trim() } }
        t("lines_to_csv", "چسباندن خطوط با ویرگول", "سه خط به a, b, c") { s -> s.lines().filter { it.isNotBlank() }.joinToString(", ") { it.trim() } }
        t("tabs_to_spaces", "تبدیل تب به فاصله", "برای کد خوانا") { it.replace("\t", "    ") }
        t("spaces_to_tabs", "تبدیل فاصله به تب", "برعکس قبلی") { it.replace("    ", "\t") }
        t("indent", "تورفتگی", "دو فاصله ابتدای هر خط") { s -> s.lines().joinToString("\n") { "  $it" } }
        t("outdent", "حذف تورفتگی", "فاصله‌های ابتدای خط پاک") { s -> s.lines().joinToString("\n") { it.trimStart() } }
        t("trim_lines", "تمیزکردن دو سر خطوط", "فاصله‌های اول و آخر هر خط") { s -> s.lines().joinToString("\n") { it.trim() } }
        t("remove_empty_lines", "حذف خطوط خالی", "متن فشرده") { s -> s.lines().filter { it.isNotBlank() }.joinToString("\n") }
        t("join_lines", "چسباندن همه‌ی خطوط", "یک پاراگراف یکپارچه") { s -> s.lines().joinToString(" ") { it.trim() }.trim() }
        t("split_sentences", "هر جمله یک خط", "برای ویرایش دقیق") { s -> s.replace(Regex("([.!?؟])\\s+"), "$1\n") }
        t("json_pretty", "زیباسازی JSON", "با تورفتگی خوانا") { s -> prettyJson(s) }
        t("json_minify", "فشرده‌سازی JSON", "حذف فاصله‌های بی‌مصرف") { s -> s.replace(Regex("\\s+(?=([^\"]*\"[^\"]*\")*[^\"]*$)"), "") }
        t("kv_align", "هم‌تراز کردن کلید و مقدار", "جدول متنی مرتب") { s ->
            val rows = s.lines().map { it.split(":", limit = 2) }
            val w = rows.filter { it.size == 2 }.maxOfOrNull { it[0].trim().length } ?: 0
            rows.joinToString("\n") { if (it.size == 2) it[0].trim().padEnd(w) + " : " + it[1].trim() else it[0] }
        }
        t("table_md", "جدول مارک‌داون", "خطوط جداشده با | به جدول") { s ->
            val rows = s.lines().filter { it.isNotBlank() }
            if (rows.isEmpty()) s else {
                val cols = rows[0].split("|").size
                (listOf(rows[0], List(cols) { "---" }.joinToString("|")) + rows.drop(1)).joinToString("\n")
            }
        }

        // --- دور دوم: تشخیص و استخراج
        t("extract_dates", "استخراج تاریخ", "الگوی ۱۴۰۳/۰۷/۰۸ یا 2026-09-29") { s ->
            Regex("[\\d۰-۹]{2,4}[/\\-.][\\d۰-۹]{1,2}[/\\-.][\\d۰-۹]{1,4}").findAll(s)
                .joinToString("\n") { it.value }.ifBlank { "— تاریخی نبود" }
        }
        t("extract_phones", "استخراج شماره تلفن", "الگوی موبایل ایران و بین‌الملل") { s ->
            Regex("(\\+\\d{6,15}|0\\d{9,10})").findAll(toAsciiDigits(s)).joinToString("\n") { it.value }
                .ifBlank { "— شماره‌ای نبود" }
        }
        t("extract_cards", "استخراج شماره کارت", "۱۶ رقمی بانکی") { s ->
            Regex("\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b").findAll(toAsciiDigits(s))
                .joinToString("\n") { it.value }.ifBlank { "— کارتی نبود" }
        }
        t("extract_iban", "استخراج شبا", "IR + ۲۴ رقم") { s ->
            Regex("IR\\d{24}").findAll(toAsciiDigits(s.uppercase())).joinToString("\n") { it.value }
                .ifBlank { "— شبایی نبود" }
        }
        t("extract_otp", "استخراج کد تأیید", "عدد ۴ تا ۸ رقمی تنها") { s ->
            Regex("(?<![\\d])\\d{4,8}(?![\\d])").findAll(toAsciiDigits(s)).joinToString(" ") { it.value }
                .ifBlank { "— کدی نبود" }
        }
        t("extract_money", "استخراج مبلغ", "عدد + تومان/ریال/دلار") { s ->
            Regex("[\\d۰-۹,]+\\s*(تومان|ریال|دلار|یورو)").findAll(s).joinToString("\n") { it.value }
                .ifBlank { "— مبلغی نبود" }
        }
        t("extract_ips", "استخراج آدرس IP", "IPv4") { s ->
            Regex("\\b(\\d{1,3}\\.){3}\\d{1,3}\\b").findAll(s).joinToString("\n") { it.value }.ifBlank { "— آی‌پی‌ای نبود" }
        }
        t("extract_ids", "استخراج شناسه‌ی عددی بلند", "مثل شناسه‌ی چت") { s ->
            Regex("-?\\d{9,}").findAll(toAsciiDigits(s)).joinToString("\n") { it.value }.ifBlank { "— شناسه‌ای نبود" }
        }
        t("mask_cards", "ماسک شماره کارت", "۶۰۳۷ **** **** ۱۲۳۴") { s ->
            Regex("\\b(\\d{4})[ -]?\\d{4}[ -]?\\d{4}[ -]?(\\d{4})\\b").replace(toAsciiDigits(s)) { m ->
                "${m.groupValues[1]} **** **** ${m.groupValues[2]}"
            }
        }
        t("mask_phones", "ماسک شماره تلفن", "۰۹۱۲***۴۵۶۷") { s ->
            Regex("(0\\d{3})\\d{3}(\\d{4})").replace(toAsciiDigits(s)) { m -> m.groupValues[1] + "***" + m.groupValues[2] }
        }
        t("mask_emails", "ماسک ایمیل", "a***@site.com") { s ->
            Regex("([\\w.+-])[\\w.+-]*(@[\\w-]+\\.[\\w.]+)").replace(s) { m -> m.groupValues[1] + "***" + m.groupValues[2] }
        }
        t("strip_utm", "پاک‌سازی پارامتر رهگیری", "utm و fbclid و gclid حذف") { s ->
            Regex("([?&])(utm_[a-z]+|fbclid|gclid|igshid|yclid)=[^&\\s]*").replace(s, "$1")
                .replace(Regex("[?&]+(\\s|$)"), "$1").replace("?&", "?")
        }
        t("linkify", "لینک‌سازی", "www.x.com می‌شود https://www.x.com") { s ->
            Regex("(?<!//)\\bwww\\.[\\w.-]+\\S*").replace(s) { m -> "https://" + m.value }
        }
        t("domain_only", "فقط دامنه‌ی لینک‌ها", "برای دیدن مقصد واقعی") { s ->
            Regex("https?://([^/\\s]+)\\S*").replace(s) { m -> m.groupValues[1] }
        }

        // --- دور دوم: عدد و تاریخ
        t("thousand_sep", "جداکننده‌ی هزارگان", "1000000 → 1,000,000") { s ->
            Regex("\\d{4,}").replace(toAsciiDigits(s)) { m -> m.value.reversed().chunked(3).joinToString(",").reversed() }
        }
        t("remove_sep", "حذف جداکننده‌ی عدد", "1,000 → 1000") { s -> Regex("(?<=\\d),(?=\\d)").replace(s, "") }
        t("toman_to_words", "مبلغ به حروف", "۱۲۰۰ → هزار و دویست") { s ->
            Regex("\\d{1,9}").replace(toAsciiDigits(s)) { m -> numToPersianWords(m.value.toLong()) }
        }
        t("rial_to_toman", "ریال به تومان", "یک صفر کمتر") { s ->
            Regex("([\\d,]+)\\s*ریال").replace(toAsciiDigits(s)) { m ->
                val n = m.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
                "${n / 10} تومان"
            }
        }
        t("percent_calc", "حساب درصد", "«۲۰٪ از ۵۰۰» را حساب می‌کند") { s ->
            Regex("([\\d.]+)\\s*[٪%]\\s*(از|of)\\s*([\\d.]+)").replace(toAsciiDigits(s)) { m ->
                val p = m.groupValues[1].toDoubleOrNull() ?: 0.0
                val v = m.groupValues[3].toDoubleOrNull() ?: 0.0
                trimNum(p * v / 100.0)
            }
        }
        t("math_eval", "حل عبارت ریاضی", "۲+۳*۴ داخل متن حساب می‌شود") { s ->
            Regex("(?<![\\w.])[-+]?[\\d.]+(\\s*[-+*/]\\s*[-+]?[\\d.]+)+").replace(toAsciiDigits(s)) { m ->
                trimNum(evalArithmetic(m.value))
            }
        }
        t("celsius_f", "سانتی‌گراد به فارنهایت", "۲۵C → 77F") { s ->
            Regex("([\\d.]+)\\s*°?[cC]\\b").replace(toAsciiDigits(s)) { m ->
                trimNum((m.groupValues[1].toDoubleOrNull() ?: 0.0) * 9 / 5 + 32) + "°F"
            }
        }
        t("km_mile", "کیلومتر به مایل", "۱۰km → 6.21mi") { s ->
            Regex("([\\d.]+)\\s*km\\b").replace(toAsciiDigits(s)) { m ->
                trimNum((m.groupValues[1].toDoubleOrNull() ?: 0.0) * 0.621371) + "mi"
            }
        }
        t("kg_lb", "کیلوگرم به پوند", "۱۰kg → 22.05lb") { s ->
            Regex("([\\d.]+)\\s*kg\\b").replace(toAsciiDigits(s)) { m ->
                trimNum((m.groupValues[1].toDoubleOrNull() ?: 0.0) * 2.20462) + "lb"
            }
        }
        t("epoch_to_date", "تایم‌استمپ به تاریخ", "عدد یونیکس خوانا می‌شود") { s ->
            Regex("\\b1[0-9]{9}\\b").replace(toAsciiDigits(s)) { m ->
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    .format(java.util.Date(m.value.toLong() * 1000))
            }
        }
        t("g_to_jalali", "میلادی به شمسی", "2026-09-29 → ۱۴۰۵/۰۷/۰۷") { s ->
            Regex("(\\d{4})-(\\d{2})-(\\d{2})").replace(toAsciiDigits(s)) { m ->
                val (y, mo, d) = Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                val j = JalaliDate.fromGregorian(y, mo, d)
                mapDigits("%04d/%02d/%02d".format(j.year, j.month, j.day), PERSIAN_DIGITS)
            }
        }
        t("jalali_to_g", "شمسی به میلادی", "۱۴۰۵/۰۷/۰۷ → 2026-09-29") { s ->
            Regex("(\\d{4})/(\\d{1,2})/(\\d{1,2})").replace(toAsciiDigits(s)) { m ->
                val g = JalaliDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()).toGregorian()
                "%04d-%02d-%02d".format(g[0], g[1], g[2])
            }
        }
        t("weekday_fa", "روز هفته‌ی تاریخ", "تاریخ را با نام روز می‌نویسد") { s ->
            Regex("(\\d{4})-(\\d{2})-(\\d{2})").replace(toAsciiDigits(s)) { m ->
                val cal = java.util.Calendar.getInstance()
                cal.set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt())
                val names = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")
                m.value + " (" + names[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1] + ")"
            }
        }

        // --- دور دوم: سبک و لحن
        t("bullet_arrow", "فهرست با پیکان", "هر خط با ◄") { s -> s.lines().filter { it.isNotBlank() }.joinToString("\n") { "◄ $it" } }
        t("numbered_fa", "شماره‌گذاری فارسی", "۱. ۲. ۳.") { s ->
            s.lines().filter { it.isNotBlank() }.mapIndexed { i, l -> mapDigits("${i + 1}", PERSIAN_DIGITS) + ". $l" }.joinToString("\n")
        }
        t("headline", "تیتر", "متن بزرگ و توپر با خط زیر") { s -> "**$s**\n" + "─".repeat(s.length.coerceAtMost(30)) }
        t("spoiler_md", "قالب اسپویلر", "متن زیر پرده") { "||$it||" }
        t("bold_md", "توپر مارک‌داون", "**متن**") { "**$it**" }
        t("italic_md", "ایتالیک مارک‌داون", "__متن__") { "__${it}__" }
        t("strike_md", "خط‌خورده‌ی مارک‌داون", "~~متن~~") { "~~$it~~" }
        t("mono_md", "تک‌عرض مارک‌داون", "`متن`") { "`$it`" }
        t("center_pad", "وسط‌چین متنی", "با فاصله وسط می‌آید") { s ->
            s.lines().joinToString("\n") { l -> " ".repeat(maxOf(0, (34 - l.length) / 2)) + l }
        }
        t("double_space", "فاصله‌ی دوبرابر بین خطوط", "برای خواندن راحت‌تر") { s -> s.lines().joinToString("\n\n") }
        t("acronym", "ساخت سرواژه", "حرف اول کلمات") { s -> s.split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.first() }.joinToString("") }
        t("initials", "حروف اول با نقطه", "ع.ر.م") { s -> s.split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(".") { it.first().toString() } }
        t("first_words", "خلاصه‌ی ده کلمه‌ای", "ده کلمه‌ی اول") { s -> s.split(Regex("\\s+")).take(10).joinToString(" ") + if (s.split(Regex("\\s+")).size > 10) " …" else "" }
        t("keywords", "کلمات کلیدی", "پرتکرارترین کلمات متن") { s ->
            s.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 3 }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
                .joinToString("، ") { it.key }.ifBlank { "— کلمه‌ی کلیدی‌ای پیدا نشد" }
        }
        t("unique_words", "کلمات یکتا", "هر کلمه یک بار") { s -> s.split(Regex("\\s+")).distinct().joinToString(" ") }
        t("longest_word", "بلندترین کلمه", "برنده‌ی مسابقه") { s -> s.split(Regex("\\s+")).maxByOrNull { it.length } ?: s }
        t("char_freq", "بسامد نویسه‌ها", "پرتکرارترین حروف") { s ->
            s.filter { it.isLetter() }.groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }.take(5).joinToString("، ") { "${it.key}:${it.value}" }
        }
        t("palindrome_check", "بررسی متقارن‌بودن", "می‌گوید متن برعکسش هم همان است یا نه") { s ->
            val c = s.filter { it.isLetterOrDigit() }.lowercase()
            "$s\n— " + if (c == c.reversed()) "متقارن است ✔" else "متقارن نیست ✘"
        }
        t("random_case", "حالت تصادفی حروف", "کاملاً بی‌نظم") { s ->
            buildString { for (c in s) append(if (kotlin.random.Random.nextBoolean()) c.uppercaseChar() else c.lowercaseChar()) }
        }
        t("vowel_stretch", "کشیدنِ مصوت‌ها", "سلااااام") { s -> s.replace(Regex("([aeiouآاوی])"), "$1$1$1") }
        t("no_spaces", "حذف کامل فاصله", "همه‌چیز به‌هم") { it.replace(Regex("\\s+"), "") }
        t("every_other_char", "یکی در میان", "حذف نویسه‌های زوج") { s -> s.filterIndexed { i, _ -> i % 2 == 0 } }
        t("rot_words", "چرخش کلمات", "کلمه‌ی اول می‌رود آخر") { s ->
            val p = s.split(" ")
            if (p.size < 2) s else (p.drop(1) + p.first()).joinToString(" ")
        }
        t("repeat_twice", "تکرار دوباره", "متن دو بار") { "$it $it" }
        t("emoji_only", "فقط ایموجی‌ها", "بقیه‌ی متن حذف") { s -> s.filter { it.code > 0x2000 && !it.isLetter() }.ifBlank { "—" } }
        t("letters_only", "فقط حروف", "عدد و نشانه حذف") { s -> s.filter { it.isLetter() || it.isWhitespace() } }
        t("digits_only", "فقط اعداد", "بقیه حذف") { s -> toAsciiDigits(s).filter { it.isDigit() }.ifBlank { "—" } }
        t("no_punct", "حذف نشانه‌گذاری", "نقطه و ویرگول و… حذف") { s -> s.filter { it.isLetterOrDigit() || it.isWhitespace() } }
        t("smart_ellipsis", "سه‌نقطه‌ی استاندارد", "... می‌شود …") { it.replace("...", "…") }
        t("fix_repeated_punct", "اصلاح نشانه‌های تکراری", "!!!! می‌شود !") { s -> Regex("([!?؟.،])\\1{1,}").replace(s, "$1") }
        t("fix_spacing_parens", "اصلاح فاصله‌ی پرانتز", "( متن ) می‌شود (متن)") { s ->
            s.replace(Regex("\\(\\s+"), "(").replace(Regex("\\s+\\)"), ")")
        }

        // --- دور سوم
        t("finglish_to_fa", "فینگلیش به فارسی", "salam → سلام") { s -> finglishToPersian(s) }
        t("sort_words", "مرتب‌سازی کلمات", "الفبایی داخل هر خط") { s ->
            s.lines().joinToString("\n") { l -> l.split(" ").filter { it.isNotBlank() }.sorted().joinToString(" ") }
        }
        t("dedupe_words", "حذف کلمه‌ی تکراری پشت‌سرهم", "«خیلی خیلی» می‌شود «خیلی»") { s ->
            Regex("\\b(\\S+)( \\1\\b)+").replace(s) { m -> m.groupValues[1] }
        }
        t("dedupe_chars", "حذف حروف تکراری اضافه", "سلااااام → سلام") { s ->
            Regex("(.)\\1{2,}").replace(s) { m -> m.groupValues[1] }
        }
        t("questions_only", "فقط جمله‌های پرسشی", "بقیه حذف") { s ->
            s.split(Regex("(?<=[.!?؟])\\s+")).filter { it.trimEnd().endsWith("؟") || it.trimEnd().endsWith("?") }
                .joinToString("\n").ifBlank { "— پرسشی نبود" }
        }
        t("remove_numbering", "حذف شماره‌گذاری خطوط", "۱. و 1) پاک می‌شود") { s ->
            s.lines().joinToString("\n") { Regex("^\\s*[\\d۰-۹]+[.)\\-]\\s*").replace(it, "") }
        }
        t("numbered_to_bullets", "شماره به گلوله", "۱. می‌شود •") { s ->
            s.lines().joinToString("\n") { Regex("^\\s*[\\d۰-۹]+[.)]\\s*").replace(it, "• ") }
        }
        t("short_lines_out", "حذف خطوط خیلی کوتاه", "کمتر از سه نویسه") { s ->
            s.lines().filter { it.trim().length >= 3 }.joinToString("\n")
        }
        t("wrap_80", "شکستن خط در ۸۰ نویسه", "برای ترمینال‌بازها") { s ->
            s.split(" ").fold(mutableListOf("")) { acc, w ->
                if ((acc.last() + " " + w).trim().length > 80) acc.add(w) else acc[acc.size - 1] = (acc.last() + " " + w).trim()
                acc
            }.joinToString("\n")
        }
        t("highlight_keywords", "برجسته‌سازی کلمات کلیدی", "دور کلمات مهم کروشه می‌گذارد") { s ->
            val keys = s.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 5 }
                .groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            var out = s
            keys.forEach { k -> out = out.replace(k, "[$k]") }
            out
        }
        t("word_freq_table", "جدول بسامد کلمات", "پرتکرارها با تعداد") { s ->
            s.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(10)
                .joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "—" }
        }
        t("letter_stats_fa", "آمار حروف فارسی", "چند حرف فارسی و چند لاتین") { s ->
            val fa = s.count { it in 'آ'..'ی' }
            val en = s.count { it in 'a'..'z' || it in 'A'..'Z' }
            "$s\n— فارسی: $fa، لاتین: $en"
        }
        t("template_formal_letter", "قالب نامه‌ی رسمی", "متن را داخل قالب اداری می‌گذارد") { s ->
            "با سلام و احترام،\n\n$s\n\nبا تشکر"
        }
        t("template_followup", "قالب پیگیری", "لحن مؤدبانه‌ی یادآوری") { s ->
            "سلام، پیرو صحبت قبلی:\n$s\nممنون می‌شوم راهنمایی بفرمایید."
        }
        t("template_apology", "قالب عذرخواهی", "جمله‌بندی آماده") { s ->
            "بابت این مورد شرمنده‌ام:\n$s\nجبران می‌کنم."
        }
        t("emoji_to_words", "ایموجی به توضیح", "❤️ می‌شود [قلب]") { s ->
            var out = s
            mapOf("❤️" to "[قلب]", "😂" to "[خنده]", "😊" to "[لبخند]", "👍" to "[تأیید]",
                "🙏" to "[تشکر]", "🔥" to "[آتش]", "😭" to "[گریه]", "😍" to "[عاشقانه]")
                .forEach { (k, v) -> out = out.replace(k, v) }
            out
        }
        t("obfuscate_spaces", "فاصله‌ی نامرئی بین حروف", "برای دور زدن جست‌وجوی ساده") { s ->
            s.map { it.toString() }.joinToString("\u200B")
        }
        t("first_letter_upper_fa", "بزرگ‌نمایی حرف اول خط", "برای فهرست‌های تمیز") { s ->
            s.lines().joinToString("\n") { l -> l.trim().replaceFirstChar { c -> c.uppercase() } }
        }
        t("strip_duplicate_spaces_fa", "یکدست‌سازی فاصله‌ها", "نیم‌فاصله‌ی بی‌مورد هم پاک می‌شود") { s ->
            s.replace("\u200C\u200C", "\u200C").replace(Regex(" {2,}"), " ")
        }
        t("checksum_line", "افزودن خط کنترل", "هش کوتاه ته متن برای اثبات دست‌نخوردگی") { s ->
            s + "\n— #" + sha("SHA-256", s).take(8)
        }
    }

    private val FINGLISH_MAP = listOf(
        "khah" to "خواه", "kh" to "خ", "ch" to "چ", "sh" to "ش", "zh" to "ژ", "gh" to "ق",
        "aa" to "آ", "ee" to "ی", "oo" to "و", "ou" to "و",
        "a" to "ا", "b" to "ب", "p" to "پ", "t" to "ت", "s" to "س", "j" to "ج",
        "h" to "ه", "d" to "د", "z" to "ز", "r" to "ر", "f" to "ف", "k" to "ک",
        "g" to "گ", "l" to "ل", "m" to "م", "n" to "ن", "v" to "و", "w" to "و",
        "y" to "ی", "i" to "ی", "u" to "و", "o" to "", "e" to "", "c" to "ک", "q" to "ق", "x" to "خ",
    )

    /** تبدیل تقریبیِ فینگلیش به فارسی — برای پیام‌های کوتاه واقعاً کار می‌کند. */
    internal fun finglishToPersian(input: String): String = buildString {
        for (word in input.split(" ")) {
            if (word.isBlank()) { append(word).append(' '); continue }
            if (word.any { it in 'آ'..'ی' }) { append(word).append(' '); continue }
            var i = 0
            val lower = word.lowercase()
            while (i < lower.length) {
                var matched = false
                for ((lat, fa) in FINGLISH_MAP) {
                    if (lower.startsWith(lat, i)) {
                        append(fa)
                        i += lat.length
                        matched = true
                        break
                    }
                }
                if (!matched) { append(lower[i]); i++ }
            }
            append(' ')
        }
    }.trim()

    // ------------------------------------------------------------- utilities

    internal fun trimNum(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d)

    /** ارزیابی امنِ یک عبارت حسابی ساده (بدون eval و بدون ریسک). */
    internal fun evalArithmetic(expr: String): Double {
        val tokens = Regex("[-+*/]|[\\d.]+").findAll(expr.replace(" ", "")).map { it.value }.toMutableList()
        if (tokens.isEmpty()) return 0.0
        // ضرب و تقسیم
        var i = 1
        while (i < tokens.size - 1) {
            if (tokens[i] == "*" || tokens[i] == "/") {
                val a = tokens[i - 1].toDoubleOrNull() ?: 0.0
                val b = tokens[i + 1].toDoubleOrNull() ?: 0.0
                val r = if (tokens[i] == "*") a * b else if (b == 0.0) 0.0 else a / b
                tokens[i - 1] = r.toString()
                tokens.removeAt(i); tokens.removeAt(i)
            } else i += 2
        }
        var acc = tokens[0].toDoubleOrNull() ?: 0.0
        var j = 1
        while (j < tokens.size - 1) {
            val b = tokens[j + 1].toDoubleOrNull() ?: 0.0
            acc = if (tokens[j] == "+") acc + b else acc - b
            j += 2
        }
        return acc
    }

    private val ONES = listOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    private val TEENS = listOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    private val TENS = listOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private val HUNDREDS = listOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")

    internal fun numToPersianWords(n: Long): String {
        if (n == 0L) return "صفر"
        if (n < 0) return "منفی " + numToPersianWords(-n)
        val parts = mutableListOf<String>()
        var rest = n
        val scales = listOf(1_000_000_000L to "میلیارد", 1_000_000L to "میلیون", 1_000L to "هزار")
        for ((v, name) in scales) {
            if (rest >= v) {
                val c = rest / v
                parts += (if (c == 1L && v == 1000L) "" else numToPersianWords(c) + " ") + name
                rest %= v
            }
        }
        if (rest > 0) parts += under1000(rest.toInt())
        return parts.joinToString(" و ").trim()
    }

    private fun under1000(n: Int): String {
        val out = mutableListOf<String>()
        val h = n / 100
        val r = n % 100
        if (h > 0) out += HUNDREDS[h]
        when {
            r in 10..19 -> out += TEENS[r - 10]
            else -> {
                val t = r / 10
                val o = r % 10
                if (t > 0) out += TENS[t]
                if (o > 0) out += ONES[o]
            }
        }
        return out.joinToString(" و ")
    }

    internal fun prettyJson(src: String): String {
        val sb = StringBuilder()
        var indent = 0
        var inStr = false
        var prev = ' '
        for (c in src) {
            when {
                c == '"' && prev != '\\' -> { inStr = !inStr; sb.append(c) }
                inStr -> sb.append(c)
                c == '{' || c == '[' -> { indent++; sb.append(c).append('\n').append("  ".repeat(indent)) }
                c == '}' || c == ']' -> { indent = maxOf(0, indent - 1); sb.append('\n').append("  ".repeat(indent)).append(c) }
                c == ',' -> sb.append(c).append('\n').append("  ".repeat(indent))
                c == ':' -> sb.append(": ")
                c.isWhitespace() -> {}
                else -> sb.append(c)
            }
            prev = c
        }
        return sb.toString()
    }

    val byId: Map<String, Transform> = all.associateBy { it.id }

    fun apply(ids: List<String>, input: String): String {
        var out = input
        for (id in ids) {
            val tr = byId[id] ?: continue
            out = try {
                tr.fn(out)
            } catch (e: Exception) {
                out // یک ترنسفورمِ خراب نباید کلِ پیام را بترکاند
            }
        }
        return out
    }
}
