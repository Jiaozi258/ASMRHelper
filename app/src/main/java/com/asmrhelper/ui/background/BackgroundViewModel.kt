package com.asmrhelper.ui.background

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.BackgroundImage
import com.asmrhelper.domain.repository.AudioRepository
import com.asmrhelper.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackgroundViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val audioRepository: AudioRepository
) : ViewModel() {

    val backgroundImages: StateFlow<List<BackgroundImage>> =
        settingsRepository.getBackgroundImages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allAudio: StateFlow<List<Audio>> =
        audioRepository.getAllAudio()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addImage(name: String, filePath: String) {
        viewModelScope.launch {
            settingsRepository.addBackgroundImage(name, filePath)
        }
    }

    fun deleteImage(id: Long) {
        viewModelScope.launch {
            settingsRepository.deleteBackgroundImage(id)
        }
    }

    fun bindAudioToImage(audioId: Long, imageId: Long) {
        viewModelScope.launch {
            settingsRepository.bindAudioToImage(audioId, imageId)
        }
    }

    fun unbindAudioFromImage(audioId: Long, imageId: Long) {
        viewModelScope.launch {
            settingsRepository.unbindAudioFromImage(audioId, imageId)
        }
    }

    suspend fun getBinding(audioId: Long): BackgroundImage? {
        return settingsRepository.getBindingForAudio(audioId)
    }

    fun setCurrentBgImage(path: String?) {
        viewModelScope.launch {
            settingsRepository.setCurrentBgImagePath(path)
        }
    }
}
