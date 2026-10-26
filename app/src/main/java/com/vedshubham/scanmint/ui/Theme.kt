package com.vedshubham.scanmint.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Mint {
    val Green = Color(0xFF19C38A)
    val Deep = Color(0xFF0F8F6A)
    val Ink = Color(0xFF0B3B2E)
    val Glow = Color(0xFF2CF0A8)
    val Gold = Color(0xFFFFCC33)
    val GoldInk = Color(0xFF5A3B00)
    val brush = Brush.linearGradient(listOf(Green, Deep))
}

private val Light = lightColorScheme(
    primary = Mint.Deep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF7E7),
    onPrimaryContainer = Mint.Ink,
    secondary = Mint.Green,
    background = Color(0xFFF4FAF7),
    onBackground = Color(0xFF12241E),
    surface = Color.White,
    onSurface = Color(0xFF12241E),
    surfaceVariant = Color(0xFFE6F1EC),
    onSurfaceVariant = Color(0xFF557068),
)

private val Dark = darkColorScheme(
    primary = Mint.Glow,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0D4A3A),
    onPrimaryContainer = Color(0xFFCFF7E7),
    secondary = Mint.Green,
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFE1ECE7),
    surface = Color(0xFF17201D),
    onSurface = Color(0xFFE1ECE7),
    surfaceVariant = Color(0xFF223029),
    onSurfaceVariant = Color(0xFF9DB5AC),
)

@Composable
fun ScanMintTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
