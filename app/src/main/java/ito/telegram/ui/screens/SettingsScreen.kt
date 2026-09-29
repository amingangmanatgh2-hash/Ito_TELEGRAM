package ito.telegram.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ito.telegram.adult.AgeGate
import ito.telegram.core.FeatureRegistry
import ito.telegram.core.Lang
import ito.telegram.core.Prefs
import ito.telegram.core.Register
import ito.telegram.core.text.TextLab
import ito.telegram.core.text.TextPipeline
import ito.telegram.tg.Td
import ito.telegram.tg.TelegramRepo
import ito.telegram.ui.SectionCard
import ito.telegram.ui.StatRow
import ito.telegram.ui.faNum
import ito.telegram.ui.prefsRevision
import ito.telegram.ui.rememberPrefs

@Composable
fun SettingsScreen(nav: NavController) {
    val prefs = rememberPrefs()
    val rev by prefsRevision(prefs)
    var apiId by remember { mutableStateOf(prefs.getString(Prefs.API_ID)) }
    var apiHash by remember { mutableStateOf(prefs.getString(Prefs.API_HASH)) }
    var saved by remember { mutableStateOf(false) }
    val adultOk = remember(rev) { AgeGate.unlocked(prefs) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        SectionCard(
            title = "اتصال به تلگرام",
            subtitle = "api_id و api_hash را از my.telegram.org بگیر. فقط روی همین دستگاه ذخیره می‌شود.",
        ) {
            OutlinedTextField(
                value = apiId,
                onValueChange = { apiId = it.filter { c -> c.isDigit() } },
                label = { Text("api_id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiHash,
                onValueChange = { apiHash = it.trim() },
                label = { Text("api_hash") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    prefs.setString(Prefs.API_ID, apiId)
                    prefs.setString(Prefs.API_HASH, apiHash)
                    TelegramRepo.restart()
                    saved = true
                }) { Text("ذخیره و اتصال مجدد") }
                OutlinedButton(onClick = { nav.navigate("login") }) { Text("ورود با شماره") }
            }
            if (saved) {
                Spacer(Modifier.height(6.dp))
                Text(Lang.t("saved"), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            StatRow("هسته‌ی نیتیو TDLib", if (Td.available) "موجود ✔" else "موجود نیست ✘")
            StatRow("حالت فعلی", if (TelegramRepo.current.kind == "tdlib") "واقعی" else "نمایشی")
        }

        SectionCard(
            title = "زبان و لحن برنامه",
            subtitle = "کلِ متن‌های اپ از همین جا عوض می‌شود؛ شوخی نیست، جدول رشته‌ها سه‌ستونه است.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Register.POLITE, Register.CASUAL, Register.ADULT).forEach { r ->
                    val locked = r == Register.ADULT && !adultOk
                    FilterChip(
                        selected = Lang.register == r,
                        enabled = !locked,
                        onClick = {
                            Lang.register = r
                            prefs.setString(Prefs.LANG_REGISTER, Lang.keyOf(r))
                        },
                        label = { Text(Lang.title(r) + if (locked) " 🔒" else "") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("نمونه: " + Lang.t("empty_chats"), style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(title = "پایپ‌لاین متن", subtitle = "چه ابزارهایی روی کدام مسیر فعال‌اند") {
            StatRow("روی پیام ارسالی", TextPipeline.describe(prefs, TextPipeline.SCOPE_OUT))
            StatRow("روی پیام دریافتی", TextPipeline.describe(prefs, TextPipeline.SCOPE_IN))
            StatRow("ابزار سریع", TextPipeline.describe(prefs, TextPipeline.SCOPE_TOOL))
            StatRow("کلیپ‌بورد", TextPipeline.describe(prefs, TextPipeline.SCOPE_CLIP))
        }

        SectionCard(title = "وضعیت کاتالوگ") {
            StatRow("کل قابلیت‌ها", faNum(FeatureRegistry.total))
            StatRow("روشن", faNum(prefs.enabledCount()))
            StatRow("ابزارهای متن", faNum(TextLab.all.size))
            if (FeatureRegistry.loadError != null) {
                StatRow("خطای بارگذاری کاتالوگ", FeatureRegistry.loadError ?: "")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { prefs.clearAllFeatures() }) { Text("خاموش‌کردن همه") }
                OutlinedButton(onClick = { nav.navigate("honesty") }) { Text("صفحه‌ی صداقت") }
            }
        }

        SectionCard(title = "اشکال‌زدایی", subtitle = "چون قرار شد چیزی زیر فرش نرود") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.navigate("debug") }) { Text("کنسول و آخرین کرش") }
                OutlinedButton(onClick = { TelegramRepo.restart() }) { Text("راه‌اندازی مجدد هسته") }
            }
        }

        SectionCard(title = "درباره") {
            Text(
                "ایتو — کلاینت تلگرام با ${faNum(FeatureRegistry.total)} قابلیت. " +
                    "ساخته‌شده روی TDLib رسمی؛ بدون تله‌متری، بدون ارسال هیچ داده‌ای به سرور ما (اصلاً سروری نداریم).",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "مدل تخمین سن: Age-Gender_Estimation_TF-Android (MIT) — اجرا فقط روی دستگاه.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}
