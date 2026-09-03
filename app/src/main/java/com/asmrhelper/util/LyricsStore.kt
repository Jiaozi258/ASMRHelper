package com.asmrhelper.util

import android.content.Context
import java.io.File

/**
 * 歌词存储：以纯文本形式保存，与音频文件路径关联。
 * 存储位置：filesDir/lyrics/<md5(filePath)>.txt
 */
object LyricsStore {

    private fun lyricsDir(context: Context): File {
        val dir = File(context.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun keyFor(filePath: String): String =
        filePath.hashCode().toString() + "_" + File(filePath).name.hashCode().toString()

    /** 保存歌词文本（覆盖） */
    fun saveLyrics(context: Context, filePath: String, lyricsText: String) {
        val file = File(lyricsDir(context), keyFor(filePath) + ".txt")
        file.writeText(lyricsText)
    }

    /** 读取歌词文本，无则返回 null */
    fun loadLyrics(context: Context, filePath: String): String? {
        val file = File(lyricsDir(context), keyFor(filePath) + ".txt")
        return if (file.exists()) file.readText() else null
    }

    /** 是否有歌词 */
    fun hasLyrics(context: Context, filePath: String): Boolean {
        val file = File(lyricsDir(context), keyFor(filePath) + ".txt")
        return file.exists() && file.length() > 0
    }

    /** 删除歌词 */
    fun deleteLyrics(context: Context, filePath: String) {
        val file = File(lyricsDir(context), keyFor(filePath) + ".txt")
        if (file.exists()) file.delete()
    }
}
