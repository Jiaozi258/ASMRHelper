package com.asmrhelper.data.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceConfigValidatorTest {
    @Test
    fun validHttpUrl() {
        assertTrue(MusicSourceManager.validateBaseUrl("https://api.example.com"))
    }

    @Test
    fun rejectsBlankAndGarbage() {
        assertFalse(MusicSourceManager.validateBaseUrl(""))
        assertFalse(MusicSourceManager.validateBaseUrl("not a url"))
    }
}
