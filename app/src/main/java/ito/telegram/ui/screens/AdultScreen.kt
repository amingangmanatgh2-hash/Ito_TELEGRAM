package ito.telegram.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ito.telegram.adult.AgeGate
import ito.telegram.core.FeatureRegistry
import ito.telegram.core.Lang
import ito.telegram.core.Prefs
import ito.telegram.core.Register
import ito.telegram.ui.SectionCard
import ito.telegram.ui.StatRow
import ito.telegram.ui.faNum
import ito.telegram.ui.prefsRevision
import ito.telegram.ui.rememberPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdultScreen(nav: NavController) {
    val prefs = rememberPrefs()
    val rev by prefsRevision(prefs)
    val unlocked = remember(rev) { AgeGate.unlocked(prefs) }
    val adultFeatures = remember { FeatureRegistry.features.filter { it.adult } }
    var pin by remember { mutableStateOf(prefs.getString(Prefs.ADULT_PIN)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        SectionCard(
            title = "بخش بزرگسال (+۱۸)",
            subtitle = "این بخش محتوایی تولید نمی‌کند؛ دسترسی، فیلتر و لحن را مدیریت می‌کند.",
        ) {
            StatRow("وضعیت", if (unlocked) "باز ✔" else "قفل 🔒")
            if (unlocked) {
                StatRow("سن تخمینی ثبت‌شده", faNum(prefs.getInt(Prefs.ADULT_VERIFIED_AGE)) + " سال")
                StatRow("زمان تأیید", stamp(prefs.getLong(Prefs.ADULT_VERIFIED_AT)))
            }
            Spacer(Modifier.height(10.dp))
            if (!unlocked) {
                Text(Lang.t("adult_locked"), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { nav.navigate("agescan") }, modifier = Modifier.fillMaxWidth()) {
                    Text("شروع تأیید سن با دوربین")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { AgeGate.lock(prefs) }) { Text("قفل کن") }
                    OutlinedButton(onClick = { nav.navigate("agescan") }) { Text("تأیید دوباره") }
                }
            }
        }

        SectionCard(
            title = "قوانین این گیت",
            subtitle = "چون سخت‌گیر انتخاب شد، دقیقاً این‌ها اجرا می‌شود:",
        ) {
            Text(
                "• حداقل ${faNum(AgeGate.MIN_SAMPLES)} نمونه‌ی سن از فریم‌های مختلف\n" +
                    "• حداقل ${faNum(AgeGate.REQUIRED_CHALLENGES)} تست زنده‌بودن (پلک / چرخش سر / لبخند)\n" +
                    "• میانه‌ی تخمین‌ها باید از ۱۸ + حاشیه‌ی اطمینان بیشتر باشد\n" +
                    "• اگر تخمین‌ها پراکنده باشند یا چند چهره در کادر باشد ⇐ رد\n" +
                    "• اگر مدل بارگذاری نشود ⇐ رد (بدون استثنا)\n" +
                    "• نتیجه فقط روی همین دستگاه ذخیره می‌شود",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "و یک اعتراف: تخمین سن از چهره خطا دارد. این یک سدِ منطقی است، نه کارت شناسایی.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (unlocked) {
            SectionCard(title = "زبان بی‌پرده", subtitle = "لحن بزرگسالانه‌ی رابط کاربری") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("فعال‌سازی رجیستر +۱۸", Modifier.weight(1f))
                    Switch(
                        checked = Lang.register == Register.ADULT,
                        onCheckedChange = { on ->
                            Lang.register = if (on) Register.ADULT else Register.CASUAL
                            prefs.setString(Prefs.LANG_REGISTER, Lang.keyOf(Lang.register))
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("نمونه: " + Lang.t("empty_chats"), style = MaterialTheme.typography.bodySmall)
            }

            SectionCard(title = "رمز جداگانه", subtitle = "علاوه بر چهره، یک رمز هم بگذار") {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("رمز عددی (خالی = بدون رمز)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { prefs.setString(Prefs.ADULT_PIN, pin) }) { Text("ذخیره‌ی رمز") }
            }

            SectionCard(
                title = "قابلیت‌های این بخش",
                subtitle = "${faNum(adultFeatures.size)} آیتم، همه پشت همین گیت",
            ) {
                adultFeatures.forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { prefs.setFeature(f.id, !prefs.isFeatureOn(f.id)) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                f.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prefs.isFeatureOn(f.id),
                            onCheckedChange = { prefs.setFeature(f.id, it) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

private fun stamp(ms: Long): String = if (ms <= 0) "—" else
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(ms))
