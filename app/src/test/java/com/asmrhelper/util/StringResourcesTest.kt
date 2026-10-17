package com.asmrhelper.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 守卫：values 与 values-en 的 string 键必须完全一致（不漏译、不多译）。 */
class StringResourcesTest {

    @Test
    fun valuesAndValuesEnHaveSameKeys() {
        val values = readKeys(resolve("src/main/res/values/strings.xml"))
        val en = readKeys(resolve("src/main/res/values-en/strings.xml"))
        assertEquals("values-en 与 values 的 string 键不一致", values, en)
    }

    private fun resolve(rel: String): File {
        val direct = File(rel)
        if (direct.exists()) return direct
        val fromModuleRoot = File("app", rel)
        assertTrue("cannot locate $rel", fromModuleRoot.exists())
        return fromModuleRoot
    }

    private fun readKeys(file: File): Set<String> {
        val text = file.readText()
        return Regex("""<string name="([^"]+)">""").findAll(text)
            .map { it.groupValues[1] }
            .toSet()
    }
}
