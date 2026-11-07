package app.magen.lock.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.magen.lock.EventLog
import app.magen.lock.EventSeverity
import app.magen.lock.EventState
import app.magen.lock.GuardService
import app.magen.lock.MagenEvent
import app.magen.lock.formatEventTime
import app.magen.lock.severityLabel
import app.magen.lock.stateLabel

@Composable
fun EventLogPanel(title: String, subtitle: String, showProtectionStatus: Boolean) {
    var severity by rememberSaveable { mutableStateOf("הכול") }
    var state by rememberSaveable { mutableStateOf("הכול") }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedSeverity = when (severity) {
        "קריטי" -> EventSeverity.CRITICAL
        "גבוה" -> EventSeverity.HIGH
        "בינוני" -> EventSeverity.MEDIUM
        "נמוך" -> EventSeverity.LOW
        else -> null
    }
    val selectedState = when (state) {
        "פעיל" -> EventState.ACTIVE
        "בטיפול" -> EventState.IN_PROGRESS
        "נפתר" -> EventState.RESOLVED
        "לא נפתר" -> EventState.UNRESOLVED
        else -> null
    }
    val filtered = EventLog.filtered(selectedSeverity, selectedState, unreadOnly, query)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (showProtectionStatus) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val running = GuardService.instance != null
                    Text("מצב שירות ההגנה", style = MaterialTheme.typography.titleMedium)
                    Text(if (running) "שירות הנגישות של מגן פועל" else "שירות הנגישות של מגן אינו פועל")
                    Text(
                        "זהו מצב השירות שנקרא כרגע; הוא אינו מוכיח שכל חסימה נאכפת בהצלחה.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("סינון וחיפוש", style = MaterialTheme.typography.titleMedium)
                Text("חומרה", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("הכול", "קריטי", "גבוה", "בינוני", "נמוך").forEach { label ->
                        FilterChip(selected = severity == label, onClick = { severity = label }, label = { Text(label) })
                    }
                }
                Text("מצב טיפול", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("הכול", "פעיל", "בטיפול", "נפתר", "לא נפתר").forEach { label ->
                        FilterChip(selected = state == label, onClick = { state = label }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("חיפוש לפי אירוע, אפליקציה או פרטים טכניים") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = unreadOnly, onCheckedChange = { unreadOnly = it })
                    Text("הצג אירועים שלא נקראו בלבד")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("אירועים (${filtered.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { EventLog.markAllRead() }) { Text("סמן הכול כנקרא") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { EventLog.markAllUnread() }) { Text("סמן הכול כלא נקרא") }
                    Text("לא נקראו: ${EventLog.unreadCount()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (filtered.isEmpty()) {
                    Text(
                        if (EventLog.events.isEmpty()) "עדיין לא נרשמו אירועים. שינויים בהגדרות ואירועי הגנה יופיעו כאן."
                        else "לא נמצאו אירועים התואמים למסננים.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    filtered.forEach { event ->
                        EventRow(event) {
                            EventLog.markRead(event.id)
                            selectedId = event.id
                        }
                    }
                }
                Text("ברירת המחדל היא שמירת אירועים ללא הגבלת זמן. אין כאן מחיקה של אירוע בודד.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Magen — דוח אירועים")
                    putExtra(Intent.EXTRA_TEXT, EventLog.exportText())
                }
                runCatching { context.startActivity(Intent.createChooser(send, "ייצוא דוח אירועים")) }
            }) { Text("ייצוא דוח") }
            TextButton(onClick = { confirmClear = true }) { Text("מחיקת כל היומן") }
        }
    }

    val selected = selectedId?.let(EventLog::find)
    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text(selected.title) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(selected.message)
                    Text("חומרה: ${severityLabel(selected.severity)}")
                    Text("מצב טיפול: ${stateLabel(selected.state)}")
                    Text("מופעים: ${selected.occurrences}")
                    Text("נוצר: ${formatEventTime(selected.createdAt)}")
                    Text("עודכן: ${formatEventTime(selected.updatedAt)}")
                    if (selected.technical.isNotBlank()) Text("פרטים טכניים: ${selected.technical}")
                    Text("היסטוריה", style = MaterialTheme.typography.titleSmall)
                    selected.history.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = { EventLog.markRead(selected.id, false); selectedId = null }) { Text("סמן כלא נקרא") }
            },
            dismissButton = { TextButton(onClick = { selectedId = null }) { Text("סגירה") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("מחיקת כל יומן האירועים?") },
            text = { Text("כל האירועים וההיסטוריה המקומית יימחקו. לא ניתן לבטל פעולה זו.") },
            confirmButton = {
                TextButton(onClick = { EventLog.clearAll(); confirmClear = false }) { Text("מחיקה") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun EventRow(event: MagenEvent, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (event.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.title, Modifier.weight(1f), fontWeight = if (event.read) FontWeight.Normal else FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(severityLabel(event.severity), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(event.message, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${stateLabel(event.state)} · ${formatEventTime(event.updatedAt)} · מופעים: ${event.occurrences}" +
                    if (event.read) "" else " · לא נקרא",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
