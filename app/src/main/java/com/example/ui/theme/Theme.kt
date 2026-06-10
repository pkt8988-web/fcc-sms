package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BentoColorScheme = darkColorScheme(
    primary = BentoPrimary,
    onPrimary = BentoOnPrimary,
    primaryContainer = BentoPrimaryContainer,
    secondary = BentoSecondary,
    onSecondary = BentoOnSecondary,
    tertiary = BentoTertiary,
    onTertiary = BentoOnTertiary,
    background = BentoDarkBg,
    onBackground = BentoTextLight,
    surface = BentoTileBg,
    onSurface = BentoTextLight,
    surfaceVariant = BentoCardBg,
    onSurfaceVariant = BentoTextMuted,
    outline = BentoBorder,
    error = BentoWicketRed,
    onError = BentoWicketText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force modern dark Bento styling as requested
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BentoColorScheme,
        typography = Typography,
        content = content
    )
}
