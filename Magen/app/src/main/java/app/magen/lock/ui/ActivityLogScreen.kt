package app.magen.lock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.magen.lock.ActivityEvent
import app.magen.lock.ActivityLog
import app.magen.lock.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun ActivityLogScreen(onBack: () -> Unit) {
    val events by produceState(emptyList<ActivityEvent>()) {
        while (true) {
            value = withContext(Dispatchers.IO) { ActivityLog.readLatest() }
            delay(2_000)
        }
    }
    TabColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.stats_title), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
        }
        SectionCard(stringResource(R.string.activity_log_title), stringResource(R.string.activity_log_desc)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { ActivityLog.clear() }) {
                    Text(stringResource(R.string.activity_log_clear))
                }
            }
            if (events.isEmpty()) {
                Text(stringResource(R.string.activity_log_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    events.forEach { event ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                eventTitle(event),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            )
                            val whenText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(event.timestamp))
                            Text(
                                listOf(whenText, event.detail).filter { it.isNotBlank() }.joinToString(" · "),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun eventTitle(event: ActivityEvent): String = when (event.type) {
    "PIN_FAILED" -> stringResource(R.string.event_pin_failed)
    "APP_UNLOCKED" -> stringResource(R.string.event_app_unlocked, event.detail)
    "APP_LOCK_ENFORCED" -> stringResource(R.string.event_app_locked, event.detail)
    "APP_USAGE_LIMIT_REACHED" -> stringResource(R.string.event_usage_limit, event.detail)
    "APP_BLOCK_METHOD_SET" -> stringResource(R.string.event_block_method, event.detail)
    "APP_UNBLOCKED" -> stringResource(R.string.event_app_unblocked, event.detail)
    "APP_SCHEDULE_CHANGED" -> stringResource(R.string.event_schedule_changed, event.detail)
    "APP_USAGE_LIMIT_CHANGED" -> stringResource(R.string.event_usage_limit_changed, event.detail)
    "UNINSTALL_APPROVAL_WINDOW" -> stringResource(R.string.event_uninstall_approval, event.detail)
    else -> event.type
}
