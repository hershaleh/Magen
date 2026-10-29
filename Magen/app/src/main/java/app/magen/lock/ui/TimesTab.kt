package app.magen.lock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.magen.lock.R
import app.magen.lock.Store
import kotlinx.coroutines.delay

/**
 * Only behaviour that really exists and depends on time: when apps lock again, the short uninstall approval
 * window, and the pause after wrong codes. Nothing here promises schedules or limits.
 */
@Composable
fun TimesTab() {
    // Re-read the clock so the state flips by itself when the approval window or a code pause ends.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val until = Store.allowedUntil
    val approvalActive = now < until
    val pauseSeconds = remember(now) { Store.lockRemaining() }

    MagenScreen(title = stringResource(R.string.times_title), subtitle = stringResource(R.string.times_sub)) {
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
                supporting = if (approvalActive) {
                    stringResource(
                        R.string.times_pause_active,
                        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(until)),
                    )
                } else {
                    stringResource(R.string.times_pause_none)
                },
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
                    if (pauseSeconds > 0) {
                        StatusChip(Sev.WARN, stringResource(R.string.times_wrong_now, pauseSeconds))
                    }
                },
            )
        }
    }
}
