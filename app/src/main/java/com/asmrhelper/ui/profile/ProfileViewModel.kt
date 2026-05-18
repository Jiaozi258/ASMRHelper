package com.asmrhelper.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.domain.repository.AudioRepository
import com.asmrhelper.domain.repository.PlaylistRepository
import com.asmrhelper.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("asmr_profile", Context.MODE_PRIVATE)

    // ── 登录状态 ──────────────────────────────────────────

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean("is_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _username = MutableStateFlow(prefs.getString("username", "") ?: "")
    val username: StateFlow<String> = _username.asStateFlow()

    fun login(name: String, password: String) {
        _isLoggedIn.value = true
        _username.value = name
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("username", name)
            .apply()
    }

    fun register(name: String, password: String) {
        // 模拟注册：直接保存
        login(name, password)
    }

    fun logout() {
        _isLoggedIn.value = false
        _username.value = ""
        prefs.edit()
            .remove("is_logged_in")
            .remove("username")
            .apply()
    }

    // ── 统计 ──────────────────────────────────────────────

    val audioCount: StateFlow<Int> = audioRepository.getAllAudio()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val playlistCount: StateFlow<Int> = playlistRepository.getAllPlaylists()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val bgImageCount: StateFlow<Int> = settingsRepository.getBackgroundImages()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
