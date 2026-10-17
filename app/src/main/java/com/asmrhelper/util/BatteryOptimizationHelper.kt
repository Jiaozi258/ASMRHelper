package com.asmrhelper.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.asmrhelper.R

/**
 * Helps the user whitelist this app from battery optimization so background
 * playback survives the ~10-minute OEM process-kill window on Chinese ROMs
 * (MIUI, ColorOS, OriginOS, HarmonyOS/EMUI).
 */
object BatteryOptimizationHelper {

    private const val PREFS_NAME = "asmr_settings"
    private const val KEY_DIALOG_SHOWN_COUNT = "battery_dialog_shown_count"
    private const val MAX_DIALOG_SHOWS = 3

    // ── Public API ─────────────────────────────────────────

    /** True if the app is already whitelisted — no action needed. */
    fun isWhitelisted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Open the system battery optimization whitelist setting for this app. */
    fun openBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: open generic battery optimization screen
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            } catch (_: Exception) { }
        }
    }

    /** Open the app's own settings page (for Chinese ROMs that bury
     *  the autostart / background-running permission deep in their
     *  system settings → Apps → AppName path). */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    // ── Dialog throttling ──────────────────────────────────

    /**
     * Returns true if it's appropriate to show the battery-optimization
     * guidance dialog (not whitelisted AND dialog hasn't been shown too many
     * times).
     */
    fun shouldShowDialog(context: Context): Boolean {
        if (isWhitelisted(context)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_DIALOG_SHOWN_COUNT, 0)
        return count < MAX_DIALOG_SHOWS
    }

    /** Record that the dialog was shown. */
    fun recordDialogShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_DIALOG_SHOWN_COUNT, 0)
        prefs.edit().putInt(KEY_DIALOG_SHOWN_COUNT, count + 1).apply()
    }

    /** Reset the dialog counter (for testing or after user whitelists). */
    fun resetDialogCount(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_DIALOG_SHOWN_COUNT).apply()
    }

    // ── Chinese ROM detection ──────────────────────────────

    /** Returns true if running on a ROM known to aggressively kill
     *  background processes (MIUI, ColorOS, OriginOS, EMUI/HarmonyOS). */
    fun isChineseRom(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi")
            || manufacturer.contains("oppo")
            || manufacturer.contains("vivo")
            || manufacturer.contains("huawei")
            || manufacturer.contains("honor")
            || brand.contains("xiaomi")
            || brand.contains("oppo")
            || brand.contains("vivo")
            || brand.contains("huawei")
            || brand.contains("honor")
            || brand.contains("redmi")
            || brand.contains("realme")
            || brand.contains("oneplus")
    }

    /** Human-readable ROM-specific guidance for the autostart / background
     *  running permission path. */
    fun getRomGuidance(context: Context): String? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi") ->
                context.getString(R.string.battery_guidance_xiaomi)

            manufacturer.contains("oppo") || brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") ->
                context.getString(R.string.battery_guidance_oppo)

            manufacturer.contains("vivo") || brand.contains("vivo") ->
                context.getString(R.string.battery_guidance_vivo)

            manufacturer.contains("huawei") || brand.contains("huawei") || brand.contains("honor") ->
                context.getString(R.string.battery_guidance_huawei)

            else -> null
        }
    }
}
