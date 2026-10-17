package com.asmrhelper.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLocaleTest {

    @Test
    fun localeForFollowsSystem() {
        assertNull(AppLocale.localeFor(0))
        assertNull(AppLocale.localeFor(99))
    }

    @Test
    fun localeForZhAndEn() {
        assertEquals(Locale("zh"), AppLocale.localeFor(1))
        assertEquals(Locale("en"), AppLocale.localeFor(2))
    }
}
