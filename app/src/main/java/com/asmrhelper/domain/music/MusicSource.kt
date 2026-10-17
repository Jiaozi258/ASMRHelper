package com.asmrhelper.domain.music

import com.asmrhelper.domain.model.music.QrLoginState
import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 音乐音源统一接口。由各平台的原生实现（如 `NeteaseMusicSource`）直接请求
 * 官方接口（weapi 加密等）并归一化为这些方法。
 */
interface MusicSource {
    val id: String
    val displayName: String
    val isLoggedIn: StateFlow<Boolean>

    fun search(keyword: String, page: Int): Flow<List<RemoteSong>>
    fun getSongUrl(songId: String, quality: String = "standard"): Flow<String>
    fun getLyric(songId: String): Flow<RemoteLyric>
    fun getCoverUrl(songId: String): Flow<String>
    fun getSongDetail(songId: String): Flow<RemoteSong>
    fun getPlaylist(playlistId: String): Flow<RemotePlaylist>
    fun getRecommendations(): Flow<List<RemoteSong>>
    fun getCharts(): Flow<List<RemoteChart>>

    fun loginAccount(username: String, password: String): Flow<Result<Unit>>
    fun loginQr(): Flow<QrLoginState>
    fun logout()
}
