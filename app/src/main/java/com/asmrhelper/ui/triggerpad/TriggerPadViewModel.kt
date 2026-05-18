package com.asmrhelper.ui.triggerpad

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.asmrhelper.data.local.db.entity.TriggerPadEntity
import com.asmrhelper.data.repository.TriggerPadRepositoryImpl
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.player.PlayerEvent
import com.asmrhelper.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TriggerPadMode { Independent, Parallel }

data class TriggerPadUiState(
    val pads: List<TriggerPadEntity> = emptyList(),
    val playingIndex: Int = -1,
    val mode: TriggerPadMode = TriggerPadMode.Independent,
    val activeParallelSlots: Set<Int> = emptySet()
)

@HiltViewModel
class TriggerPadViewModel @Inject constructor(
    private val repository: TriggerPadRepositoryImpl,
    private val playerManager: PlayerManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TriggerPadUiState())
    val uiState: StateFlow<TriggerPadUiState> = _uiState.asStateFlow()

    private val parallelPlayers = mutableMapOf<Int, ExoPlayer>()

    init {
        viewModelScope.launch {
            repository.getAll().collect { pads ->
                _uiState.update { it.copy(pads = pads) }
            }
        }
    }

    fun assignToSlot(slotIndex: Int, name: String, filePath: String) {
        viewModelScope.launch {
            repository.save(TriggerPadEntity(slotIndex = slotIndex, name = name, filePath = filePath))
        }
    }

    fun removeFromSlot(slotIndex: Int) {
        viewModelScope.launch {
            stopParallelSlot(slotIndex)
            if (_uiState.value.playingIndex == slotIndex) {
                playerManager.handleEvent(PlayerEvent.Pause)
                _uiState.update { it.copy(playingIndex = -1) }
            }
            repository.deleteBySlot(slotIndex)
        }
    }

    fun toggleMode() {
        val current = _uiState.value.mode
        val next = if (current == TriggerPadMode.Independent) TriggerPadMode.Parallel
                    else TriggerPadMode.Independent
        // Stop all playback when switching modes
        if (next == TriggerPadMode.Independent) {
            stopAllParallel()
        } else {
            playerManager.handleEvent(PlayerEvent.Pause)
            _uiState.update { it.copy(playingIndex = -1) }
        }
        _uiState.update { it.copy(mode = next) }
    }

    fun playSlot(filePath: String, slotIndex: Int) {
        if (_uiState.value.mode == TriggerPadMode.Parallel) {
            toggleParallelSlot(filePath, slotIndex)
        } else {
            playIndependentSlot(filePath, slotIndex)
        }
    }

    private fun playIndependentSlot(filePath: String, slotIndex: Int) {
        val audio = Audio(
            id = -slotIndex.toLong() - 1,
            title = "Trigger $slotIndex",
            artist = "",
            filePath = filePath,
            durationMs = 0L,
            isFavorite = false
        )
        playerManager.handleEvent(PlayerEvent.Play(audio, emptyList()))
        _uiState.update { it.copy(playingIndex = slotIndex) }
    }

    private fun toggleParallelSlot(filePath: String, slotIndex: Int) {
        val active = _uiState.value.activeParallelSlots
        if (slotIndex in active) {
            stopParallelSlot(slotIndex)
            _uiState.update { it.copy(activeParallelSlots = active - slotIndex) }
        } else {
            startParallelSlot(filePath, slotIndex)
            _uiState.update { it.copy(activeParallelSlots = active + slotIndex) }
        }
    }

    private fun startParallelSlot(filePath: String, slotIndex: Int) {
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(filePath))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            play()
        }
        parallelPlayers[slotIndex] = player
    }

    private fun stopParallelSlot(slotIndex: Int) {
        parallelPlayers[slotIndex]?.let {
            it.stop()
            it.release()
        }
        parallelPlayers.remove(slotIndex)
    }

    private fun stopAllParallel() {
        parallelPlayers.keys.toList().forEach { stopParallelSlot(it) }
        _uiState.update { it.copy(activeParallelSlots = emptySet()) }
    }

    override fun onCleared() {
        super.onCleared()
        stopAllParallel()
    }
}
