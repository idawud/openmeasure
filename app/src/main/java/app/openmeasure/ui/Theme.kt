package app.openmeasure.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** OpenMeasure palette (dark only; iOS Measure inspired). */
object OmColors {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceHigh = Color(0xFF2C2C2E)
    val Accent = Color(0xFFFFD60A)
    val Success = Color(0xFF30D158)
    val Warning = Color(0xFFFF9F0A)
    val Danger = Color(0xFFFF453A)
    val TextSecondary = Color(0xFFAEAEB2)

    /** Icon buttons over the camera (black 55%). */
    val OverlayScrim = Color(0x8C000000)

    /** Anything that carries text over the camera: black 70% keeps white text >= 4.5:1 even on a white wall. */
    val OverlayLabel = Color(0xB3000000)

    /** Bottom tab scrim end colour. */
    val OverlayStrong = Color(0xB3000000)
    val Outline = Color(0x33FFFFFF)
    val LineOutline = Color(0x66000000)
    val DotShadow = Color(0x4D000000)
}

object Dimens {
    val touch = 48.dp
    val iconButton = 44.dp
    val shutter = 72.dp
    val pillHeight = 36.dp
    val margin = 16.dp
    val gap = 8.dp
    val card = 16.dp
    val maxContent = 600.dp
    val wideBreakpoint = 600.dp
    /** Space the bottom tab row reserves at the bottom of the Measure / Height / Level screens (above the system inset). */
    val tabBarHeight = 72.dp
    /** Top row of corner buttons (44dp + margins); hints start below it. */
    val topRowHeight = 64.dp
}

object OmShapes {
    val pill = RoundedCornerShape(50)
    val label = RoundedCornerShape(18.dp)
    val banner = RoundedCornerShape(20.dp)
    val card = RoundedCornerShape(Dimens.card)
}

private val Tnum = "tnum"

/** Typography: system font, medium weights, tabular numerals for anything that shows a measurement. */
val OmTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tnum),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Text styles for measurement values (bold, tabular numerals so digits do not jitter). */
object OmText {
    val measure = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = Tnum)
    val measureLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = Tnum)
    val sheetValue = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = Tnum)
}

private val OmColorScheme = darkColorScheme(
    primary = OmColors.Accent,
    onPrimary = OmColors.Black,
    secondary = OmColors.Accent,
    onSecondary = OmColors.Black,
    background = OmColors.Black,
    onBackground = Color.White,
    surface = OmColors.Black,
    onSurface = Color.White,
    surfaceVariant = OmColors.SurfaceHigh,
    onSurfaceVariant = OmColors.TextSecondary,
    surfaceContainerLowest = OmColors.Black,
    surfaceContainerLow = OmColors.Surface,
    surfaceContainer = OmColors.Surface,
    surfaceContainerHigh = OmColors.SurfaceHigh,
    surfaceContainerHighest = OmColors.SurfaceHigh,
    error = OmColors.Danger,
    onError = OmColors.Black,
    outline = OmColors.TextSecondary,
    outlineVariant = OmColors.Outline,
    inverseSurface = OmColors.SurfaceHigh,
    inverseOnSurface = Color.White,
    inversePrimary = OmColors.Accent,
)

@Composable
fun OpenMeasureTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OmColorScheme,
        typography = OmTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
