package ae.majlis.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import ae.majlis.tv.R

/** «غسق ليوا» — Liwa dusk. Charcoal base, dune sand, one gold accent, palm green for live/now only. */
object C {
    val Bg = Color(0xFF0F0E0D)
    val Raised = Color(0xFF1A1917)
    val Raised2 = Color(0xFF24221F)
    val Sand = Color(0xFFE6D3B0)
    val Text = Color(0xFFF4EFE6)
    val Dim = Color(0xFFB8AE9C)
    val Gold = Color(0xFFD4A84B)
    val Palm = Color(0xFF5E6B4E)
    val Ring = Color(0xFFF4EFE6)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun vf(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Kufi = FontFamily(vf(R.font.kufi, 400), vf(R.font.kufi, 500), vf(R.font.kufi, 700), vf(R.font.kufi, 800))
val Naskh = FontFamily(vf(R.font.naskh, 400), vf(R.font.naskh, 600), vf(R.font.naskh, 700))
val QuranFont = FontFamily(Font(R.font.amiri_quran))

private val type = Typography(
    displayLarge = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(700), fontSize = 76.sp, lineHeight = 92.sp),
    displayMedium = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(700), fontSize = 44.sp, lineHeight = 60.sp),
    headlineLarge = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(700), fontSize = 32.sp, lineHeight = 46.sp),
    headlineMedium = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(700), fontSize = 26.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(700), fontSize = 22.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(700), fontSize = 20.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(500), fontSize = 17.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(500), fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Naskh, fontWeight = FontWeight(400), fontSize = 19.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontFamily = Naskh, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontFamily = Naskh, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(500), fontSize = 15.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = Kufi, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 18.sp),
)

/** Overscan-safe margins (5% of a 960×540dp TV canvas). */
val SafeH: Dp = 48.dp
val SafeV: Dp = 27.dp

@Composable
fun AlcodeTheme(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    // Fixed font scale → sp == dp: layout never shifts with the system font-size setting.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDensity provides Density(d.density, 1f)) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = C.Gold, onPrimary = C.Bg, secondary = C.Sand, onSecondary = C.Bg,
                background = C.Bg, onBackground = C.Text, surface = C.Raised, onSurface = C.Text,
                surfaceVariant = C.Raised2, onSurfaceVariant = C.Dim, border = C.Ring, inverseSurface = C.Text, inverseOnSurface = C.Bg,
            ),
            typography = type, content = content,
        )
    }
}

/** Sadu weave ornament: filled + outlined triangles over a gold baseline (from DESIGN.md / design.css --sadu). */
@Composable
fun Sadu(modifier: Modifier = Modifier, width: Dp = 96.dp, color: Color = C.Gold) {
    Box(modifier.width(width).height(6.dp)) {
        Canvas(Modifier.width(width).height(6.dp)) {
            val u = size.height; val step = u * 4
            var x = 0f
            while (x < size.width) {
                drawPath(Path().apply { moveTo(x, u); lineTo(x + u, 0f); lineTo(x + 2 * u, u); close() }, color)
                drawPath(Path().apply { moveTo(x + 2 * u, u); lineTo(x + 3 * u, u * .25f); lineTo(x + 4 * u, u); close() }, color, style = Stroke(u * .17f))
                x += step
            }
            drawLine(color, Offset(0f, u * .93f), Offset(size.width, u * .93f), u * .14f)
        }
    }
}
