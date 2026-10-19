package com.asmrhelper.data.music

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在线歌曲收藏（跨音源、纯本地、免登录）。
 *
 * 在线歌曲没有 Room 实体（AudioEntity 无 sourceId/sourceSongId 列），
 * 收藏状态用 SharedPreferences 的 `Set<String>` 以 `sourceId:songId` 为键持久化，
 * 供播放页爱心按钮在远程曲目上即时读写。
 */
@Singleton
class RemoteFavoritesStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("remote_favorites", Context.MODE_PRIVATE)

    fun isFavorite(sourceId: String, songId: String): Boolean =
        prefs.getStringSet(KEY, emptySet())?.contains(key(sourceId, songId)) == true

    fun setFavorite(sourceId: String, songId: String, favorite: Boolean) {
        val set = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (favorite) set.add(key(sourceId, songId)) else set.remove(key(sourceId, songId))
        prefs.edit().putStringSet(KEY, set).apply()
    }

    private fun key(sourceId: String, songId: String) = "$sourceId:$songId"

    private companion object {
        const val KEY = "favorites"
    }
}
