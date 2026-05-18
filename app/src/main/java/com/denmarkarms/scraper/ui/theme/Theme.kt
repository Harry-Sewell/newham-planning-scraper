package com.denmarkarms.scraper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue800 = Color(0xFF1565C0)
private val Blue100 = Color(0xFFBBDEFB)
private val Blue900 = Color(0xFF0D47A1)
private val Teal600 = Color(0xFF00897B)
private val Teal100 = Color(0xFFB2DFDB)
private val Orange700 = Color(0xFFF57C00)
private val BlueGrey50 = Color(0xFFF0F4F8)

private val LightColors = lightColorScheme(
    primary = Blue800,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Teal600,
    onSecondary = Color.White,
    secondaryContainer = Teal100,
    onSecondaryContainer = Color(0xFF00352F),
    tertiary = Orange700,
    onTertiary = Color.White,
    background = BlueGrey50,
    surface = Color.White,
    onBackground = Color(0xFF1A1C1E),
    onSurface = Color(0xFF1A1C1E),
    outline = Color(0xFF72777F)
)

@Composable
fun DenmarkArmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
