package com.asmrhelper

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.asmrhelper.domain.repository.SettingsRepository
import com.asmrhelper.ui.navigation.AsmrNavHost
import com.asmrhelper.ui.theme.ASMRHelperTheme
import com.asmrhelper.ui.theme.ThemePreset
import com.asmrhelper.util.ShareReceiver
import com.asmrhelper.util.ShortcutReceiver
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
        handleShortcutIntent(intent)

        // Request notification permission on Android 13+ if not yet granted.
        // Only on first creation, not on recreation (rotation etc.) to avoid
        // repeatedly showing the dialog.
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val prefs = getSharedPreferences("asmr_settings", MODE_PRIVATE)
        val presetOrdinal = prefs.getInt("theme_preset", 0)
        val darkThemeDefault = prefs.getBoolean("dark_theme", true)

        setContent {
            val ordinal by settingsRepository.getThemePresetOrdinal()
                .collectAsState(initial = presetOrdinal)
            val preset = ThemePreset.fromOrdinalOrDefault(ordinal)
            val darkTheme by settingsRepository.getDarkTheme()
                .collectAsState(initial = darkThemeDefault)

            // 浅色模式下系统状态栏/导航栏图标需切为深色，否则白图标压在浅灰背景上不可见
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }

            ASMRHelperTheme(preset = preset, darkTheme = darkTheme) {
                AsmrNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // so onCreate sees the latest intent on recreation
        handleShareIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        val action = intent?.getStringExtra("shortcut_action") ?: return
        ShortcutReceiver.receive(action)
    }

    private fun handleShareIntent(intent: Intent) {
        // Accept ACTION_SEND with text content. Some apps (e.g. Bilibili) may set
        // type to "text/plain", "text/html", or leave it null — be lenient.
        if (Intent.ACTION_SEND == intent.action) {
            val type = intent.type
            if (type != null && !type.startsWith("text/")) return
            val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            if (sharedUrl.startsWith("http")) {
                // Use reactive StateFlow so Compose auto-navigates to video tab
                ShareReceiver.receive(sharedUrl)
            }
        }
    }
}
