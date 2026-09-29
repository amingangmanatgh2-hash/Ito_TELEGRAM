package ito.telegram.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ito.telegram.adult.AgeGate
import ito.telegram.core.FeatureRegistry
import ito.telegram.core.Prefs
import ito.telegram.ui.faNum
import ito.telegram.ui.prefsRevision
import ito.telegram.ui.rememberPrefs

@Composable
fun FeaturesScreen(nav: NavController) {
    val prefs = rememberPrefs()
    val rev by prefsRevision(prefs)
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var onlyOn by remember { mutableStateOf(false) }

    val adultUnlocked = remember(rev) { AgeGate.unlocked(prefs) }
    val list = remember(query, cat, onlyOn, rev) {
        FeatureRegistry.search(query, cat, onlyOn, prefs)
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("کاتالوگ قابلیت‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${faNum(FeatureRegistry.total)} آیتم · ${faNum(prefs.enabledCount())} تا روشن",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { nav.navigate("honesty") }) { Text("صداقت") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("بگرد: مثلاً «مورس» یا «پروکسی»") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(selected = onlyOn, onClick = { onlyOn = !onlyOn }, label = { Text("فقط روشن‌ها") })
                    FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text("همه") })
                    FeatureRegistry.categories.forEach { c ->
                        FilterChip(
                            selected = cat == c.id,
                            onClick = { cat = if (cat == c.id) null else c.id },
                            label = { Text("${c.title} (${faNum(FeatureRegistry.countIn(c.id))})") },
                        )
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { f ->
                val locked = f.adult && !adultUnlocked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !locked) { prefs.setFeature(f.id, !prefs.isFeatureOn(f.id)) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            f.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(onClick = {}, label = { Text(f.implTitle, style = MaterialTheme.typography.labelSmall) })
                            if (f.adult) {
                                AssistChip(
                                    onClick = { nav.navigate("adult") },
                                    label = { Text("+۱۸", style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                )
                            }
                        }
                    }
                    Switch(
                        checked = prefs.isFeatureOn(f.id),
                        onCheckedChange = { prefs.setFeature(f.id, it) },
                        enabled = !locked,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "چیزی پیدا نشد. شاید املا؟",
                        modifier = Modifier.padding(30.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
