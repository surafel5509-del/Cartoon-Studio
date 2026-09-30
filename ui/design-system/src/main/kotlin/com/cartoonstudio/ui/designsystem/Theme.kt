package com.cartoonstudio.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cartoon Studio design language.
 *
 * A dark-first workspace: artwork is the brightest thing on screen, chrome
 * recedes, and a single warm accent marks anything interactive.
 */
object StudioPalette {
    val Accent = Color(0xFFFF7043)
    val AccentSoft = Color(0xFFFFB199)
    val Secondary = Color(0xFF4DD0E1)
    val Tertiary = Color(0xFFFFD54F)
    val Danger = Color(0xFFE53935)
    val Success = Color(0xFF66BB6A)

    val InkBlack = Color(0xFF101014)
    val Charcoal = Color(0xFF17171D)
    val Slate = Color(0xFF1E1E26)
    val Stone = Color(0xFF2A2A35)
    val Smoke = Color(0xFF3A3A47)
    val Mist = Color(0xFFB9B9C6)
    val Paper = Color(0xFFFAF8F4)
    val PaperShade = Color(0xFFEDE9E2)
}

private val DarkColors = darkColorScheme(
    primary = StudioPalette.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF7A2E16),
    onPrimaryContainer = StudioPalette.AccentSoft,
    secondary = StudioPalette.Secondary,
    onSecondary = Color(0xFF00363D),
    tertiary = StudioPalette.Tertiary,
    onTertiary = Color(0xFF3A2E00),
    background = StudioPalette.InkBlack,
    onBackground = Color(0xFFE9E9F0),
    surface = StudioPalette.Charcoal,
    onSurface = Color(0xFFE9E9F0),
    surfaceVariant = StudioPalette.Stone,
    onSurfaceVariant = StudioPalette.Mist,
    outline = StudioPalette.Smoke,
    error = StudioPalette.Danger,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD1481C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3B0A00),
    secondary = Color(0xFF00838F),
    tertiary = Color(0xFFB28900),
    background = StudioPalette.Paper,
    onBackground = Color(0xFF1B1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = StudioPalette.PaperShade,
    onSurfaceVariant = Color(0xFF4A4A52),
    outline = Color(0xFFC6C2BA),
    error = StudioPalette.Danger,
)

private val StudioTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 11.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Spacing scale; every layout in the app uses these values. */
data class StudioSpacing(
    val hairline: androidx.compose.ui.unit.Dp = 1.dp,
    val tiny: androidx.compose.ui.unit.Dp = 4.dp,
    val small: androidx.compose.ui.unit.Dp = 8.dp,
    val medium: androidx.compose.ui.unit.Dp = 12.dp,
    val large: androidx.compose.ui.unit.Dp = 16.dp,
    val extraLarge: androidx.compose.ui.unit.Dp = 24.dp,
    val huge: androidx.compose.ui.unit.Dp = 32.dp,
    val toolSize: androidx.compose.ui.unit.Dp = 44.dp,
    val panelWidth: androidx.compose.ui.unit.Dp = 300.dp,
    val timelineHeight: androidx.compose.ui.unit.Dp = 208.dp,
    val trackHeight: androidx.compose.ui.unit.Dp = 30.dp,
)

val LocalStudioSpacing = staticCompositionLocalOf { StudioSpacing() }

val MaterialTheme.spacing: StudioSpacing
    @Composable get() = LocalStudioSpacing.current

@Composable
fun CartoonStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStudioSpacing provides StudioSpacing()) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = StudioTypography,
            shapes = StudioShapes,
            content = content,
        )
    }
}
