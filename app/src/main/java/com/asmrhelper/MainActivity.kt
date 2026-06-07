package com.asmrhelper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.asmrhelper.domain.repository.SettingsRepository
import com.asmrhelper.ui.navigation.AsmrNavHost
import com.asmrhelper.ui.theme.ASMRHelperTheme
import com.asmrhelper.ui.theme.ThemePreset
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    companion object {
        const val KEY_PENDING_VIDEO_URL = "pending_video_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        CrashHandler.showLastCrash(this)

        // Handle share intent (video URL from external apps e.g. Bilibili, YouTube)
        if (Intent.ACTION_SEND == intent?.action && intent?.type == "text/plain") {
            val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            if (sharedUrl.startsWith("http")) {
                getSharedPreferences("asmr_settings", MODE_PRIVATE)
                    .edit().putString(KEY_PENDING_VIDEO_URL, sharedUrl).apply()
            }
        }

        val presetOrdinal = getSharedPreferences("asmr_settings", MODE_PRIVATE)
            .getInt("theme_preset", 0)

        setContent {
            val ordinal by settingsRepository.getThemePresetOrdinal()
                .collectAsState(initial = presetOrdinal)
            val preset = ThemePreset.fromOrdinalOrDefault(ordinal)

            ASMRHelperTheme(preset = preset) {
                AsmrNavHost()
            }
        }
    }
}
