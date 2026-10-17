package com.asmrhelper.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModeTest {
    @Test
    fun asmrShowsAllMenuItems() {
        assertTrue(PlaybackMode.ASMR.isMenuItemVisible("trigger_pad"))
        assertTrue(PlaybackMode.ASMR.isMenuItemVisible("sleep_journal"))
        assertTrue(PlaybackMode.ASMR.isMenuItemVisible("favorites"))
    }

    @Test
    fun musicHidesAsmrOnlyItems() {
        assertFalse(PlaybackMode.MUSIC.isMenuItemVisible("trigger_pad"))
        assertFalse(PlaybackMode.MUSIC.isMenuItemVisible("sleep_journal"))
        assertTrue(PlaybackMode.MUSIC.isMenuItemVisible("favorites"))
        assertTrue(PlaybackMode.MUSIC.isMenuItemVisible("background"))
    }
}
