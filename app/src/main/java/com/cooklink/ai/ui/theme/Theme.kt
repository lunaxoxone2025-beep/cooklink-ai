package com.cooklink.ai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warm kitchen palette: terracotta, olive, cream paper, charred espresso.
private val Terracotta = Color(0xFFC2410C)
private val TerracottaLight = Color(0xFFFFB693)
private val Olive = Color(0xFF5F6B2E)
private val OliveLight = Color(0xFFC5D18E)
private val Cream = Color(0xFFFFF8F1)
private val Paper = Color(0xFFF7EDE2)
private val Espresso = Color(0xFF17120E)
private val Roast = Color(0xFF231C17)

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Olive,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EDB0),
    onSecondaryContainer = Color(0xFF1A1E00),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2C1700),
    background = Cream,
    onBackground = Color(0xFF221A15),
    surface = Cream,
    onSurface = Color(0xFF221A15),
    surfaceVariant = Paper,
    onSurfaceVariant = Color(0xFF55433A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF2E9),
    surfaceContainer = Paper,
    surfaceContainerHigh = Color(0xFFF1E5DA),
    surfaceContainerHighest = Color(0xFFEBDFD3),
    outline = Color(0xFF88726A),
    outlineVariant = Color(0xFFDBC1B6),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = TerracottaLight,
    onPrimary = Color(0xFF5C1A00),
    primaryContainer = Color(0xFF842B00),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = OliveLight,
    onSecondary = Color(0xFF2E3500),
    secondaryContainer = Color(0xFF454F18),
    onSecondaryContainer = Color(0xFFE2EDB0),
    tertiary = Color(0xFFFFB950),
    tertiaryContainer = Color(0xFF694300),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Espresso,
    onBackground = Color(0xFFF0DFD6),
    surface = Espresso,
    onSurface = Color(0xFFF0DFD6),
    surfaceVariant = Roast,
    onSurfaceVariant = Color(0xFFDBC1B6),
    surfaceContainerLowest = Color(0xFF110D0A),
    surfaceContainerLow = Color(0xFF1F1813),
    surfaceContainer = Roast,
    surfaceContainerHigh = Color(0xFF2E2620),
    surfaceContainerHighest = Color(0xFF39302A),
    outline = Color(0xFFA38C83),
    outlineVariant = Color(0xFF55433A),
    error = Color(0xFFFFB4AB),
)

private val Display = FontFamily.Serif
private val Body = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Colors for the confirmed / estimated / missing badges, tuned per theme. */
@Immutable
data class CertaintyColors(val confirmed: Color, val estimated: Color, val missing: Color)

val LocalCertaintyColors = staticCompositionLocalOf {
    CertaintyColors(Color(0xFF2E7D32), Color(0xFFB26A00), Color(0xFF9E9E9E))
}

@Composable
fun CookLinkTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (darkTheme) DarkColors else LightColors
    val certainty = if (darkTheme) {
        CertaintyColors(Color(0xFF81C784), Color(0xFFFFB950), Color(0xFF9E8F88))
    } else {
        CertaintyColors(Color(0xFF2E7D32), Color(0xFFB26A00), Color(0xFF8C7B73))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalCertaintyColors provides certainty) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
