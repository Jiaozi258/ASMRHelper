package com.asmrhelper.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrImageBase64Test {

    @Test
    fun nullRawIsNull() {
        assertNull(QrImageBase64.payload(null))
    }

    @Test
    fun blankRawIsNull() {
        assertNull(QrImageBase64.payload("   "))
    }

    @Test
    fun stripsDataUriPrefix() {
        assertEquals("AAAA", QrImageBase64.payload("data:image/png;base64,AAAA"))
    }

    @Test
    fun keepsRawBase64() {
        assertEquals("AAAA", QrImageBase64.payload("AAAA"))
    }
}
