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

/** 从封面 URL 提取主色（ARGB 编码为 Long）；空封面或加载失败回退 [fallback]。 */
@Composable
fun rememberCoverDominantColor(coverUrl: String?, fallback: Long): Long {
    val context = LocalContext.current
    val loader = Coil.imageLoader(context)
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
                val drawable = loader.execute(request).drawable
                val bitmap = drawable?.toBitmap()
                val swatch = bitmap?.let { Palette.from(it).generate().dominantSwatch }
                swatch?.rgb?.let { opaqueArgb(it) } ?: fallback
            } catch (_: Exception) {
                fallback
            }
        }
    }
    return state.value
}
