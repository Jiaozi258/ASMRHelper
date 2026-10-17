package com.asmrhelper.ui.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.R
import com.asmrhelper.data.local.scanner.AudioScanner
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.repository.AudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val audioScanner: AudioScanner,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val deletedPathsKey = "deleted_audio_paths"
    private val deletedPaths: MutableSet<String> by lazy {
        context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)
            .getStringSet(deletedPathsKey, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

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

    fun deleteAudio(audio: Audio) {
        viewModelScope.launch {
            audioRepository.deleteAudio(audio.id)
            deletedPaths.add(audio.filePath)
            context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)
                .edit().putStringSet(deletedPathsKey, deletedPaths).apply()
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
                    _scanResult.emit(context.getString(R.string.library_scan_no_files))
                } else {
                    val newAudios = scanned.filter { it.filePath !in deletedPaths }
                    newAudios.forEach { audio -> audioRepository.addAudio(audio) }
                    val skipped = scanned.size - newAudios.size
                    val msg = if (skipped > 0)
                        context.getString(R.string.library_scan_imported_skipped, newAudios.size, skipped)
                    else context.getString(R.string.library_scan_imported, newAudios.size)
                    _scanResult.emit(msg)
                }
            } catch (e: Exception) {
                _scanResult.emit(context.getString(R.string.library_scan_failed, e.message))
            } finally {
                _isScanning.value = false
            }
        }
    }

    /** Import audio files selected via SAF file picker.
     *  SAF lets users browse private space, secondary users, USB OTG —
     *  locations that MediaStore cannot see. */
    fun importFromSaf(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val destDir = File(context.filesDir, "imported")
                if (!destDir.exists()) destDir.mkdirs()

                var imported = 0
                for (uri in uris) {
                    try {
                        val audio = copyAndExtract(uri, destDir) ?: continue
                        audioRepository.addAudio(audio)
                        imported++
                    } catch (_: Exception) { /* skip corrupt files */ }
                }

                _scanResult.emit(
                    if (imported > 0) context.getString(R.string.library_import_success, imported)
                    else context.getString(R.string.library_import_no_files)
                )
            } catch (e: Exception) {
                _scanResult.emit(context.getString(R.string.library_import_failed, e.message))
            } finally {
                _isScanning.value = false
            }
        }
    }

    /** Copy a SAF URI to local storage and extract metadata. */
    private fun copyAndExtract(uri: Uri, destDir: File): Audio? {
        val cr = context.contentResolver

        // Get display name for the file
        var fileName: String? = null
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                fileName = cursor.getString(0)
            }
        }
        if (fileName == null) fileName = "audio_${System.currentTimeMillis()}"
        // Ensure unique file name
        val dest = File(destDir, fileName!!)
        val finalDest = if (dest.exists()) {
            File(destDir, "${System.currentTimeMillis()}_$fileName")
        } else dest

        // Copy to local storage
        cr.openInputStream(uri)?.use { input ->
            finalDest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null

        if (finalDest.length() == 0L) {
            finalDest.delete()
            return null
        }

        // Extract metadata
        val mmr = MediaMetadataRetriever()
        var title = fileName!!.substringBeforeLast(".")
        var artist = ""
        var durationMs = 0L
        try {
            mmr.setDataSource(finalDest.absolutePath)
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = dur?.toLongOrNull() ?: 0L
        } catch (_: Exception) { /* use defaults */ }
        finally { mmr.release() }

        return Audio(
            title = title,
            artist = artist,
            filePath = finalDest.absolutePath,
            durationMs = durationMs
        )
    }
}
