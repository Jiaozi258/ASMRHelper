package com.asmrhelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ASMRHelperTheme(
    preset: ThemePreset = ThemePreset.APPLE_BLUE,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = preset.accent,
            secondary = preset.accentVariant,
            background = appColors.background,
            surface = appColors.surface,
            surfaceVariant = appColors.surfaceVariant,
            onPrimary = appColors.controlWhite,
            onSecondary = appColors.controlWhite,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary,
            onSurfaceVariant = appColors.textSecondary,
            error = appColors.errorRed
        )
    } else {
        lightColorScheme(
            primary = preset.accent,
            secondary = preset.accentVariant,
            background = appColors.background,
            surface = appColors.surface,
            surfaceVariant = appColors.surfaceVariant,
            onPrimary = appColors.controlWhite,
            onSecondary = appColors.controlWhite,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary,
            onSurfaceVariant = appColors.textSecondary,
            error = appColors.errorRed
        )
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalAccentColor provides preset.accent
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
