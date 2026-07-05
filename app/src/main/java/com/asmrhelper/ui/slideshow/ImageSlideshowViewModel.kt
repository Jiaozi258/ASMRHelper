package com.asmrhelper.ui.slideshow

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.data.local.db.entity.ImageLibraryEntity
import com.asmrhelper.data.repository.ImageLibraryRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SlideshowMode { Manual, Auto, Timed }

data class SlideshowState(
    val images: List<ImageLibraryEntity> = emptyList(),
    val currentIndex: Int = 0,
    val mode: SlideshowMode = SlideshowMode.Manual,
    val autoIntervalSec: Int = 5,
    val timePoints: List<Long> = emptyList(),  // in ms from song start
    val isImporting: Boolean = false
)

@HiltViewModel
class ImageSlideshowViewModel @Inject constructor(
    private val repository: ImageLibraryRepositoryImpl,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SlideshowState())
    val state: StateFlow<SlideshowState> = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.getAll().collect { images ->
                _state.value = _state.value.copy(images = images)
            }
        }
    }

    fun importFromUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isImporting = true)
            val dir = File(context.filesDir, "slideshow")
            if (!dir.exists()) dir.mkdirs()
            var count = 0
            for (uri in uris) {
                try {
                    val name = "${System.currentTimeMillis()}_${count}.jpg"
                    val dest = File(dir, name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                    if (dest.length() > 0) {
                        repository.insert(ImageLibraryEntity(filePath = dest.absolutePath))
                        count++
                    }
                } catch (_: Exception) { }
            }
            _state.value = _state.value.copy(isImporting = false)
            _toastMessage.emit(if (count > 0) "已导入 $count 张图片" else "未导入任何图片")
        }
    }

    fun deleteImage(id: Long) {
        viewModelScope.launch {
            val img = _state.value.images.find { it.id == id }
            if (img != null) {
                File(img.filePath).delete()
                repository.deleteById(id)
            }
        }
    }

    fun setMode(mode: SlideshowMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setAutoInterval(seconds: Int) {
        _state.value = _state.value.copy(autoIntervalSec = seconds.coerceIn(1, 60))
    }

    fun nextImage() {
        val images = _state.value.images
        if (images.isEmpty()) return
        val next = (_state.value.currentIndex + 1) % images.size
        _state.value = _state.value.copy(currentIndex = next)
    }

    fun prevImage() {
        val images = _state.value.images
        if (images.isEmpty()) return
        val prev = if (_state.value.currentIndex == 0) images.size - 1 else _state.value.currentIndex - 1
        _state.value = _state.value.copy(currentIndex = prev)
    }

    fun addTimePoint(positionMs: Long) {
        val tp = _state.value.timePoints.toMutableList()
        tp.add(positionMs)
        tp.sort()
        _state.value = _state.value.copy(timePoints = tp)
    }

    fun removeTimePoint(index: Int) {
        val tp = _state.value.timePoints.toMutableList()
        if (index in tp.indices) tp.removeAt(index)
        _state.value = _state.value.copy(timePoints = tp)
    }

    fun checkTimedAdvance(progressMs: Long) {
        if (_state.value.mode != SlideshowMode.Timed) return
        val tp = _state.value.timePoints
        if (tp.isEmpty()) return
        val idx = _state.value.currentIndex
        if (idx < tp.size && progressMs >= tp[idx]) {
            nextImage()
        }
    }
}
