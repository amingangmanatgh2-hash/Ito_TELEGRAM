package ito.telegram.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ito.telegram.core.text.TextLab
import ito.telegram.ui.faNum

@Composable
fun LabScreen() {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("سلام، من دارم می روم خانه. شماره ام 09121234567 است و 1500000 ریال بدهکارم.") }
    var chain by remember { mutableStateOf(listOf<String>()) }
    var query by remember { mutableStateOf("") }

    val output = remember(input, chain) { TextLab.apply(chain, input) }
    val list = remember(query) {
        TextLab.all.filter { query.isBlank() || it.title.contains(query, true) || it.desc.contains(query, true) }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(12.dp)) {
                Text("آزمایشگاه متن", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${faNum(TextLab.all.size)} ابزار واقعی — بزن روی هرکدام تا به زنجیره اضافه شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
                    label = { Text("ورودی") },
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 160.dp),
                ) {
                    Column(Modifier.padding(10.dp).verticalScroll(rememberScrollState())) {
                        Text("خروجی", style = MaterialTheme.typography.labelSmall)
                        Text(output)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("ito", output))
                    }) { Text("کپی خروجی") }
                    OutlinedButton(onClick = { chain = emptyList() }) { Text("پاک‌کردن زنجیره") }
                    Text(
                        if (chain.isEmpty()) "زنجیره خالی است" else "زنجیره: " + chain.mapNotNull { TextLab.byId[it]?.title }.joinToString(" ← "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("جست‌وجوی ابزار") },
                    singleLine = true,
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { tr ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { chain = chain + tr.id }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(tr.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        tr.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}
