package io.noriskservis.wpayin.wallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R

val Ink = Color(0xFF070810)
val InkRaised = Color(0xFF0E101A)
val InkSurface = Color(0xFF161926)
val InkLight = Color(0xFF202434)
val Indigo = Color(0xFF718AF8)
val IndigoDark = Color(0xFF5568D3)
val Violet = Color(0xFF8B68F6)
val Success = Color(0xFF34D399)
val Error = Color(0xFFF87171)
val Warning = Color(0xFFFBBF24)
val TextPrimary = Color.White
val TextSecondary = Color(0xFF9CA3B5)
val TextTertiary = Color(0xFF6B7285)
val Surface = Color.White.copy(alpha = 0.045f)
val SurfaceLight = Color.White.copy(alpha = 0.07f)
val SurfaceBorder = Color.White.copy(alpha = 0.09f)

private val BrandFont = FontFamily(Font(R.font.inter_bold, FontWeight.Bold))

private val WpayinTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

private val WpayinColors = darkColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Indigo.copy(alpha = 0.18f),
    secondary = Violet,
    background = Ink,
    onBackground = TextPrimary,
    surface = InkSurface,
    onSurface = TextPrimary,
    surfaceVariant = InkLight,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    error = Error,
)

@Composable
fun WpayinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WpayinColors,
        typography = WpayinTypography,
    ) {
        CompositionLocalProvider(LocalContentColor provides TextPrimary, content = content)
    }
}
