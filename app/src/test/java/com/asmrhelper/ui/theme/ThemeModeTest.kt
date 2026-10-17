package com.asmrhelper.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun followSystemResolvesToSystem() {
        assertTrue(ThemeMode.FOLLOW_SYSTEM.resolveDark(true))
        assertFalse(ThemeMode.FOLLOW_SYSTEM.resolveDark(false))
    }

    @Test
    fun manualOverridesSystem() {
        assertTrue(ThemeMode.DARK.resolveDark(false))
        assertFalse(ThemeMode.LIGHT.resolveDark(true))
    }

    @Test
    fun migrateKeepsExistingDarkUsersDark() {
        assertEquals(ThemeMode.DARK, migrateDarkThemeToThemeMode(true))
        assertEquals(ThemeMode.LIGHT, migrateDarkThemeToThemeMode(false))
        assertEquals(ThemeMode.FOLLOW_SYSTEM, migrateDarkThemeToThemeMode(null))
    }
}
