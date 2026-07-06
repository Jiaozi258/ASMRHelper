package com.asmrhelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ASMRHelperTheme(
    preset: ThemePreset = ThemePreset.PURPLE,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = preset.accent,
        secondary = preset.accentVariant,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceVariant = DarkSurfaceVariant,
        onPrimary = ControlWhite,
        onSecondary = ControlWhite,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        error = ErrorRed
    )

    CompositionLocalProvider(LocalAccentColor provides preset.accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
