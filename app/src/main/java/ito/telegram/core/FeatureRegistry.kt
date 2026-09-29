package ito.telegram.core

import android.content.Context
import org.json.JSONObject

data class FeatureCategory(val id: String, val title: String, val desc: String)

data class Feature(
    val id: String,
    val title: String,
    val desc: String,
    val cat: String,
    val impl: String,      // core | data | combo
    val adult: Boolean,
    val payload: JSONObject?,
) {
    val implTitle: String
        get() = when (impl) {
            "core" -> "موتور اختصاصی"
            "data" -> "محتوای واقعی"
            else -> "ترکیب دو موتور"
        }
}

/**
 * کاتالوگ قابلیت‌ها؛ از assets/features.json خوانده می‌شود (خروجیِ tools/gen_features.py).
 *
 * اگر فایل به هر دلیلی نبود، اپ کرش نمی‌کند: کاتالوگ خالی می‌ماند و بقیه‌ی برنامه
 * سر جایش کار می‌کند.
 */
object FeatureRegistry {

    @Volatile
    private var loaded = false

    var categories: List<FeatureCategory> = emptyList()
        private set

    var features: List<Feature> = emptyList()
        private set

    var byId: Map<String, Feature> = emptyMap()
        private set

    var loadError: String? = null
        private set

    val total: Int get() = features.size

    fun countByImpl(impl: String): Int = features.count { it.impl == impl }

    fun byCategory(cat: String): List<Feature> = features.filter { it.cat == cat }

    fun countIn(cat: String): Int = features.count { it.cat == cat }

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        try {
            val raw = context.assets.open("features.json").bufferedReader().use { it.readText() }
            val root = JSONObject(raw)
            val cats = root.getJSONArray("categories")
            categories = (0 until cats.length()).map { i ->
                val o = cats.getJSONObject(i)
                FeatureCategory(o.getString("id"), o.getString("title"), o.optString("desc"))
            }
            val arr = root.getJSONArray("features")
            val list = ArrayList<Feature>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += Feature(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    desc = o.optString("desc"),
                    cat = o.getString("cat"),
                    impl = o.optString("impl", "core"),
                    adult = o.optBoolean("adult", false),
                    payload = o.optJSONObject("payload"),
                )
            }
            features = list
            byId = list.associateBy { it.id }
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: e.javaClass.simpleName
            categories = emptyList()
            features = emptyList()
            byId = emptyMap()
        }
        loaded = true
    }

    /** همه‌ی قابلیت‌های روشنِ یک نوع payload — موتورها از همین می‌خوانند. */
    fun enabledWithPayload(prefs: Prefs, kind: String): List<Feature> =
        features.filter {
            it.payload?.optString("kind") == kind && prefs.isFeatureOn(it.id)
        }

    fun search(query: String, cat: String?, onlyEnabled: Boolean, prefs: Prefs): List<Feature> {
        val q = query.trim()
        return features.asSequence()
            .filter { cat == null || it.cat == cat }
            .filter { !onlyEnabled || prefs.isFeatureOn(it.id) }
            .filter {
                q.isEmpty() || it.title.contains(q, true) || it.desc.contains(q, true) || it.id.contains(q, true)
            }
            .toList()
    }
}
