package com.asmrhelper.data.local.scanner

import android.content.Context
import android.provider.MediaStore
import com.asmrhelper.domain.model.Audio
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** 扫描系统媒体库中的音频文件 */
    fun scanMediaStore(): List<Audio> {
        val audioList = mutableListOf<Audio>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        )
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null,
            MediaStore.Audio.Media.DATE_ADDED + " DESC"
        ) ?: return audioList

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                audioList.add(
                    Audio(
                        title = it.getString(titleCol) ?: "未知音频",
                        artist = it.getString(artistCol) ?: "未知艺术家",
                        filePath = it.getString(dataCol) ?: continue,
                        durationMs = it.getLong(durationCol)
                    )
                )
            }
        }
        return audioList
    }
}
