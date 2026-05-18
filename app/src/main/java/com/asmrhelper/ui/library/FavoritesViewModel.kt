package com.asmrhelper.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.data.local.scanner.AudioScanner
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.repository.AudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val audioScanner: AudioScanner
) : ViewModel() {

    val allAudio: StateFlow<List<Audio>> = audioRepository.getAllAudio()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<Audio>> = audioRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<Audio>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else flow { emit(audioRepository.searchAudio(query)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanResult = MutableSharedFlow<String>()
    val scanResult = _scanResult.asSharedFlow()

    fun toggleFavorite(audioId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            audioRepository.updateFavorite(audioId, isFavorite)
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun scanAndImport() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val scanned = audioScanner.scanMediaStore()
                if (scanned.isEmpty()) {
                    _scanResult.emit("未找到音频文件，请检查权限或在「文件管理」中手动浏览")
                } else {
                    scanned.forEach { audio -> audioRepository.addAudio(audio) }
                    _scanResult.emit("已导入 ${scanned.size} 首音频")
                }
            } catch (e: Exception) {
                _scanResult.emit("扫描失败: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
    }
}
