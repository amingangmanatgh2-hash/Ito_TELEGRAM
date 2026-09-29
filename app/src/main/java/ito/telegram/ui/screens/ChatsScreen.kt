package ito.telegram.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ito.telegram.core.FeatureRegistry
import ito.telegram.core.Lang
import ito.telegram.tg.AuthState
import ito.telegram.tg.TelegramRepo
import ito.telegram.ui.faNum
import ito.telegram.ui.rememberPrefs

@Composable
fun ChatsScreen(nav: NavController) {
    val prefs = rememberPrefs()
    val gateway by TelegramRepo.gateway.collectAsState()
    val auth by gateway.authState.collectAsState()
    val chats by gateway.chats.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("ایتو", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    Lang.t("app_tagline") + " · " + faNum(FeatureRegistry.total) + " قابلیت",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val a = auth) {
            is AuthState.Offline -> Banner(
                title = Lang.t("offline_banner"),
                body = a.reason,
                action = "تنظیم کلیدها" to { nav.navigate("settings") },
            )
            is AuthState.Failed -> Banner("خطای ورود", a.message, "تلاش دوباره" to { nav.navigate("login") })
            AuthState.WaitPhone, AuthState.WaitCode, AuthState.WaitPassword ->
                Banner("ورود ناتمام", "برای دیدن چت‌های واقعی باید وارد شوی.", "ادامه‌ی ورود" to { nav.navigate("login") })
            AuthState.Booting -> Banner("در حال اتصال…", "هسته دارد بالا می‌آید.", null)
            else -> Unit
        }

        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Lang.t("empty_chats"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(chats, key = { it.id }) { chat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { nav.navigate("chat/${chat.id}") }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(chat.title.take(1), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chat.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                TelegramRepo.renderIncoming(chat.lastMessage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (chat.unread > 0) {
                            Badge { Text(faNum(chat.unread)) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Banner(title: String, body: String, action: Pair<String, () -> Unit>?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall)
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = action.second) { Text(action.first) }
                }
            }
        }
    }
}
