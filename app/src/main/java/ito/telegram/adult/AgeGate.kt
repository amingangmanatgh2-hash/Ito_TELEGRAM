package ito.telegram.adult

import ito.telegram.core.Prefs
import kotlin.math.abs

/**
 * سیاستِ گیت سنی — منطق خالص، بدون اندروید، تا بشود در تست واحد سنجیدش.
 */
object AgeGate {

    const val MIN_AGE = 18
    const val MIN_SAMPLES = 6
    const val REQUIRED_CHALLENGES = 2

    /** حاشیه‌ی اطمینان در حالت سخت‌گیر: تخمین باید با فاصله بالای ۱۸ باشد. */
    const val STRICT_MARGIN = 1.5f

    data class Challenge(
        val blink: Boolean = false,
        val turn: Boolean = false,
        val smile: Boolean = false,
    ) {
        val passed: Int get() = listOf(blink, turn, smile).count { it }
        val ok: Boolean get() = passed >= REQUIRED_CHALLENGES
    }

    sealed class Verdict {
        data class Allowed(val age: Int) : Verdict()
        data class Denied(val age: Int, val reason: String) : Verdict()
        data class Inconclusive(val reason: String) : Verdict()
    }

    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2f
    }

    fun spread(values: List<Float>): Float {
        if (values.size < 2) return 99f
        val m = median(values)
        return median(values.map { abs(it - m) }) * 2f
    }

    /**
     * @param samples تخمین‌های سن جمع‌آوری‌شده
     * @param challenge نتیجه‌ی تست زنده‌بودن
     * @param strict حالت سخت‌گیر (پیش‌فرض روشن)
     * @param faces تعداد چهره‌های دیده‌شده در فریم آخر
     * @param modelReady مدل واقعاً بارگذاری شده یا نه
     */
    fun decide(
        samples: List<Float>,
        challenge: Challenge,
        strict: Boolean,
        faces: Int,
        modelReady: Boolean,
    ): Verdict {
        if (!modelReady) return Verdict.Inconclusive("مدل تخمین سن بارگذاری نشد")
        if (faces > 1) return Verdict.Inconclusive("بیش از یک چهره جلوی دوربین است")
        if (samples.size < MIN_SAMPLES) return Verdict.Inconclusive("نمونه‌ی کافی گرفته نشد")
        if (!challenge.ok) return Verdict.Inconclusive("تست زنده‌بودن کامل نشد")

        val m = median(samples)
        val sp = spread(samples)
        if (strict && sp > 8f) return Verdict.Inconclusive("تخمین‌ها پراکنده‌اند؛ نور و زاویه را بهتر کنید")

        val threshold = if (strict) MIN_AGE + STRICT_MARGIN else MIN_AGE.toFloat()
        return if (m >= threshold) {
            Verdict.Allowed(m.toInt())
        } else {
            Verdict.Denied(m.toInt(), if (m < MIN_AGE) "سن تخمینی زیر ۱۸ سال" else "تخمین به مرز ۱۸ نزدیک است")
        }
    }

    /** آیا دسترسی بزرگسال هنوز معتبر است؟ (با در نظر گرفتن بازبینی دوره‌ای) */
    fun unlocked(prefs: Prefs, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!prefs.getBool(Prefs.ADULT_UNLOCKED)) return false
        val at = prefs.getLong(Prefs.ADULT_VERIFIED_AT)
        if (at <= 0L) return false
        val recheckDays = if (prefs.isFeatureOn("adult_recheck")) 7 else 30
        val maxAge = recheckDays * 24L * 3600_000L
        return nowMs - at <= maxAge
    }

    fun store(prefs: Prefs, age: Int, nowMs: Long = System.currentTimeMillis()) {
        prefs.setBool(Prefs.ADULT_UNLOCKED, true)
        prefs.setInt(Prefs.ADULT_VERIFIED_AGE, age)
        prefs.setLong(Prefs.ADULT_VERIFIED_AT, nowMs)
    }

    fun lock(prefs: Prefs) {
        prefs.setBool(Prefs.ADULT_UNLOCKED, false)
        prefs.setLong(Prefs.ADULT_VERIFIED_AT, 0L)
    }
}
