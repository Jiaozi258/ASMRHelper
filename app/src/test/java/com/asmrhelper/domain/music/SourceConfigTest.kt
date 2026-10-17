package com.asmrhelper.domain.music

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceConfigTest {
    @Test
    fun presetsHaveUniqueIds() {
        val ids = SourcePresets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun customConfigIsCustom() {
        assertTrue(SourceConfig("x", "X", "http://a", isCustom = true).isCustom)
    }
}
