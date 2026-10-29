package app.magen.lock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.magen.lock.R

// ---------------------------------------------------------------------------------------------
// Magen design system. One place for spacing, surfaces, rows, status, buttons and dialogs, so every
// screen reads as one product. Scale: spacing 4 / 8 / 12 / 16 / 24, groups and banners 16-20 dp corners,
// buttons 12 dp corners and at least 48 dp tall, icons 24 dp (14 dp inside chips).
// Colour: indigo (primary) is the main action and the selected state; status uses its own colours and
// always comes with an icon and a word.
// ---------------------------------------------------------------------------------------------

object Tabs {
    const val HOME = 0
    const val APPS = 1
    const val PROTECTION = 2
    const val TIMES = 3
    const val SETTINGS = 4
}

object Sp {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

private val GroupShape = RoundedCornerShape(16.dp)
private val ButtonShape = RoundedCornerShape(12.dp)

// ---- screens and groups ----------------------------------------------------------------------

/** Standard scrolling screen: a title (optional) and then content with even spacing. */
@Composable
fun MagenScreen(
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Sp.l, vertical = Sp.l),
        verticalArrangement = Arrangement.spacedBy(Sp.l),
    ) {
        if (title != null) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        content()
    }
}

/** Small heading above a group. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Sp.xs),
    )
}

/** One surface for a set of related rows. Separate the rows with [RowDivider]. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GroupShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = Sp.l), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Free text inside a group, with the same side padding as a row. */
@Composable
fun GroupNote(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier.fillMaxWidth().padding(horizontal = Sp.l, vertical = Sp.m),
    )
}

// ---- rows ------------------------------------------------------------------------------------

/**
 * A plain row: optional icon, title, supporting text, something at the end ([trailing]) and optional extra
 * content under the text ([below]). Clickable only when [onClick] is given.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    iconDescription: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val base = modifier.fillMaxWidth().heightIn(min = 56.dp)
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = Sp.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sp.m),
    ) {
        if (icon != null) Icon(icon, iconDescription, tint = iconTint, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            below?.invoke(this)
        }
        trailing?.invoke()
    }
}

/** A row whose whole surface toggles the switch (one accessibility node: "title, switch, on/off"). */
@Composable
fun SwitchListRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Sp.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sp.m),
    ) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The arrow at the end of a row that opens another screen. It mirrors in right-to-left layouts. */
@Composable
fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A row that opens or closes a section; [badge] is a short count shown before the arrow. */
@Composable
fun ExpandRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    badge: String? = null,
) {
    ListRow(
        title = title,
        modifier = modifier,
        supporting = supporting,
        icon = icon,
        onClick = onToggle,
        trailing = {
            if (badge != null) {
                Text(badge, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Sp.s))
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                stringResource(if (expanded) R.string.collapse else R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

// ---- status ----------------------------------------------------------------------------------

/** A small pill with the status icon and a word. */
@Composable
fun StatusChip(sev: Sev, text: String, modifier: Modifier = Modifier) {
    val c = sevColor(sev)
    Surface(modifier, shape = RoundedCornerShape(50), color = c.copy(alpha = 0.14f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sp.xs),
        ) {
            Icon(sevIcon(sev), null, tint = c, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = c)
        }
    }
}

/** The main status of a screen: tinted by severity, with icon, title, text and an optional action. */
@Composable
fun StatusBanner(
    sev: Sev,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = sevColor(sev)
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = c.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, c.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(Sp.l), verticalArrangement = Arrangement.spacedBy(Sp.m)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                    Icon(sevIcon(sev), stringResource(sevLabel(sev)), tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            action?.invoke()
        }
    }
}

/** A tappable number with a label, for a short summary that links to the full tab. */
@Composable
fun SummaryTile(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = GroupShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Sp.l), verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- buttons ---------------------------------------------------------------------------------

@Composable
private fun RowScope.ButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Sp.s))
    }
    Text(text, textAlign = TextAlign.Center)
}

/** The one main action of a screen or dialog area. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = ButtonShape) {
        ButtonLabel(text, icon)
    }
}

/** A secondary action. */
@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = ButtonShape) {
        ButtonLabel(text, icon)
    }
}

/** A quieter secondary action. */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = ButtonShape) {
        ButtonLabel(text, icon)
    }
}

/** An action that removes protection or data. The confirmation dialog it opens carries the warning. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, error.copy(alpha = if (enabled) 0.6f else 0.2f)),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = error),
    ) {
        ButtonLabel(text, icon)
    }
}

/** A low-emphasis text action (a fix link, "back", "copy"). */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Color.Unspecified, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text, color = color, textAlign = TextAlign.Center)
    }
}

// ---- messages --------------------------------------------------------------------------------

/** A hint or explanation inside a screen (not inside a group). */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Sp.xs),
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(Sp.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Sp.m),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/**
 * The one dialog. The body scrolls, so long text is never cut off on small screens or with large fonts.
 * [destructive] colours the confirm button as a warning.
 */
@Composable
fun MagenDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismissAction: (() -> Unit)? = null,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Sp.s),
                content = content,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = if (dismissText != null) {
            { TextButton(onClick = onDismissAction ?: onDismiss) { Text(dismissText) } }
        } else {
            null
        },
    )
}

/** Plain text body for [MagenDialog]. */
@Composable
fun DialogText(text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
    )
}
