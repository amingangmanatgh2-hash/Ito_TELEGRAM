package ito.telegram.core.text

import ito.telegram.core.FeatureRegistry
import ito.telegram.core.Prefs

/**
 * پایپ‌لاین متن — همان چیزی که آیتم‌های «ترکیبی» کاتالوگ را واقعی می‌کند.
 *
 * برای هر مسیر (out/in/tool/clip) فهرست ابزارهای روشن را می‌گیرد و پشت‌سرهم اجرا می‌کند.
 */
object TextPipeline {

    const val SCOPE_OUT = "out"
    const val SCOPE_IN = "in"
    const val SCOPE_TOOL = "tool"
    const val SCOPE_CLIP = "clip"

    fun activeTransforms(prefs: Prefs, scope: String): List<String> =
        FeatureRegistry.enabledWithPayload(prefs, "textlab")
            .filter { it.payload?.optString("scope") == scope }
            .mapNotNull { it.payload?.optString("transform") }

    fun run(prefs: Prefs, scope: String, input: String): String {
        val ids = activeTransforms(prefs, scope)
        if (ids.isEmpty()) return input
        return TextLab.apply(ids, input)
    }

    /** خلاصه‌ی خوانا برای نمایش در UI: «۳ ابزار روی پیام ارسالی فعال است». */
    fun describe(prefs: Prefs, scope: String): String {
        val ids = activeTransforms(prefs, scope)
        if (ids.isEmpty()) return "هیچ ابزاری فعال نیست"
        val names = ids.mapNotNull { TextLab.byId[it]?.title }
        return "${names.size} ابزار: " + names.take(4).joinToString("، ") +
            if (names.size > 4) " و ${names.size - 4} تای دیگر" else ""
    }
}
