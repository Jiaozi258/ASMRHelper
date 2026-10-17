package com.asmrhelper.data.music.netease

import com.asmrhelper.domain.model.music.QrLoginState
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseQrReducerTest {

    @Test
    fun code800IsExpired() {
        assertTrue(NeteaseQr.stateFor(800) is QrLoginState.Expired)
    }

    @Test
    fun code801IsWaiting() {
        assertTrue(NeteaseQr.stateFor(801) is QrLoginState.Waiting)
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
