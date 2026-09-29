package ito.telegram.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ito.telegram.core.Lang
import ito.telegram.tg.AuthState
import ito.telegram.tg.TelegramRepo
import ito.telegram.ui.SectionCard

@Composable
fun LoginScreen(nav: NavController) {
    val gateway by TelegramRepo.gateway.collectAsState()
    val auth by gateway.authState.collectAsState()
    var phone by remember { mutableStateOf("+98") }
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionCard(title = Lang.t("login_title"), subtitle = Lang.t("login_hint")) {
            when (val a = auth) {
                is AuthState.Offline -> {
                    Text(
                        "هسته در حالت آفلاین است: ${a.reason}\nاول در تنظیمات api_id و api_hash را وارد کن.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { nav.navigate("settings") }) { Text("رفتن به تنظیمات") }
                }
                AuthState.WaitPhone, AuthState.Booting -> {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("شماره با کد کشور") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { gateway.submitPhone(phone.trim()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("ارسال کد")
                    }
                }
                AuthState.WaitCode -> {
                    Text(Lang.t("code_hint"), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter { c -> c.isDigit() } },
                        label = { Text("کد تأیید") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { gateway.submitCode(code.trim()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("تأیید کد")
                    }
                }
                AuthState.WaitPassword -> {
                    Text("رمز تأیید دومرحله‌ای را بزن.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("رمز دومرحله‌ای") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { gateway.submitPassword(pass) }, modifier = Modifier.fillMaxWidth()) {
                        Text("ورود")
                    }
                }
                AuthState.Ready -> {
                    Text("وارد شده‌ای. برو سراغ چت‌ها.", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { nav.navigate("chats") }) { Text("چت‌ها") }
                }
                AuthState.LoggedOut -> {
                    Text("از حساب خارج شدی.")
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { TelegramRepo.restart() }) { Text("اتصال دوباره") }
                }
                is AuthState.Failed -> {
                    Text("خطا: ${a.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { TelegramRepo.restart() }) { Text("از اول") }
                }
            }
        }

        SectionCard(title = "چرا شماره‌ی من امن است؟") {
            Text(
                "ورود مستقیماً با کتابخانه‌ی رسمی TDLib و سرورهای خود تلگرام انجام می‌شود. " +
                    "این برنامه سروری ندارد که چیزی برایش بفرستد؛ شماره، کد و رمز از دستگاه بیرون نمی‌رود مگر به سمت تلگرام.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
