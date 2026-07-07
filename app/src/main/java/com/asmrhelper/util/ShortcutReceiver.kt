package com.asmrhelper.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive bridge between app shortcut intents and Compose navigation.
 * When the user taps a long-press shortcut, MainActivity posts the action here.
 * AsmrNavHost observes the flow and navigates accordingly.
 */
object ShortcutReceiver {
    private val _pendingAction = MutableStateFlow("")
    val pendingAction: StateFlow<String> = _pendingAction.asStateFlow()

    fun receive(action: String) {
        _pendingAction.value = action
    }

    /** Call after the action has been consumed by the UI. */
    fun consume() {
        _pendingAction.value = ""
    }
}
