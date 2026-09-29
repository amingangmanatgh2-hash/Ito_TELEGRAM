package ito.telegram

import ito.telegram.core.text.JalaliDate
import ito.telegram.core.text.TextLab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLabTest {

    @Test
    fun `every transform has a unique id and survives weird input`() {
        val ids = TextLab.all.map { it.id }
        assertEquals("شناسه‌ی تکراری در ابزارهای متن", ids.size, ids.toSet().size)

        val nasty = listOf(
            "", " ", "\n\n\n", "سلام", "salam", "1234567890", "۱۲۳۴",
            "https://x.com/a?utm_source=t&fbclid=1", "a,b,c", "{\"a\":1}",
            "🙂🙃🙂", "\u200C\u200F", "0912-123-4567", "IR" + "1".repeat(24),
            "2026-09-29", "۱۴۰۵/۰۷/۰۷", "۲۰٪ از ۵۰۰", "2+3*4", "a".repeat(2000),
        )
        for (tr in TextLab.all) {
            for (input in nasty) {
                val out = try {
                    tr.fn(input)
                } catch (e: Throwable) {
                    throw AssertionError("ابزار ${tr.id} روی ورودی «$input» ترکید: $e", e)
                }
                assertNotNull("ابزار ${tr.id} خروجی null داد", out)
            }
        }
    }

    @Test
    fun `persian spacing fixes mi prefix`() {
        assertEquals("می‌رود", TextLab.persianSpacing("می رود"))
        assertTrue(TextLab.persianSpacing("کتاب ها").contains("\u200C"))
    }

    @Test
    fun `base64 round trip`() {
        val b64 = TextLab.byId["base64"]!!.fn
        val dec = TextLab.byId["base64_dec"]!!.fn
        for (s in listOf("hello", "سلام دنیا", "a", "ab", "abc", "۱۲۳")) {
            assertEquals(s, dec(b64(s)))
        }
    }

    @Test
    fun `morse round trip`() {
        val enc = TextLab.byId["morse"]!!.fn
        val dec = TextLab.byId["morse_dec"]!!.fn
        assertEquals("sos", dec(enc("sos")))
    }

    @Test
    fun `digit conversions`() {
        assertEquals("۱۲۳", TextLab.byId["fa_digits"]!!.fn("123"))
        assertEquals("123", TextLab.byId["en_digits"]!!.fn("۱۲۳"))
    }

    @Test
    fun `arithmetic evaluator respects precedence`() {
        assertEquals(14.0, TextLab.evalArithmetic("2+3*4"), 0.001)
        assertEquals(2.0, TextLab.evalArithmetic("8/4"), 0.001)
        assertEquals(0.0, TextLab.evalArithmetic("8/0"), 0.001)
    }

    @Test
    fun `numbers to persian words`() {
        assertEquals("صفر", TextLab.numToPersianWords(0))
        assertEquals("بیست و یک", TextLab.numToPersianWords(21))
        assertEquals("هزار و دویست", TextLab.numToPersianWords(1200))
    }

    @Test
    fun `card and phone masking hides the middle`() {
        val masked = TextLab.byId["mask_cards"]!!.fn("6037991234567890")
        assertTrue(masked.contains("****"))
        assertTrue(!masked.contains("9912"))
        val phone = TextLab.byId["mask_phones"]!!.fn("09121234567")
        assertTrue(phone.contains("***"))
    }

    @Test
    fun `tracker params are stripped`() {
        val out = TextLab.byId["strip_utm"]!!.fn("https://x.com/a?utm_source=tg&id=5&fbclid=9")
        assertTrue(!out.contains("utm_source"))
        assertTrue(!out.contains("fbclid"))
        assertTrue(out.contains("id=5"))
    }

    @Test
    fun `jalali conversion matches known dates`() {
        val j = JalaliDate.fromGregorian(2026, 9, 29)
        assertEquals(1405, j.year)
        assertEquals(7, j.month)
        assertEquals(7, j.day)

        val g = JalaliDate(1405, 1, 1).toGregorian()
        assertEquals(2026, g[0])
        assertEquals(3, g[1])
        assertEquals(21, g[2])

        // رفت و برگشت روی چند سال
        for (year in 1390..1410) {
            for (month in listOf(1, 6, 7, 12)) {
                val gg = JalaliDate(year, month, 10).toGregorian()
                val back = JalaliDate.fromGregorian(gg[0], gg[1], gg[2])
                assertEquals(year, back.year)
                assertEquals(month, back.month)
                assertEquals(10, back.day)
            }
        }
    }

    @Test
    fun `finglish converter produces persian letters`() {
        val out = TextLab.finglishToPersian("salam")
        assertTrue("خروجی: $out", out.any { it in 'آ'..'ی' })
    }

    @Test
    fun `chained transforms do not throw`() {
        val chain = TextLab.all.map { it.id }
        val out = TextLab.apply(chain, "سلام، این یک تست است. 1234")
        assertNotNull(out)
    }
}
