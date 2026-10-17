package com.asmrhelper.data.music.netease

import com.asmrhelper.domain.model.music.QrLoginState
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseQrReducerTest {

    @Test
    fun code800IsExpired() {
        assertTrue(NeteaseQr.stateFor(800) is QrLoginState.Expired)
    }

    @Test
    fun code801IsWaitingWithNullImage() {
        val state = NeteaseQr.stateFor(801)
        assertTrue(state is QrLoginState.Waiting)
        assertNull((state as QrLoginState.Waiting).qrImageBase64)
    }

    @Test
    fun code802IsScanned() {
        assertTrue(NeteaseQr.stateFor(802) is QrLoginState.Scanned)
    }

    @Test
    fun code803IsConfirmed() {
        assertTrue(NeteaseQr.stateFor(803) is QrLoginState.Confirmed)
    }

    @Test
    fun otherCodeIsFailed() {
        assertTrue(NeteaseQr.stateFor(999) is QrLoginState.Failed)
    }
}
