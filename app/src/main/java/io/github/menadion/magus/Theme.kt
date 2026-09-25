package io.github.menadion.magus

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Direction B, "Floating Cards". Everything here comes from design/handoff/HANDOFF.md, section 1.

// Colours that are Mogar's own, outside Material's roles. The Material ones live in the scheme below.
object MogarColors {
    val FamilyGreen = Color(0xFF26803F)   // family dots, quiet ring, done ticks
    val QuietInitial = Color(0xFF1D6B34)  // the letter inside a quiet (hollow) dot
    val Paused = Color(0xFF6E7482)        // paused dot
    val Handle = Color(0xFFB8B9C3)        // bottom sheet drag handle
    val Dialog = Color(0xFFF0F0F7)        // About dialog
}

private val MogarScheme = lightColorScheme(
    primary = Color(0xFF2F55C4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE2FF),
    onPrimaryContainer = Color(0xFF0A1F5C),
    surface = Color(0xFFFAF9FD),
    background = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1A1B20),
    onBackground = Color(0xFF1A1B20),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainerLowest = Color(0xFFFFFFFF), // floating cards, rows, tiles
    surfaceContainerLow = Color(0xFFF4F3FA),    // member card, family list sheet
    surfaceContainerHigh = Color(0xFFECEDF4),   // done step cards, Sharing OFF pill, dividers
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C6D0),
    error = Color(0xFFBA1A1A),
    scrim = Color(0xFF000000),
)

// Figtree, bundled as one variable font so every family phone shows the same letters.
private fun figtree(weight: FontWeight) = Font(
    R.font.figtree,
    weight = weight,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
)

val Figtree = FontFamily(figtree(FontWeight.W400), figtree(FontWeight.W600), figtree(FontWeight.W700))

private fun style(size: Int, weight: FontWeight, lineHeight: Int, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = Figtree,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

// The type scale, mapped onto Material's slots. The comment on each slot names the handoff's use.
private val MogarTypography = Typography(
    displaySmall = style(30, FontWeight.W700, 36),              // Setup title
    displayMedium = style(26, FontWeight.W700, 32, 3.1f),       // family code, wide letter spacing
    headlineLarge = style(28, FontWeight.W700, 34),             // member card name, Keep running title
    headlineMedium = style(24, FontWeight.W700, 30),            // Settings title, list sheet title, tile values
    headlineSmall = style(22, FontWeight.W700, 28),             // family name on the top card
    titleLarge = style(21, FontWeight.W700, 26),                // Sharing pill label
    titleMedium = style(19, FontWeight.W700, 24),               // settings value, choice card title
    titleSmall = style(18, FontWeight.W700, 23),                // list row name, nav row title, setup subline
    bodyLarge = style(16, FontWeight.W400, 22),                 // body, top card subline
    bodyMedium = style(15, FontWeight.W600, 20),                // status lines, labels
    bodySmall = style(14, FontWeight.W400, 18),
    labelLarge = style(17, FontWeight.W700, 22),                // "Family" row header
    labelMedium = style(15, FontWeight.W700, 20),               // family row names
    labelSmall = style(14, FontWeight.W700, 18),                // Settings section labels
)

private val MogarShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), // map name tags
    small = RoundedCornerShape(16.dp),      // tiles
    medium = RoundedCornerShape(20.dp),     // settings groups, choice cards
    large = RoundedCornerShape(24.dp),      // floating cards, step cards
    extraLarge = RoundedCornerShape(28.dp), // sheets (top corners), dialogs
)

@Composable
fun MogarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MogarScheme, typography = MogarTypography, shapes = MogarShapes, content = content)
}
