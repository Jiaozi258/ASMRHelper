package com.asmrhelper.domain.model

import androidx.annotation.StringRes
import com.asmrhelper.R

/**
 * Represents an ambient sound source — either a user-imported file
 * or a built-in OGG resource shipped with the app.
 */
data class AmbientSource(
    @StringRes val labelRes: Int,
    val sourcePath: String,
    val isBuiltIn: Boolean
) {
    companion object {
        /** 6 built-in nature sound loops in res/raw/. */
        val BUILT_IN = listOf(
            AmbientSource(R.string.ambient_rain, "builtin:ambient_rain", isBuiltIn = true),
            AmbientSource(R.string.ambient_stream, "builtin:ambient_stream", isBuiltIn = true),
            AmbientSource(R.string.ambient_campfire, "builtin:ambient_campfire", isBuiltIn = true),
            AmbientSource(R.string.ambient_wind, "builtin:ambient_wind", isBuiltIn = true),
            AmbientSource(R.string.ambient_thunder, "builtin:ambient_thunder", isBuiltIn = true),
            AmbientSource(R.string.ambient_ocean, "builtin:ambient_ocean", isBuiltIn = true),
        )
    }
}
