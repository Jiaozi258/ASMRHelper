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
    MONOCHROME(R.string.theme_monochrome, Color(0xFFE0E0E0), Color(0xFF9E9E9E)),
    KLEIN_BLUE(R.string.theme_klein_blue, Color(0xFF002FA7), Color(0xFF003CB5)),
    DARK_GOLD(R.string.theme_dark_gold, Color(0xFFC9A227), Color(0xFFA8861F)),
    BRILLIANT_RED(R.string.theme_brilliant_red, Color(0xFFFF2D55), Color(0xFFD50000)),
    MINT_GREEN(R.string.theme_mint_green, Color(0xFF00C853), Color(0xFF00A84A)),
    CYAN_TEAL(R.string.theme_cyan_teal, Color(0xFF00BCD4), Color(0xFF0097A7)),
    LAVENDER(R.string.theme_lavender, Color(0xFFCE93D8), Color(0xFFAB47BC)),
    SAKURA_PINK(R.string.theme_sakura_pink, Color(0xFFFF80AB), Color(0xFFF06292));

    companion object {
        fun fromOrdinalOrDefault(ordinal: Int): ThemePreset =
            entries.getOrElse(ordinal) { APPLE_BLUE }
    }
}
