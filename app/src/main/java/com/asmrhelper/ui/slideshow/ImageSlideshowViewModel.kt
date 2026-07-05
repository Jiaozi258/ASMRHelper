package com.asmrhelper.ui.slideshow

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.data.local.db.entity.ImageAlbumEntity
import com.asmrhelper.data.local.db.entity.ImageLibraryEntity
import com.asmrhelper.data.repository.ImageAlbumRepositoryImpl
import com.asmrhelper.data.repository.ImageLibraryRepositoryImpl
import com.asmrhelper.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SlideshowMode { Manual, Auto, Timed }

data class SlideshowState(
    val images: List<ImageLibraryEntity> = emptyList(),
    val albums: List<ImageAlbumEntity> = emptyList(),
    val selectedAlbumId: Long = 0,  // 0 = show all / uncategorized
    val currentIndex: Int = 0,
    val mode: SlideshowMode = SlideshowMode.Manual,
    val autoIntervalSec: Int = 5,
    val timePoints: List<Long> = emptyList(),
    val timedAdvanceIndex: Int = 0,
    val isImporting: Boolean = false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ImageSlideshowViewModel @Inject constructor(
    private val repository: ImageLibraryRepositoryImpl,
    private val albumRepository: ImageAlbumRepositoryImpl,
    private val playerManager: PlayerManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SlideshowState())
    val state: StateFlow<SlideshowState> = _state.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        // Observe albums
        viewModelScope.launch {
            albumRepository.getAll().collect { albums ->
                _state.value = _state.value.copy(albums = albums)
            }
        }
        // Observe images filtered by selected album
        viewModelScope.launch {
            _state.flatMapLatest { s ->
                if (s.selectedAlbumId == 0L) repository.getAll()
                else repository.getByAlbumId(s.selectedAlbumId)
            }.collect { images ->
                val st = _state.value
                // Adjust currentIndex if images shrunk (e.g. after delete)
                val newIdx = if (images.isEmpty()) 0
                else st.currentIndex.coerceIn(0, images.size - 1)
                _state.value = st.copy(images = images, currentIndex = newIdx)
            }
        }
        // Observe player progress for timed-advance mode
        viewModelScope.launch {
            playerManager.state.collect { playerState ->
                _progressMs.value = playerState.progressMs
                checkTimedAdvance(playerState.progressMs)
            }
        }
    }

    // ── Albums ───────────────────────────────────────────

    fun selectAlbum(albumId: Long) {
        _state.value = _state.value.copy(
            selectedAlbumId = albumId,
            currentIndex = 0,
            timedAdvanceIndex = 0
        )
    }

    fun createAlbum(name: String) {
        viewModelScope.launch {
            val newId = albumRepository.insert(ImageAlbumEntity(name = name.trim()))
            if (newId > 0) {
                selectAlbum(newId)
                _toastMessage.emit("已创建合集「${name.trim()}」")
            }
        }
    }

    fun deleteAlbum(album: ImageAlbumEntity) {
        viewModelScope.launch {
            albumRepository.delete(album)
            if (_state.value.selectedAlbumId == album.id) {
                selectAlbum(0)
            }
            _toastMessage.emit("已删除合集「${album.name}」")
        }
    }

    // ── Images ───────────────────────────────────────────

    fun importFromUris(uris: List<Uri>) {
        val targetAlbumId = _state.value.selectedAlbumId
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
                        repository.insert(
                            ImageLibraryEntity(filePath = dest.absolutePath, albumId = targetAlbumId)
                        )
                        count++
                    }
                } catch (_: Exception) { }
            }
            _state.value = _state.value.copy(isImporting = false)
            _toastMessage.emit(if (count > 0) "已导入 $count 张图片" else "未导入任何图片")
        }
    }

    fun deleteCurrentImage() {
        val img = _state.value.images.getOrNull(_state.value.currentIndex) ?: return
        viewModelScope.launch {
            File(img.filePath).delete()
            repository.deleteById(img.id)
            _toastMessage.emit("已删除")
        }
    }

    // ── Slideshow ────────────────────────────────────────

    fun setMode(mode: SlideshowMode) {
        _state.value = _state.value.copy(
            mode = mode,
            timedAdvanceIndex = 0
        )
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

    private fun checkTimedAdvance(progressMs: Long) {
        if (_state.value.mode != SlideshowMode.Timed) return
        val st = _state.value
        val tp = st.timePoints
        if (tp.isEmpty()) return
        val idx = st.timedAdvanceIndex
        if (idx < tp.size && progressMs >= tp[idx]) {
            val images = st.images
            if (images.isEmpty()) return
            val nextImgIdx = (st.currentIndex + 1) % images.size
            val nextTpIdx = idx + 1
            _state.value = st.copy(
                currentIndex = nextImgIdx,
                timedAdvanceIndex = nextTpIdx,
                mode = if (nextTpIdx >= tp.size) SlideshowMode.Manual else SlideshowMode.Timed
            )
        }
    }
}
