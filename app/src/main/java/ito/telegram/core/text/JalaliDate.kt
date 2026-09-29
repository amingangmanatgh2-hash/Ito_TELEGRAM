package ito.telegram.core.text

/**
 * تبدیل تقویم شمسی/میلادی — الگوریتم روزِ ژولینی، بدون هیچ کتابخانه‌ی بیرونی.
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {

    fun toGregorian(): IntArray = jdnToGregorian(jalaliToJdn(year, month, day))

    companion object {

        /** لنگرِ کالیبره‌شده: ۱۳۵۸/۰۱/۰۱ = ۱۹۷۹-۰۳-۲۱ */
        private const val JALALI_ANCHOR = 2305527

        private fun div(a: Int, b: Int): Int = Math.floorDiv(a, b)

        fun gregorianToJdn(y: Int, m: Int, d: Int): Int {
            val a = div(14 - m, 12)
            val y2 = y + 4800 - a
            val m2 = m + 12 * a - 3
            return d + div(153 * m2 + 2, 5) + 365 * y2 + div(y2, 4) - div(y2, 100) + div(y2, 400) - 32045
        }

        fun jdnToGregorian(jdn: Int): IntArray {
            var a = jdn + 32044
            val b = div(4 * a + 3, 146097)
            a -= div(146097 * b, 4)
            val c = div(4 * a + 3, 1461)
            a -= div(1461 * c, 4)
            val e = div(5 * a + 2, 153)
            val day = a - div(153 * e + 2, 5) + 1
            val month = e + 3 - 12 * div(e, 10)
            val year = 100 * b + c - 4800 + div(e, 10)
            return intArrayOf(year, month, day)
        }

        private fun jalaliEpochBase(jy: Int): Int {
            val jy2 = jy - 979
            return 365 * jy2 + div(jy2, 33) * 8 + div(jy2 % 33 + 3, 4)
        }

        fun jalaliToJdn(jy: Int, jm: Int, jd: Int): Int {
            val days = jalaliEpochBase(jy) +
                (if (jm <= 7) (jm - 1) * 31 else (jm - 7) * 30 + 186) + jd - 1
            return days + JALALI_ANCHOR
        }

        fun fromGregorian(gy: Int, gm: Int, gd: Int): JalaliDate {
            val jdn = gregorianToJdn(gy, gm, gd)
            var days = jdn - JALALI_ANCHOR
            var jy = 979 + 33 * div(days, 12053)
            days %= 12053
            jy += 4 * div(days, 1461)
            days %= 1461
            if (days > 365) {
                jy += div(days - 1, 365)
                days = (days - 1) % 365
            }
            val jm: Int
            val jd: Int
            if (days < 186) {
                jm = 1 + div(days, 31)
                jd = 1 + days % 31
            } else {
                jm = 7 + div(days - 186, 30)
                jd = 1 + (days - 186) % 30
            }
            return JalaliDate(jy, jm, jd)
        }
    }
}
