package com.example.paylist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PayListColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    secondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFF386A20),
    error = Color(0xFFB3261E),
    surface = Color(0xFFFFFBFE),
    background = Color(0xFFFFFBFE)
)

@Composable
fun PayListTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PayListColors, content = content)
}
