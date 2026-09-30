package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GameNetDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),       // Clean Minimalism primary: lavender
    onPrimary = Color(0xFF381E72),     // Dark purple
    secondary = Color(0xFF49454F),     // Medium grey
    onSecondary = Color(0xFFE6E1E5),   // Light text
    tertiary = Color(0xFFB6F2AF),      // Light green active accent
    onTertiary = Color(0xFF06180E),
    background = Color(0xFF1C1B1F),    // Dark background #1C1B1F
    onBackground = Color(0xFFE6E1E5),  // Text light #E6E1E5
    surface = Color(0xFF2B2930),       // Card container background #2B2930
    onSurface = Color(0xFFE6E1E5),
    error = Color(0xFFF2B8B5),         // Paused / Error / Alarm orange-red
    onError = Color(0xFF601410),
    outline = Color(0xFF49454F),       // Borders
    outlineVariant = Color(0xFF938F99) // Muted text
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF424242),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF388E3C),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFF9E9E9E)
)

private val MatteNeonColorScheme = darkColorScheme(
    primary = Color(0xFF4DD0E1),       // Matte Neon Cyan
    onPrimary = Color(0xFF00363D),
    secondary = Color(0xFFFF79B0),     // Matte Neon Pink
    onSecondary = Color(0xFF3E0021),
    tertiary = Color(0xFFB388FF),      // Matte Neon Purple
    onTertiary = Color(0xFF23005C),
    background = Color(0xFF1A1A1D),    // Dark matte background
    onBackground = Color(0xFFE4E4E6),
    surface = Color(0xFF252529),       // Matte card container
    onSurface = Color(0xFFE4E4E6),
    error = Color(0xFFFF5252),         // Matte Neon Red
    onError = Color(0xFF410002),
    outline = Color(0xFF4A4A52),       // Borders
    outlineVariant = Color(0xFF313136) // Muted text/borders
)


@Composable
fun MyApplicationTheme(
    themeName: String = "دارک",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeName) {
        "سفید" -> LightColorScheme
        "نئون مات" -> MatteNeonColorScheme
        else -> GameNetDarkColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
