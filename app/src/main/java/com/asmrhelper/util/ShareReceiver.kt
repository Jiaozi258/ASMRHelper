package com.asmrhelper.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive bridge between Activity share intent and Compose navigation.
 * When an external app shares a URL to ASMRHelper, MainActivity posts it here.
 * AsmrNavHost observes the flow and auto-navigates to the video audio tab.
 */
object ShareReceiver {
    private val _pendingUrl = MutableStateFlow("")
    val pendingUrl: StateFlow<String> = _pendingUrl.asStateFlow()

    fun receive(url: String) {
        _pendingUrl.value = url
    }

    /** Call after the URL has been consumed by the UI. */
    fun consume() {
        _pendingUrl.value = ""
    }
}
