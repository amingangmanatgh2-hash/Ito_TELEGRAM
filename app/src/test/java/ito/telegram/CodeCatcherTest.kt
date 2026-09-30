package ito.telegram

import ito.telegram.core.CodeCatcher
import ito.telegram.tg.ApiKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * برداشتن خودکار کد باید دقیق باشد: کدِ درست را بگیرد و شماره‌ی تلفن را
 * اشتباهی به‌جای کد نزند، وگرنه کاربر قفل می‌شود.
 */
class CodeCatcherTest {

    @Test
    fun `plain code is caught`() {
        assertEquals("12345", CodeCatcher.extract("12345"))
        assertEquals("123456", CodeCatcher.extract("123456"))
    }

    @Test
    fun `code inside a telegram sms is caught`() {
        assertEquals(
            "54321",
            CodeCatcher.extract("Telegram code 54321. Do not give this code to anyone."),
        )
        assertEquals(
            "24680",
            CodeCatcher.extract("کد ورود شما به تلگرام: 24680 — آن را به کسی ندهید"),
        )
    }

    @Test
    fun `persian digits are normalized`() {
        assertEquals("13579", CodeCatcher.extract("کد: ۱۳۵۷۹"))
    }

    @Test
    fun `phone numbers are not mistaken for a code`() {
        assertNull(CodeCatcher.extract("+989121234567"))
        assertNull(CodeCatcher.extract("+1 202 555 0143"))
    }

    @Test
    fun `junk returns nothing`() {
        assertNull(CodeCatcher.extract(""))
        assertNull(CodeCatcher.extract(null))
        assertNull(CodeCatcher.extract("سلام خوبی؟"))
        assertNull(CodeCatcher.extract("1234"))       // کوتاه‌تر از پنج رقم
        assertNull(CodeCatcher.extract("1234567"))    // هفت رقمِ چسبیده کد نیست
    }

    @Test
    fun `built in keys are usable and distinct`() {
        assertTrue("باید حداقل دو کلید پشتیبان باشد", ApiKeys.builtIn.size >= 2)
        val ids = ApiKeys.builtIn.map { it.id }
        assertEquals("کلید تکراری", ids.size, ids.toSet().size)
        for (k in ApiKeys.builtIn) {
            assertTrue("api_id نامعتبر: ${k.id}", k.id > 0)
            assertEquals("api_hash باید ۳۲ کاراکتر باشد: ${k.label}", 32, k.hash.length)
            assertTrue("api_hash باید هگز باشد", k.hash.all { it.isDigit() || it in 'a'..'f' })
            assertTrue("برچسب خالی", k.label.isNotBlank())
        }
    }

    @Test
    fun `api key errors are recognised`() {
        assertTrue(ApiKeys.isApiKeyProblem("API_ID_PUBLISHED_FLOOD"))
        assertTrue(ApiKeys.isApiKeyProblem("API_ID_INVALID"))
        assertTrue(ApiKeys.isApiKeyProblem("api_hash is invalid"))
        assertFalse(ApiKeys.isApiKeyProblem("PHONE_CODE_INVALID"))
        assertFalse(ApiKeys.isApiKeyProblem("FLOOD_WAIT_30"))
    }
}
