package com.asmrhelper.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.asmrhelper.R

enum class ThemePreset(@StringRes val labelRes: Int, val accent: Color, val accentVariant: Color) {
    APPLE_BLUE(R.string.theme_apple_blue, Color(0xFF0071E3), Color(0xFF0077ED)),
    PURPLE(R.string.theme_purple, Color(0xFFBB86FC), Color(0xFF9C64E8)),
    WARM_ORANGE(R.string.theme_warm_orange, Color(0xFFFF8A65), Color(0xFFE67A5A)),
    FOREST_GREEN(R.string.theme_forest_green, Color(0xFF81C784), Color(0xFF66BB6A)),
    OCEAN_BLUE(R.string.theme_ocean_blue, Color(0xFF64B5F6), Color(0xFF42A5F5)),
    ROSE_GOLD(R.string.theme_rose_gold, Color(0xFFF48FB1), Color(0xFFEC407A)),
    MONOCHROME(R.string.theme_monochrome, Color(0xFFE0E0E0), Color(0xFF9E9E9E));

    companion object {
        fun fromOrdinalOrDefault(ordinal: Int): ThemePreset =
            entries.getOrElse(ordinal) { APPLE_BLUE }
    }
}
