package com.tradingmaster.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TmDark = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF04140B),
    secondary = Color(0xFF60A5FA),
    surface = Color(0xFF0F172A),
    background = Color(0xFF080C15),
    error = Color(0xFFEF4444)
)

@Composable
fun TmTheme(content: @Composable () -> Unit) {
    // Trading UIs are always dark; system light mode does not switch it.
    MaterialTheme(colorScheme = TmDark, content = content)
}
