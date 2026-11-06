@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.cooklink.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooklink.ai.R
import com.cooklink.ai.data.settings.NeonAccent

// Night kitchen: near-black surfaces, one electric accent, a hot secondary.
object Neon {
    val Ink = Color(0xFF09090C)
    val Night = Color(0xFF0E0E13)
    val Panel = Color(0xFF15151C)
    val PanelHigh = Color(0xFF1C1C25)
    val PanelHigher = Color(0xFF24242F)
    val Line = Color(0xFF2C2C3A)
    val Muted = Color(0xFF8D8DA3)
    val Text = Color(0xFFF2F2F7)

    val Lime = Color(0xFFC6FF3D)
    val Orange = Color(0xFFFF7A1A)
    val Pink = Color(0xFFFF3DB8)
    val Cyan = Color(0xFF3DE8FF)
    val Amber = Color(0xFFFFC53D)
    val Green = Color(0xFF5CFF8A)
}

fun NeonAccent.color(): Color = when (this) {
    NeonAccent.LIME -> Neon.Lime
    NeonAccent.ORANGE -> Neon.Orange
    NeonAccent.PINK -> Neon.Pink
    NeonAccent.CYAN -> Neon.Cyan
}

/** Second accent paired with each main accent, for chips and highlights. */
private fun NeonAccent.partner(): Color = when (this) {
    NeonAccent.LIME -> Neon.Orange
    NeonAccent.ORANGE -> Neon.Lime
    NeonAccent.PINK -> Neon.Cyan
    NeonAccent.CYAN -> Neon.Pink
}

private fun weighted(res: Int, w: Int) =
    Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Wide, chunky display face for titles. */
val Display = FontFamily(
    weighted(R.font.unbounded, 400),
    weighted(R.font.unbounded, 500),
    weighted(R.font.unbounded, 600),
    weighted(R.font.unbounded, 700),
    weighted(R.font.unbounded, 800),
    weighted(R.font.unbounded, 900),
)

/** Techy grotesk for body text and labels. */
val Body = FontFamily(
    weighted(R.font.space_grotesk, 400),
    weighted(R.font.space_grotesk, 500),
    weighted(R.font.space_grotesk, 600),
    weighted(R.font.space_grotesk, 700),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Colors for the confirmed / estimated / missing badges. */
@Immutable
data class CertaintyColors(val confirmed: Color, val estimated: Color, val missing: Color)

val LocalCertaintyColors = staticCompositionLocalOf { CertaintyColors(Neon.Green, Neon.Amber, Neon.Muted) }

/** The current accent pair, for glows and decorative touches outside the Material scheme. */
@Immutable
data class NeonPalette(val accent: Color, val partner: Color)

val LocalNeon = staticCompositionLocalOf { NeonPalette(Neon.Lime, Neon.Orange) }

@Composable
fun CookLinkTheme(accent: NeonAccent, content: @Composable () -> Unit) {
    val a = accent.color()
    val p = accent.partner()
    val scheme = darkColorScheme(
        primary = a,
        onPrimary = Neon.Ink,
        primaryContainer = a.copy(alpha = 0.16f).compositeOver(Neon.Panel),
        onPrimaryContainer = a,
        secondary = p,
        onSecondary = Neon.Ink,
        secondaryContainer = p.copy(alpha = 0.14f).compositeOver(Neon.Panel),
        onSecondaryContainer = p,
        tertiary = Neon.Amber,
        onTertiary = Neon.Ink,
        tertiaryContainer = Neon.Amber.copy(alpha = 0.14f).compositeOver(Neon.Panel),
        onTertiaryContainer = Neon.Amber,
        background = Neon.Night,
        onBackground = Neon.Text,
        surface = Neon.Night,
        onSurface = Neon.Text,
        surfaceVariant = Neon.PanelHigh,
        onSurfaceVariant = Neon.Muted,
        surfaceContainerLowest = Neon.Ink,
        surfaceContainerLow = Neon.Panel,
        surfaceContainer = Neon.Panel,
        surfaceContainerHigh = Neon.PanelHigh,
        surfaceContainerHighest = Neon.PanelHigher,
        inverseSurface = Neon.Text,
        inverseOnSurface = Neon.Ink,
        outline = Neon.Line,
        outlineVariant = Neon.Line,
        error = Color(0xFFFF5470),
        onError = Neon.Ink,
        errorContainer = Color(0xFF3A0F1A),
        onErrorContainer = Color(0xFFFFB3C0),
        scrim = Color.Black,
    )
    CompositionLocalProvider(
        LocalCertaintyColors provides CertaintyColors(Neon.Green, Neon.Amber, Neon.Muted),
        LocalNeon provides NeonPalette(a, p),
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

/** Soft neon light around a rounded element. */
fun Modifier.neonGlow(color: Color, radius: Dp = 16.dp, corner: Dp = 28.dp, strength: Float = 0.6f): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = color.copy(alpha = 0f).toArgb()
            setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = strength).toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner.toPx(), corner.toPx(), paint)
    }
}
