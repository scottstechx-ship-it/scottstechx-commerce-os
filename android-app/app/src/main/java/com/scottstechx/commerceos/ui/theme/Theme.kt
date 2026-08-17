package com.scottstechx.commerceos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ScottsGreen = Color(0xFF1B5E20)
private val ScottsGreenLight = Color(0xFF4C8C4A)
private val ScottsAccent = Color(0xFFFFB300)

private val LightColors = lightColorScheme(
    primary = ScottsGreen,
    onPrimary = Color.White,
    primaryContainer = ScottsGreenLight,
    onPrimaryContainer = Color.White,
    secondary = ScottsAccent,
    onSecondary = Color.Black,
    background = Color(0xFFFAFAFA),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = ScottsGreenLight,
    onPrimary = Color.Black,
    secondary = ScottsAccent,
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

/**
 * Bumped-typography scale for low-literacy users. Material 3 defaults
 * use 16sp body text; we use 18sp as the baseline and scale every other
 * style up proportionally so the entire UI reads larger without the user
 * having to enable system font scaling.
 *
 * This is a deliberate accessibility choice for the Uganda marketplace
 * slice, where some users are first-time smartphone owners.
 */
private val LargeTypeTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    bodySmall = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun ScottsTechXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = LargeTypeTypography, content = content)
}
