package com.example.disasterlink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DisasterBlue = Color(0xFF174EA6)
private val DisasterBlueDark = Color(0xFF0B3D91)
private val DisasterGreen = Color(0xFF2E8B57)
private val DisasterRed = Color(0xFFE53935)
private val AppBackground = Color(0xFFF6F8FC)

private val LightColors = lightColorScheme(
    primary = DisasterBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF0A2D68),
    secondary = DisasterGreen,
    tertiary = Color(0xFFFFA726),
    error = DisasterRed,
    background = AppBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFEFF3F8)
)

@Composable
fun DisasterLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content
    )
}
