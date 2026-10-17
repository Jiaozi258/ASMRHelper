package com.asmrhelper.domain.music

import com.asmrhelper.R
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
    fun presetDisplayNameResources() {
        assertEquals(R.string.source_netease, SourcePresets.NETEASE.displayNameRes)
        assertEquals(R.string.source_qq, SourcePresets.QQ.displayNameRes)
    }
}
