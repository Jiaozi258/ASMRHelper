package com.asmrhelper.domain.model

/**
 * Represents an ambient sound source — either a user-imported file
 * or a built-in OGG resource shipped with the app.
 */
data class AmbientSource(
    val label: String,
    val sourcePath: String,
    val isBuiltIn: Boolean
) {
    companion object {
        /** 6 built-in nature sound loops in res/raw/. */
        val BUILT_IN = listOf(
            AmbientSource("🌧️ 雨声", "builtin:ambient_rain", isBuiltIn = true),
            AmbientSource("🌊 溪流", "builtin:ambient_stream", isBuiltIn = true),
            AmbientSource("🔥 篝火", "builtin:ambient_campfire", isBuiltIn = true),
            AmbientSource("💨 风声", "builtin:ambient_wind", isBuiltIn = true),
            AmbientSource("⚡ 雷声", "builtin:ambient_thunder", isBuiltIn = true),
            AmbientSource("🌊 海浪", "builtin:ambient_ocean", isBuiltIn = true),
        )
    }
}
