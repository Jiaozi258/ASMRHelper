package com.asmrhelper.domain.music

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceConfigTest {

    @Test
    fun presetsContainOnlyNeteaseAndQq() {
        assertEquals(listOf("netease", "qq"), SourcePresets.ALL.map { it.id })
    }

    @Test
    fun presetsHaveUniqueIds() {
        val ids = SourcePresets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun presetDisplayNames() {
        assertEquals("网易云音乐", SourcePresets.NETEASE.displayName)
        assertEquals("QQ音乐", SourcePresets.QQ.displayName)
    }
}
