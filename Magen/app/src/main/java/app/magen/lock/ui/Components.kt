package app.magen.lock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.magen.lock.R
import app.magen.lock.Store
import kotlinx.coroutines.delay

@Composable
fun ShieldBadge(size: Dp = 84.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(HeroBrush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun AppBadge(icon: ImageBitmap?, size: Dp = 84.dp) {
    if (icon == null) {
        ShieldBadge(size)
    } else {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(24.dp)))
    }
}

/** Numeric keypad with dots. Always laid out left-to-right so digits keep their familiar order in Hebrew. */
@Composable
fun PinPad(
    title: String,
    subtitle: String?,
    error: String?,
    enabled: Boolean = true,
    icon: @Composable () -> Unit = { ShieldBadge() },
    onCancel: (() -> Unit)? = null,
    onSubmit: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val maxLen = 8
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxLen) { i ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }
        }
        Text(
            error ?: " ",
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("<", "0", ">"),
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { k ->
                            Key(enabled, onClick = {
                                if (k == "<") {
                                    pin = pin.dropLast(1)
                                } else if (k == ">") {
                                    if (pin.length >= 4) {
                                        val p = pin
                                        pin = ""
                                        onSubmit(p)
                                    }
                                } else if (pin.length < maxLen) {
                                    pin += k
                                }
                            }) {
                                when (k) {
                                    "<" -> Icon(Icons.AutoMirrored.Filled.Backspace, null)
                                    ">" -> Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    else -> Text(k, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.go_back)) }
        }
    }
}

@Composable
private fun Key(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(76.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/** Keypad that verifies against the master code, including the wrong-attempt lockout. */
@Composable
fun PinGate(
    title: String,
    subtitle: String?,
    icon: @Composable () -> Unit = { ShieldBadge() },
    onCancel: (() -> Unit)? = null,
    onSuccess: () -> Unit,
) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableIntStateOf(Store.lockRemaining()) }
    LaunchedEffect(wait) {
        if (wait > 0) {
            delay(1000)
            wait = Store.lockRemaining()
        }
    }
    PinPad(
        title = title,
        subtitle = subtitle,
        error = if (wait > 0) ctx.getString(R.string.try_again_in, wait) else error,
        enabled = wait == 0,
        icon = icon,
        onCancel = onCancel,
    ) { pin ->
        if (Store.checkPin(pin)) {
            onSuccess()
        } else {
            error = ctx.getString(R.string.wrong_code)
            wait = Store.lockRemaining()
        }
    }
}

/** Two-step "enter code, then confirm it" flow. */
@Composable
fun CreatePinFlow(onCancel: (() -> Unit)? = null, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    PinPad(
        title = stringResource(if (first == null) R.string.create_pin else R.string.confirm_pin),
        subtitle = stringResource(R.string.pin_hint),
        error = error,
        onCancel = onCancel,
    ) { pin ->
        val f = first
        if (f == null) {
            first = pin
            error = null
        } else if (f == pin) {
            onDone(pin)
        } else {
            error = ctx.getString(R.string.pin_mismatch)
            first = null
        }
    }
}

@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(HeroBrush).padding(22.dp),
        content = content,
    )
}

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun IconTile(icon: ImageVector) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, desc: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
