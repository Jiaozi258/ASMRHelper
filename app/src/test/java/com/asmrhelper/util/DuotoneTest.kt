package com.asmrhelper.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DuotoneTest {
    @Test
    fun darkenScalesEachChannelByPercent() {
        assertEquals(0xFF1C3854L, Duotone.darken(0xFF336699L, 55))
    }

    @Test
    fun swapRedBlueExchangesChannels() {
        assertEquals(0xFF996633L, Duotone.swapRedBlue(0xFF336699L))
    }

    @Test
    fun duotoneReturnsDarkPrimaryAndSwappedSecondary() {
        val c = Duotone.duotone(0xFF336699L)
        assertEquals(0xFF1C3854L, c.primary)
        assertEquals(0xFF996633L, c.secondary)
    }
}
