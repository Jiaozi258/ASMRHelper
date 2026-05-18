package com.asmrhelper.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.data.local.db.entity.SleepJournalEntity
import com.asmrhelper.data.repository.SleepJournalRepositoryImpl
import com.asmrhelper.data.repository.SleepJournalRepositoryImpl.SleepStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class SleepJournalUiState(
    val entries: List<SleepJournalEntity> = emptyList(),
    val stats: SleepStats = SleepStats(),
    // Active tracking
    val isTracking: Boolean = false,
    val activeEntryId: Long? = null,
    val trackingStartMs: Long = 0L,
    val elapsedMs: Long = 0L,
    // Quality dialog
    val showQualityDialog: Boolean = false,
    val qualityEntryId: Long? = null
)

@HiltViewModel
class SleepJournalViewModel @Inject constructor(
    private val repository: SleepJournalRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepJournalUiState())
    val uiState: StateFlow<SleepJournalUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getAll().collect { entries ->
                _uiState.update { it.copy(entries = entries) }
            }
        }
        viewModelScope.launch {
            val stats = repository.getStats()
            _uiState.update { it.copy(stats = stats) }
        }
    }

    fun startTracking() {
        val now = System.currentTimeMillis()
        val today = getTodayEpoch()
        tickJob?.cancel()
        _uiState.update { it.copy(isTracking = true, trackingStartMs = now, elapsedMs = 0L) }
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update { current ->
                    if (current.isTracking)
                        current.copy(elapsedMs = System.currentTimeMillis() - current.trackingStartMs)
                    else current
                }
            }
        }
    }

    fun stopTracking() {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val durationMin = ((now - state.trackingStartMs) / 60000).toInt()
        tickJob?.cancel()

        viewModelScope.launch {
            val entry = SleepJournalEntity(
                date = getTodayEpoch(),
                startTimeMs = state.trackingStartMs,
                endTimeMs = now,
                durationMinutes = durationMin
            )
            val id = repository.insert(entry)
            _uiState.update {
                it.copy(
                    isTracking = false,
                    activeEntryId = id,
                    elapsedMs = 0L,
                    qualityEntryId = id,
                    showQualityDialog = true
                )
            }
            refreshStats()
        }
    }

    fun cancelTracking() {
        tickJob?.cancel()
        _uiState.update { it.copy(isTracking = false, elapsedMs = 0L, trackingStartMs = 0L) }
    }

    fun setQuality(quality: Int, notes: String) {
        val entryId = _uiState.value.qualityEntryId ?: return
        viewModelScope.launch {
            repository.saveQualityAndNotes(entryId, quality, notes)
            _uiState.update { it.copy(showQualityDialog = false, qualityEntryId = null) }
            refreshStats()
        }
    }

    fun dismissQualityDialog() {
        _uiState.update { it.copy(showQualityDialog = false, qualityEntryId = null) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            refreshStats()
        }
    }

    private fun refreshStats() {
        viewModelScope.launch {
            val stats = repository.getStats()
            _uiState.update { it.copy(stats = stats) }
        }
    }

    private fun getTodayEpoch(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
