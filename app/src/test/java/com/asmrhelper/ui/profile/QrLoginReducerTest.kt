package com.asmrhelper.ui.profile

import com.asmrhelper.domain.model.music.QrLoginState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrLoginReducerTest {
    @Test
    fun expiredShouldRestart() {
        assertTrue(QrLoginReducer.shouldRestart(QrLoginState.Expired("k")))
    }

    @Test
    fun confirmedShouldNotRestart() {
        assertFalse(QrLoginReducer.shouldRestart(QrLoginState.Confirmed("u")))
    }
}
