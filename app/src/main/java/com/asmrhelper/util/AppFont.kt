package com.asmrhelper.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * 自定义文字类型：把用户导入的 .ttf/.otf 字体复制到应用私有目录，
 * 并在启动时加载成全局 FontFamily（null = 跟随系统）。
 */
object AppFont {

    private const val PREFS = "asmr_settings"
    private const val KEY = "app_font_path"

    /** 读取已保存的自定义字体并构造 FontFamily；无/失败返回 null（跟随系统）。 */
    fun loadFontFamily(context: Context): FontFamily? {
        val path = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return try {
            FontFamily(Typeface.createFromFile(File(path)))
        } catch (_: Exception) {
            null
        }
    }

    /** 把用户挑选的字体复制到应用私有目录，返回绝对路径；失败或非 .ttf/.otf 返回 null。 */
    fun importFont(context: Context, uri: Uri): String? {
        val name = queryDisplayName(context, uri)
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        if (ext !in setOf("ttf", "otf")) return null
        val dir = File(context.filesDir, "fonts").apply { mkdirs() }
        val target = File(dir, "custom.$ext")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
}
