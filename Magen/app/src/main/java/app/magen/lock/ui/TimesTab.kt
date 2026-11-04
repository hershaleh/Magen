package app.magen.lock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.magen.lock.ProfileStore
import app.magen.lock.R
import app.magen.lock.Store
import app.magen.lock.WeeklyProfileWindow
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun TimesTab() {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            ProfileStore.refresh()
            delay(5_000)
        }
    }
    val until = Store.allowedUntil
    val approvalActive = now < until
    val pauseSeconds = remember(now) { Store.lockRemaining() }

    MagenScreen(title = stringResource(R.string.times_title), subtitle = stringResource(R.string.times_sub)) {
        SectionHeader(stringResource(R.string.profiles_title))
        GroupCard {
            Text(stringResource(R.string.profiles_active, ProfileStore.activeProfileName))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileStore.profiles.forEach { profile ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = ProfileStore.activeProfileId == profile.id,
                            onClick = { ProfileStore.select(profile.id) },
                            label = { Text(profile.name) },
                        )
                    }
                }
            }
            var newName by remember { mutableStateOf("") }
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it.take(32) },
                label = { Text(stringResource(R.string.profile_new_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { if (ProfileStore.addProfile(newName)) newName = "" },
                enabled = newName.isNotBlank() && ProfileStore.profiles.size < 20,
            ) { Text(stringResource(R.string.profile_add)) }
        }

        SectionHeader(stringResource(R.string.schedule_title))
        GroupCard {
            Text(stringResource(R.string.schedule_help))
            var day by remember { mutableStateOf("1") }
            var start by remember { mutableStateOf("08:00") }
            var end by remember { mutableStateOf("16:00") }
            var selected by remember { mutableStateOf(ProfileStore.activeProfileId) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = day, onValueChange = { day = it.filter(Char::isDigit).take(1) },
                    label = { Text(stringResource(R.string.schedule_day)) }, modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = start, onValueChange = { start = it.take(5) },
                    label = { Text(stringResource(R.string.schedule_start)) }, modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = end, onValueChange = { end = it.take(5) },
                    label = { Text(stringResource(R.string.schedule_end)) }, modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            ProfileStore.profiles.forEach { profile ->
                FilterChip(
                    selected = selected == profile.id,
                    onClick = { selected = profile.id },
                    label = { Text(profile.name) },
                )
            }
            val dayNumber = day.toIntOrNull()
            val startMinute = parseMinute(start)
            val endMinute = parseMinute(end)
            Button(
                onClick = {
                    val next = ProfileStore.windows + WeeklyProfileWindow(
                        UUID.randomUUID().toString(), dayNumber!!, startMinute!!, endMinute!!, selected,
                    )
                    ProfileStore.saveWindows(next)
                },
                enabled = dayNumber != null && dayNumber in 1..7 && startMinute != null && endMinute != null &&
                    startMinute != endMinute && ProfileStore.profiles.any { it.id == selected },
            ) { Text(stringResource(R.string.schedule_add)) }
            if (ProfileStore.windows.isNotEmpty()) {
                Text(stringResource(R.string.schedule_saved))
                ProfileStore.windows.forEach { window ->
                    val profileName = ProfileStore.profiles.firstOrNull { it.id == window.profileId }?.name ?: "?"
                    Text(stringResource(
                        R.string.schedule_window_summary,
                        window.dayOfWeek, formatMinute(window.startMinute), formatMinute(window.endMinute), profileName,
                    ))
                }
                Button(onClick = { ProfileStore.saveWindows(emptyList()) }) {
                    Text(stringResource(R.string.schedule_clear))
                }
            }
        }

        SectionHeader(stringResource(R.string.times_lock_title))
        GroupCard {
            SwitchListRow(
                title = stringResource(R.string.relock_screen_off),
                checked = Store.relockOnScreenOff,
                onChange = { Store.setRelock(it) },
                supporting = stringResource(R.string.relock_desc),
                icon = Icons.Filled.Lock,
            )
            RowDivider()
            ListRow(
                title = stringResource(R.string.times_leave_title),
                supporting = stringResource(R.string.times_leave_body),
                icon = Icons.Filled.Schedule,
            )
        }

        SectionHeader(stringResource(R.string.times_pause_title))
        GroupCard {
            ListRow(
                title = stringResource(R.string.times_approval_title),
                supporting = if (approvalActive) stringResource(
                    R.string.times_pause_active,
                    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(until)),
                ) else stringResource(R.string.times_pause_none),
                icon = Icons.Filled.Warning,
                iconTint = if (approvalActive) sevColor(Sev.WARN) else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                trailing = {
                    StatusChip(
                        if (approvalActive) Sev.WARN else Sev.INFO,
                        stringResource(if (approvalActive) R.string.times_state_active else R.string.times_state_inactive),
                    )
                },
            )
            RowDivider()
            ListRow(
                title = stringResource(R.string.times_wrong_title),
                supporting = stringResource(R.string.times_wrong_body),
                icon = Icons.Filled.Lock,
                trailing = {
                    if (pauseSeconds > 0) StatusChip(Sev.WARN, stringResource(R.string.times_wrong_now, pauseSeconds))
                },
            )
        }
    }
}

private fun parseMinute(value: String): Int? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun formatMinute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
