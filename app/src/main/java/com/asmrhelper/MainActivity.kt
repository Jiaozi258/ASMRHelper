package com.asmrhelper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.asmrhelper.domain.repository.SettingsRepository
import com.asmrhelper.ui.navigation.AsmrNavHost
import com.asmrhelper.ui.theme.ASMRHelperTheme
import com.asmrhelper.ui.theme.ThemePreset
import com.asmrhelper.util.ShareReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** Android 13+ requires runtime permission for notifications. Without this,
     *  the foreground service notification is silently suppressed by the system. */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — service will check pref on next start */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        CrashHandler.showLastCrash(this)
        intent?.let { handleShareIntent(it) }

        // Request notification permission on Android 13+ if not yet granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        // Accept ACTION_SEND with text content. Some apps (e.g. Bilibili) may set
        // type to "text/plain", "text/html", or leave it null — be lenient.
        if (Intent.ACTION_SEND == intent.action) {
            if (intent.type != null && !intent.type!!.startsWith("text/")) return
            val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            if (sharedUrl.startsWith("http")) {
                // Use reactive StateFlow so Compose auto-navigates to video tab
                ShareReceiver.receive(sharedUrl)
            }
        }
    }
}
