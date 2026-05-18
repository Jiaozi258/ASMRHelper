package com.asmrhelper.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemePreset(val label: String, val accent: Color, val accentVariant: Color) {
    PURPLE("暗夜紫", Color(0xFFBB86FC), Color(0xFF9C64E8)),
    WARM_ORANGE("暖橙", Color(0xFFFF8A65), Color(0xFFE67A5A)),
    FOREST_GREEN("森林绿", Color(0xFF81C784), Color(0xFF66BB6A)),
    OCEAN_BLUE("海洋蓝", Color(0xFF64B5F6), Color(0xFF42A5F5)),
    ROSE_GOLD("玫瑰金", Color(0xFFF48FB1), Color(0xFFEC407A)),
    MONOCHROME("极简灰", Color(0xFFE0E0E0), Color(0xFF9E9E9E));

    companion object {
        fun fromOrdinalOrDefault(ordinal: Int): ThemePreset =
            entries.getOrElse(ordinal) { PURPLE }
    }
}
