package ito.telegram

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import ito.telegram.core.FeatureRegistry
import ito.telegram.core.Lang
import ito.telegram.core.Prefs
import ito.telegram.tg.TelegramRepo
import java.io.PrintWriter
import java.io.StringWriter

class ItoApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val prefs = Prefs.get(this)

        // «سپر ضد کرش»: هر استثنای مدیریت‌نشده ثبت می‌شود تا در صفحه‌ی اشکال‌زدایی
        // دیده شود. رفتار پیش‌فرض سیستم هم اجرا می‌شود، یعنی چیزی را پنهان نمی‌کنیم.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                prefs.setString(
                    Prefs.CRASH_LOG,
                    "زمان: ${System.currentTimeMillis()}\nنخ: ${thread.name}\n$sw"
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }

        FeatureRegistry.load(this)
        Lang.register = Lang.fromKey(prefs.getString(Prefs.LANG_REGISTER, "casual"))
        TelegramRepo.init(this)
    }

    companion object {
        lateinit var instance: ItoApp
            private set
    }
}
