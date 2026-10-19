package com.asmrhelper.player

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource

/**
 * 为在线流请求注入 CDN 校验所需的 Referer 头。
 *
 * 网易云/QQ 的 CDN（music.126.net / *.qqmusic.qq.com 等）会校验 Referer，
 * 缺失时返回 403，导致「所有在线歌曲无法播放」。Media3 1.x 没有按 MediaItem
 * 设请求头的 API，只能在 HttpDataSource 层按 host 注入。本地 file:// 请求
 * 不经过 HttpDataSource，因此不受影响。
 *
 * @param baseFactory 已配置好 User-Agent 等全局默认头的底层工厂。
 */
class RefererHttpDataSourceFactory(
    private val baseFactory: DefaultHttpDataSource.Factory
) : HttpDataSource.Factory by baseFactory {

    override fun createDataSource(): HttpDataSource {
        val upstream = baseFactory.createDataSource()
        return object : HttpDataSource by upstream {
            override fun open(dataSpec: DataSpec): Long {
                refererFor(dataSpec.uri?.host)?.let { upstream.setRequestProperty("Referer", it) }
                return upstream.open(dataSpec)
            }
        }
    }

    private fun refererFor(host: String?): String? = when {
        host == null -> null
        host == "music.126.net" || host.endsWith(".music.126.net") -> "https://music.163.com"
        host == "y.qq.com" || host.endsWith(".y.qq.com") ||
            host.endsWith(".qqmusic.qq.com") || host.endsWith(".tc.qq.com") -> "https://y.qq.com/"
        else -> null
    }
}
