package ito.telegram

import ito.telegram.core.text.TextLab
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * تستِ صداقتِ کاتالوگ: اگر کسی (از جمله خودم) بخواهد آیتم الکی اضافه کند،
 * این تست‌ها جلویش را می‌گیرند.
 */
class CatalogTest {

    private val doc: JSONObject by lazy {
        val candidates = listOf(
            File("src/main/assets/features.json"),
            File("app/src/main/assets/features.json"),
        )
        val f = candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("features.json پیدا نشد؛ اول tools/gen_features.py را اجرا کن")
        JSONObject(f.readText())
    }

    @Test
    fun `catalog has at least two thousand items`() {
        val total = doc.getJSONArray("features").length()
        assertTrue("تعداد قابلیت‌ها کم است: $total", total >= 2000)
        assertEquals(total, doc.getInt("total"))
    }

    @Test
    fun `ids are unique and well formed`() {
        val arr = doc.getJSONArray("features")
        val ids = HashSet<String>()
        for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).getString("id")
            assertTrue("شناسه‌ی نامعتبر: $id", Regex("[a-z0-9_.]+").matches(id))
            assertTrue("شناسه‌ی تکراری: $id", ids.add(id))
        }
    }

    @Test
    fun `titles are unique and non empty`() {
        val arr = doc.getJSONArray("features")
        val titles = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val title = o.getString("title")
            assertTrue("عنوان خالی برای ${o.getString("id")}", title.isNotBlank())
            assertTrue("عنوان تکراری: $title", titles.add(title))
        }
    }

    @Test
    fun `no colour variant padding`() {
        // خواسته‌ی صریح سفارش‌دهنده: نسخه‌ی رنگی به‌عنوان قابلیت جدا حساب نشود.
        val banned = listOf("رنگ صورتی", "رنگ آبی", "رنگ قرمز", "رنگ سبز", "رنگ زرد", "تم صورتی", "تم آبی")
        val arr = doc.getJSONArray("features")
        for (i in 0 until arr.length()) {
            val title = arr.getJSONObject(i).getString("title")
            for (b in banned) {
                assertTrue("آیتم پرکننده پیدا شد: $title", !title.contains(b))
            }
        }
    }

    @Test
    fun `every category is declared and used`() {
        val cats = doc.getJSONArray("categories")
        val declared = (0 until cats.length()).map { cats.getJSONObject(it).getString("id") }.toSet()
        val used = HashSet<String>()
        val arr = doc.getJSONArray("features")
        for (i in 0 until arr.length()) used.add(arr.getJSONObject(i).getString("cat"))
        assertTrue("دسته‌ی اعلام‌نشده: ${used - declared}", (used - declared).isEmpty())
        assertTrue("دسته‌ی بدون آیتم: ${declared - used}", (declared - used).isEmpty())
    }

    @Test
    fun `textlab combos point at real transforms`() {
        val arr = doc.getJSONArray("features")
        var combos = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val p = o.optJSONObject("payload") ?: continue
            if (p.optString("kind") != "textlab") continue
            combos++
            val tid = p.getString("transform")
            assertTrue("ابزار متنِ ناموجود در کاتالوگ: $tid", TextLab.byId.containsKey(tid))
        }
        assertEquals("هر ابزار باید دقیقاً چهار مسیر داشته باشد", TextLab.all.size * 4, combos)
    }

    @Test
    fun `adult items are flagged and gated`() {
        val arr = doc.getJSONArray("features")
        var adult = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optBoolean("adult")) {
                adult++
                assertTrue(
                    "آیتم بزرگسال باید در دسته‌ی adult یا lang باشد: ${o.getString("id")}",
                    o.getString("cat") in setOf("adult", "lang"),
                )
            }
        }
        assertTrue("بخش بزرگسال خالی است", adult > 20)
    }

    @Test
    fun `impl levels are known`() {
        val arr = doc.getJSONArray("features")
        for (i in 0 until arr.length()) {
            val impl = arr.getJSONObject(i).getString("impl")
            assertTrue("نوع پیاده‌سازی ناشناس: $impl", impl in setOf("core", "data", "combo"))
        }
    }

    @Test
    fun `data items carry real payload`() {
        val arr = doc.getJSONArray("features")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("impl") != "data") continue
            if (o.getString("cat") == "wordlist") continue // فهرست‌ها را کاربر پر می‌کند
            val p = o.optJSONObject("payload")
                ?: throw AssertionError("آیتم داده‌ای بدون محتوا: ${o.getString("id")}")
            assertTrue(
                "محتوای خالی برای ${o.getString("id")}",
                p.optString("text").isNotBlank(),
            )
        }
    }
}
