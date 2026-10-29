package app.magen.lock.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.magen.lock.Bio
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
    extra: (@Composable () -> Unit)? = null,
    onSubmit: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val maxLen = 8
    // Short or zoomed screens: the pad scrolls instead of cutting off the bottom row (with the confirm key),
    // and the keys shrink a little when there is little height.
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
    val viewport = maxHeight
    val keySize = if (viewport < 640.dp) 60.dp else 76.dp
    Column(
        Modifier.fillMaxWidth().heightIn(min = viewport).verticalScroll(rememberScrollState()).padding(24.dp),
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
                            Key(enabled, keySize, onClick = {
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
                                    "<" -> Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.pin_delete))
                                    ">" -> Icon(Icons.Filled.Check, stringResource(R.string.pin_confirm), tint = MaterialTheme.colorScheme.primary)
                                    else -> Text(k, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
        extra?.invoke()
        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.go_back)) }
        }
    }
    }
}

@Composable
private fun Key(enabled: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(size),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/** Android 13+ shows its own "copied" preview; older versions need this short confirmation. */
fun toastCopied(ctx: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT < 33) {
        android.widget.Toast.makeText(ctx, R.string.copied, android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * Keypad that verifies against the master code, including the wrong-attempt lockout.
 * With [allowAlt] it also offers fingerprint unlock (when enabled) and the recovery-code reset.
 */
@Composable
fun PinGate(
    title: String,
    subtitle: String?,
    icon: @Composable () -> Unit = { ShieldBadge() },
    onCancel: (() -> Unit)? = null,
    allowAlt: Boolean = false,
    onSuccess: () -> Unit,
) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableIntStateOf(Store.lockRemaining()) }
    var recovering by remember { mutableStateOf(false) }
    LaunchedEffect(wait) {
        if (wait > 0) {
            delay(1000)
            wait = Store.lockRemaining()
        }
    }
    val bioOk = remember { Bio.available(ctx) }
    val useBio = allowAlt && Store.biometric && bioOk
    val askBio = {
        val act = Bio.activity(ctx)
        if (act != null) {
            Bio.prompt(act, ctx.getString(R.string.bio_prompt_title), ctx.getString(R.string.bio_use_code), onSuccess)
        }
    }
    LaunchedEffect(useBio) { if (useBio && !recovering) askBio() }

    if (recovering) {
        RecoveryFlow(onCancel = { recovering = false }, onDone = onSuccess)
        return
    }
    val altUi: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (useBio) {
                IconButton(onClick = { askBio() }) {
                    Icon(Icons.Filled.Fingerprint, stringResource(R.string.bio_prompt_title), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (Store.hasRecovery) {
                TextButton(onClick = { recovering = true }) { Text(stringResource(R.string.forgot_code)) }
            }
        }
    }
    PinPad(
        title = title,
        subtitle = subtitle,
        error = if (wait > 0) ctx.getString(R.string.try_again_in, wait) else error,
        enabled = wait == 0,
        icon = icon,
        onCancel = onCancel,
        extra = if (allowAlt && (useBio || Store.hasRecovery)) altUi else null,
    ) { pin ->
        if (Store.checkPin(pin)) {
            onSuccess()
        } else {
            error = ctx.getString(R.string.wrong_code)
            wait = Store.lockRemaining()
        }
    }
}

/** Reset the master code with the recovery code, then show the replacement recovery code. */
@Composable
fun RecoveryFlow(onCancel: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf("") }
    when (step) {
        0 -> Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ShieldBadge()
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.recovery_enter_sub),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(20) },
                singleLine = true,
                placeholder = { Text("XXXX-XXXX-XXXX") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(error ?: " ", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 10.dp))
            Button(
                onClick = {
                    val wait = Store.lockRemaining()
                    if (wait > 0) {
                        error = ctx.getString(R.string.try_again_in, wait)
                    } else if (Store.checkRecovery(code)) {
                        error = null
                        step = 1
                    } else {
                        error = ctx.getString(R.string.recovery_wrong)
                    }
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(R.string.recovery_verify)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.go_back)) }
        }
        1 -> CreatePinFlow(onCancel = onCancel) {
            Store.setPin(it)
            fresh = Store.newRecoveryCode()
            step = 2
        }
        else -> RecoveryCodeScreen(fresh, onDone)
    }
}

/** Shows a recovery code once, with copy, until the person confirms they saved it. */
@Composable
fun RecoveryCodeScreen(code: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShieldBadge()
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.recovery_show_sub),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedButton(
            onClick = {
                clipboard.setText(AnnotatedString(code))
                toastCopied(ctx)
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.recovery_copy))
        }
        Text(
            stringResource(R.string.recovery_warn),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 20.dp),
        )
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(stringResource(R.string.recovery_saved)) }
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
        // One accessibility node per row, so a screen reader announces "title, description, switch, on/off".
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 8.dp),
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
