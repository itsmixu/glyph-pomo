package org.stuhi.glyphpomodoro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Nothing-inspired palette: black, off-white, one signature red. */
object Glyph {
    val Black = Color(0xFF000000)
    val Bg = Color(0xFF0A0A0A)
    val Panel = Color(0xFF151515)
    val PanelHi = Color(0xFF1F1F1F)
    val Line = Color(0xFF262626)
    val Text = Color(0xFFEDEDED)
    val Muted = Color(0xFF7C7C7C)
    val Red = Color(0xFFE5332A)
    val CellOff = Color(0xFF2A2A2A)
    val CellGhost = Color(0xFF6E6E6E)
}

private val Scheme = darkColorScheme(
    primary = Glyph.Red,
    onPrimary = Color.White,
    secondary = Glyph.Text,
    onSecondary = Glyph.Black,
    background = Glyph.Bg,
    onBackground = Glyph.Text,
    surface = Glyph.Panel,
    onSurface = Glyph.Text,
    surfaceVariant = Glyph.PanelHi,
    onSurfaceVariant = Glyph.Muted,
    outline = Glyph.Line,
    outlineVariant = Glyph.Line,
)

private val Mono = FontFamily.Monospace

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 3.sp),
    bodyLarge = TextStyle(fontFamily = Mono, fontSize = 14.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, letterSpacing = 0.5.sp),
    bodySmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp),
)

@Composable
fun GlyphTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
