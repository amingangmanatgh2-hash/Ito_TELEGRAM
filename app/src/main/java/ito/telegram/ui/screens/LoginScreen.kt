package ito.telegram.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import ito.telegram.core.CodeCatcher
import ito.telegram.core.Lang
import ito.telegram.core.Prefs
import ito.telegram.tg.ApiKeys
import ito.telegram.tg.AuthState
import ito.telegram.tg.TelegramRepo
import ito.telegram.ui.SectionCard
import kotlinx.coroutines.delay

/**
 * ورود در دو قدم و بس: شماره، بعد کد.
 *
 * کلید API داخل خودِ برنامه است، پس کاربر هیچ‌جا نمی‌رود. کد را هم اگر کپی کند
 * (یا پیامک بیاید) خودمان برمی‌داریم و می‌زنیم؛ دست به کیبورد نمی‌شود.
 */
@Composable
fun LoginScreen(nav: NavController) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val gateway by TelegramRepo.gateway.collectAsState()
    val auth by gateway.authState.collectAsState()

    var phone by remember { mutableStateOf(prefs.getString(Prefs.LAST_PHONE, "+98")) }
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var autoCode by remember { mutableStateOf(prefs.getBool(Prefs.AUTO_CODE, true)) }
    var showAdvanced by remember { mutableStateOf(false) }
    var ownId by remember { mutableStateOf(prefs.getString(Prefs.API_ID)) }
    var ownHash by remember { mutableStateOf(prefs.getString(Prefs.API_HASH)) }

    val caught by CodeCatcher.found.collectAsState()
    val caughtFrom by CodeCatcher.source.collectAsState()
    var autoNote by remember { mutableStateOf("") }

    val smsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) CodeCatcher.startSmsWatch(context) }

    // وقتی به صفحه برمی‌گردیم (مثلاً بعد از کپی‌کردن کد) کلیپ‌بورد را نگاه می‌کنیم.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, autoCode) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && autoCode) CodeCatcher.pollClipboard(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // تا وقتی منتظر کدیم، هر ثانیه یک نگاه به کلیپ‌بورد.
    LaunchedEffect(auth, autoCode) {
        if (auth != AuthState.WaitCode || !autoCode) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            CodeCatcher.startSmsWatch(context)
        }
        while (true) {
            CodeCatcher.pollClipboard(context)
            delay(900)
        }
    }

    // کد پیدا شد: بگذار توی کادر و خودت بزن.
    LaunchedEffect(caught, auth) {
        val c = caught
        if (c != null && auth == AuthState.WaitCode && autoCode) {
            code = c
            autoNote = "کد از $caughtFrom برداشته شد: $c"
            CodeCatcher.consume()
            delay(400)
            gateway.submitCode(c)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionCard(title = Lang.t("login_title"), subtitle = Lang.t("login_hint")) {
            when (val a = auth) {
                AuthState.Booting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(20.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("  در حال وصل‌شدن به تلگرام…")
                    }
                }

                is AuthState.Offline -> {
                    Text(
                        "هسته در حالت آفلاین است: ${a.reason}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { TelegramRepo.restart() }) { Text("تلاش دوباره") }
                }

                AuthState.WaitPhone -> {
                    Text(
                        "شماره‌ات را با کد کشور بزن. همین. نه کلید API می‌خواهد، نه ثبت‌نام جایی.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                        label = { Text("شماره با کد کشور") },
                        placeholder = { Text("+989121234567") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val p = phone.trim()
                            prefs.setString(Prefs.LAST_PHONE, p)
                            CodeCatcher.reset()
                            if (autoCode &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECEIVE_SMS,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                smsPermission.launch(Manifest.permission.RECEIVE_SMS)
                            }
                            gateway.submitPhone(p)
                        },
                        enabled = phone.count { it.isDigit() } >= 8,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("ارسال کد") }
                }

                AuthState.WaitCode -> {
                    Text(
                        "کد را تلگرام فرستاد. کافی است کپی‌اش کنی و برگردی — بقیه‌اش با من.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (autoCode) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            if (autoNote.isNotBlank()) autoNote else "منتظرِ کد… (کلیپ‌بورد و پیامک را می‌پایم)",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            code = it.filter { c -> c.isDigit() }.take(6)
                            if (code.length == 5) gateway.submitCode(code)
                        },
                        label = { Text("کد تأیید") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { gateway.submitCode(code.trim()) },
                            enabled = code.length >= 4,
                        ) { Text("تأیید کد") }
                        OutlinedButton(onClick = { CodeCatcher.pollClipboard(context) }) {
                            Text("از کلیپ‌بورد بردار")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = autoCode,
                            onCheckedChange = {
                                autoCode = it
                                prefs.setBool(Prefs.AUTO_CODE, it)
                            },
                        )
                        Text("  برداشتن خودکار کد", style = MaterialTheme.typography.bodySmall)
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
                    Button(
                        onClick = { gateway.submitPassword(pass) },
                        enabled = pass.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("ورود") }
                }

                AuthState.Ready -> {
                    Text("وارد شدی. برو سراغ چت‌ها.", fontWeight = FontWeight.Bold)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { TelegramRepo.restart() }) { Text("از اول") }
                        OutlinedButton(onClick = { showAdvanced = true }) { Text("کلید دستی") }
                    }
                }
            }
        }

        SectionCard(title = "کلید اتصال") {
            val key = remember(auth) { TelegramRepo.activeKey() }
            Text(
                "الان با کلیدِ «${key.label}» (api_id ${key.id}) وصل می‌شویم. " +
                    "این کلیدها در مخزن‌های متن‌بازِ خود تلگرام منتشر شده‌اند، برای همین " +
                    "لازم نیست کاربر چیزی بسازد. اگر تلگرام روی یکی گیر بدهد، برنامه " +
                    "خودش کلید بعدی را امتحان می‌کند.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "بستن تنظیمات کلید" else "کلید خودم را می‌خواهم بگذارم")
            }
            if (showAdvanced) {
                Text(
                    "اگر زیاد وارد و خارج می‌شوی، کلیدِ شخصی از my.telegram.org بگیر؛ " +
                        "سهمیه‌اش فقط مال خودت است.",
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = ownId,
                    onValueChange = { ownId = it.filter { c -> c.isDigit() } },
                    label = { Text("api_id") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = ownHash,
                    onValueChange = { ownHash = it.trim() },
                    label = { Text("api_hash") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        prefs.setString(Prefs.API_ID, ownId.trim())
                        prefs.setString(Prefs.API_HASH, ownHash.trim())
                        TelegramRepo.restart()
                    }) { Text("ذخیره و اتصال") }
                    OutlinedButton(onClick = {
                        ownId = ""
                        ownHash = ""
                        prefs.setString(Prefs.API_ID, "")
                        prefs.setString(Prefs.API_HASH, "")
                        ApiKeys.resetRotation(prefs)
                        TelegramRepo.restart()
                    }) { Text("برگرد به کلید داخلی") }
                }
            }
        }

        SectionCard(title = "چرا شماره‌ی من امن است؟") {
            Text(
                "ورود مستقیماً با کتابخانه‌ی رسمی TDLib و سرورهای خود تلگرام انجام می‌شود. " +
                    "این برنامه سروری ندارد که چیزی برایش بفرستد؛ شماره، کد و رمز از دستگاه " +
                    "بیرون نمی‌رود مگر به سمت تلگرام. کدی هم که از کلیپ‌بورد یا پیامک برداشته " +
                    "می‌شود، فقط داخل همین صفحه مصرف می‌شود و هیچ‌جا ذخیره نمی‌ماند.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
