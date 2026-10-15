package app.magen.lock.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0F9F8F),
    tertiary = Color(0xFF7C3AED),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14172B),
    surface = Color.White,
    onSurface = Color(0xFF14172B),
    surfaceVariant = Color(0xFFEEF0F8),
    onSurfaceVariant = Color(0xFF5A6078),
    outlineVariant = Color(0xFFDDE1EE),
    error = Color(0xFFDC2626),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8B93FF),
    onPrimary = Color(0xFF14123F),
    primaryContainer = Color(0xFF2B2E63),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF2DD4BF),
    tertiary = Color(0xFFB794F6),
    background = Color(0xFF0E1120),
    onBackground = Color(0xFFE8EAF6),
    surface = Color(0xFF171B2E),
    onSurface = Color(0xFFE8EAF6),
    surfaceVariant = Color(0xFF212640),
    onSurfaceVariant = Color(0xFFA3A9C4),
    outlineVariant = Color(0xFF2E3454),
    error = Color(0xFFF87171),
)

val HeroBrush = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF0EA5A4)))

@Composable
fun MagenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp),
        ),
        content = content,
    )
}
