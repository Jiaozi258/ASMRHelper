package com.asmrhelper.ui.play.folia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import com.asmrhelper.util.opaqueArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从封面 URL 提取双色调主/次色（ARGB 编码为 Long）。
 * 主色取 vibrant（缺则 dominant），次色取 darkMuted（缺则 muted，再缺则主色）；
 * 空封面或加载失败时分别回退 [fallbackPrimary]/[fallbackSecondary]。
 */
@Composable
fun rememberCoverPalette(
    coverUrl: String?,
    fallbackPrimary: Long,
    fallbackSecondary: Long
): Pair<Long, Long> {
    val context = LocalContext.current
    val loader = Coil.imageLoader(context)
    val fallback = fallbackPrimary to fallbackSecondary
    val state = produceState(initialValue = fallback, coverUrl, loader) {
        if (coverUrl.isNullOrBlank()) {
            value = fallback
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)
                    .build()
                val bitmap = loader.execute(request).drawable?.toBitmap()
                val palette = bitmap?.let { Palette.from(it).generate() }
                val primary = palette?.vibrantSwatch ?: palette?.dominantSwatch
                val secondary = palette?.darkMutedSwatch ?: palette?.mutedSwatch ?: primary
                val p = primary?.rgb?.let { opaqueArgb(it) } ?: fallbackPrimary
                val s = secondary?.rgb?.let { opaqueArgb(it) } ?: fallbackSecondary
                p to s
            } catch (_: Exception) {
                fallback
            }
        }
    }
    return state.value
}
