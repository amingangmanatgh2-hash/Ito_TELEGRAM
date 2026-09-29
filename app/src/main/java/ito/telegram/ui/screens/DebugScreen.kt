package ito.telegram.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ito.telegram.adult.AgeEstimator
import ito.telegram.core.FeatureRegistry
import ito.telegram.core.Prefs
import ito.telegram.core.text.TextLab
import ito.telegram.tg.Td
import ito.telegram.tg.TelegramRepo
import ito.telegram.ui.SectionCard
import ito.telegram.ui.StatRow
import ito.telegram.ui.faNum
import ito.telegram.ui.rememberPrefs
import androidx.compose.ui.platform.LocalContext

@Composable
fun DebugScreen() {
    val prefs = rememberPrefs()
    val ctx = LocalContext.current
    val gateway by TelegramRepo.gateway.collectAsState()
    val log by gateway.log.collectAsState()
    val crash = prefs.getString(Prefs.CRASH_LOG)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionCard(title = "چکاپ سلامت", subtitle = "یک نگاه، همه‌چیز") {
            StatRow("کلاس‌های TDLib", if (Td.available) "پیدا شد" else "نیست")
            StatRow("گیت‌وی فعال", gateway.kind)
            StatRow("خطای آخر TDLib", Td.lastError ?: "—")
            StatRow("کاتالوگ", faNum(FeatureRegistry.total) + " آیتم")
            StatRow("خطای کاتالوگ", FeatureRegistry.loadError ?: "—")
            StatRow("ابزار متن", faNum(TextLab.all.size))
            StatRow("قابلیت‌های روشن", faNum(prefs.enabledCount()))
        }

        SectionCard(title = "مدل تخمین سن") {
            val probe = remember0(ctx)
            StatRow("بارگذاری مدل", if (probe.first) "موفق" else "ناموفق")
            StatRow("جزئیات", probe.second ?: "—")
        }

        SectionCard(title = "کنسول هسته", subtitle = "آخرین رویدادها") {
            if (log.isEmpty()) {
                Text("هنوز چیزی ثبت نشده.", style = MaterialTheme.typography.bodySmall)
            } else {
                log.takeLast(60).forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        SectionCard(title = "آخرین کرش ثبت‌شده") {
            if (crash.isBlank()) {
                Text("هیچ کرشی ثبت نشده. فعلاً.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(crash.take(3000), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { prefs.setString(Prefs.CRASH_LOG, "") }) { Text("پاک‌کردن") }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** بارگذاری آزمایشی مدل، فقط برای نمایش وضعیت. */
@Composable
private fun remember0(ctx: android.content.Context): Pair<Boolean, String?> =
    androidx.compose.runtime.remember {
        val est = AgeEstimator(ctx)
        val res = est.ready to est.error
        est.close()
        res
    }
