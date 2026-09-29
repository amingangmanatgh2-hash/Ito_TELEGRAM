package ito.telegram.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ito.telegram.core.text.TextLab
import ito.telegram.core.text.TextPipeline
import ito.telegram.tg.TelegramRepo
import ito.telegram.ui.rememberPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(nav: NavController, chatId: Long) {
    val prefs = rememberPrefs()
    val gateway by TelegramRepo.gateway.collectAsState()
    val messages by gateway.messages.collectAsState()
    val chats by gateway.chats.collectAsState()
    val title = chats.firstOrNull { it.id == chatId }?.title ?: "گفت‌وگو"
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatId) { gateway.openChat(chatId) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        val desc = TextPipeline.describe(prefs, TextPipeline.SCOPE_OUT)
                        Text(
                            "خروجی: $desc",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { gateway.closeChat(); nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        // اعمال دستی پایپ‌لاین روی پیش‌نویس تا قبل از ارسال ببینی چه می‌شود
                        draft = TextPipeline.run(prefs, TextPipeline.SCOPE_OUT, draft)
                    }) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = "اعمال ابزارها روی پیش‌نویس")
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("پیام…") },
                        maxLines = 4,
                        keyboardActions = KeyboardActions.Default,
                    )
                    IconButton(
                        onClick = {
                            val text = expandSnippets(draft)
                            if (text.isNotBlank()) {
                                TelegramRepo.sendProcessed(chatId, text)
                                draft = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ارسال")
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(inner),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                val shown = if (msg.outgoing) msg.text else TelegramRepo.renderIncoming(msg.text)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.outgoing) Arrangement.Start else Arrangement.End,
                ) {
                    Surface(
                        color = if (msg.outgoing) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                shown,
                                color = if (msg.outgoing) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                clock(msg.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (msg.outgoing) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("پیامی نیست. اولین حرف را تو بزن.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun clock(epochSeconds: Int): String = try {
    SimpleDateFormat("HH:mm", Locale.US).format(Date(epochSeconds * 1000L))
} catch (e: Exception) {
    ""
}

/** جایگزینی /shortcut با متن پاسخ آماده — موتور «میان‌برهای متن». */
private fun expandSnippets(text: String): String {
    if (!text.startsWith("/")) return text
    val key = text.drop(1).substringBefore(' ')
    val rest = text.drop(1).substringAfter(' ', "")
    val f = ito.telegram.core.FeatureRegistry.features.firstOrNull {
        it.payload?.optString("kind") == "snippet" && it.payload.optString("short") == key
    }
    val snippet = f?.payload?.optString("text")
    if (!snippet.isNullOrBlank()) return (snippet + " " + rest).trim()
    // دستورهای سریع متنی
    return when (key) {
        "shrug" -> "¯\\_(ツ)_/¯"
        "flip" -> "(╯°□°)╯︵ ┻━┻"
        "roll" -> "🎲 " + (1..6).random()
        "coin" -> if (Math.random() < 0.5) "شیر" else "خط"
        "time" -> SimpleDateFormat("HH:mm", Locale.US).format(Date())
        "rev" -> rest.reversed()
        "b64" -> TextLab.byId["base64"]?.fn?.invoke(rest) ?: rest
        "morse" -> TextLab.byId["morse"]?.fn?.invoke(rest) ?: rest
        "nim" -> TextLab.persianSpacing(rest)
        "calc" -> TextLab.trimNum(TextLab.evalArithmetic(rest))
        else -> text
    }
}
