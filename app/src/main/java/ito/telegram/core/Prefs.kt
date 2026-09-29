package ito.telegram.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Small, dependency-free settings store.
 *
 * Every one of the 2000 catalogue entries is persisted here under its stable id,
 * so a toggle survives reboots, and the engines (text pipeline, rule engine,
 * privacy layer...) read their state from exactly this object.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("ito_prefs", Context.MODE_PRIVATE)

    private val bump = MutableStateFlow(0L)

    /** Increments on every write; Compose screens collect it to recompose. */
    val revision: StateFlow<Long> = bump

    private fun touch() {
        bump.value = bump.value + 1
    }

    fun getBool(key: String, def: Boolean = false): Boolean = sp.getBoolean(key, def)

    fun setBool(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
        touch()
    }

    fun toggle(key: String, def: Boolean = false): Boolean {
        val next = !getBool(key, def)
        setBool(key, next)
        return next
    }

    fun getString(key: String, def: String = ""): String = sp.getString(key, def) ?: def

    fun setString(key: String, value: String) {
        sp.edit().putString(key, value).apply()
        touch()
    }

    fun getInt(key: String, def: Int = 0): Int = sp.getInt(key, def)

    fun setInt(key: String, value: Int) {
        sp.edit().putInt(key, value).apply()
        touch()
    }

    fun getLong(key: String, def: Long = 0L): Long = sp.getLong(key, def)

    fun setLong(key: String, value: Long) {
        sp.edit().putLong(key, value).apply()
        touch()
    }

    fun getStringSet(key: String): Set<String> = sp.getStringSet(key, emptySet()) ?: emptySet()

    fun setStringSet(key: String, value: Set<String>) {
        sp.edit().putStringSet(key, value).apply()
        touch()
    }

    fun addToSet(key: String, item: String) = setStringSet(key, getStringSet(key) + item)

    fun removeFromSet(key: String, item: String) = setStringSet(key, getStringSet(key) - item)

    fun enabledFeatures(): Set<String> = getStringSet(KEY_ENABLED)

    fun isFeatureOn(id: String): Boolean = getBool("f_$id", false)

    fun setFeature(id: String, on: Boolean) {
        sp.edit().putBoolean("f_$id", on).apply()
        val set = getStringSet(KEY_ENABLED).toMutableSet()
        if (on) set.add(id) else set.remove(id)
        sp.edit().putStringSet(KEY_ENABLED, set).apply()
        touch()
    }

    fun enabledCount(): Int = getStringSet(KEY_ENABLED).size

    fun clearAllFeatures() {
        val set = getStringSet(KEY_ENABLED)
        val e = sp.edit()
        set.forEach { e.remove("f_$it") }
        e.putStringSet(KEY_ENABLED, emptySet())
        e.apply()
        touch()
    }

    companion object {
        const val KEY_ENABLED = "enabled_features"

        // --- well known keys ------------------------------------------------
        const val API_ID = "tg_api_id"
        const val API_HASH = "tg_api_hash"
        const val LANG_REGISTER = "lang_register"      // polite | casual | adult
        const val ADULT_UNLOCKED = "adult_unlocked"
        const val ADULT_VERIFIED_AGE = "adult_verified_age"
        const val ADULT_VERIFIED_AT = "adult_verified_at"
        const val ADULT_PIN = "adult_pin"
        const val ADULT_PANIC = "adult_panic"
        const val ONBOARDED = "onboarded"
        const val CRASH_LOG = "last_crash"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
