package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asmrhelper.data.local.db.entity.PlaylistAudioCrossRef
import com.asmrhelper.data.local.db.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val created_at: Long,
    val audio_count: Int
)

@Dao
interface PlaylistDao {
    @Query("""
        SELECT p.id, p.name, p.created_at, COUNT(ref.audio_id) AS audio_count
        FROM playlist p
        LEFT JOIN playlist_audio_cross_ref ref ON p.id = ref.playlist_id
        GROUP BY p.id
        ORDER BY p.created_at DESC
    """)
    fun getAllPlaylists(): Flow<List<PlaylistWithCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlist SET name = :newName WHERE id = :id")
    suspend fun renamePlaylist(id: Long, newName: String)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAudioToPlaylist(crossRef: PlaylistAudioCrossRef)

    @Query("DELETE FROM playlist_audio_cross_ref WHERE playlist_id = :playlistId AND audio_id = :audioId")
    suspend fun removeAudioFromPlaylist(playlistId: Long, audioId: Long)

    @Query("""
        SELECT a.* FROM audio a
        INNER JOIN playlist_audio_cross_ref ref ON a.id = ref.audio_id
        WHERE ref.playlist_id = :playlistId
        ORDER BY a.added_at DESC
    """)
    fun getPlaylistAudios(playlistId: Long): Flow<List<com.asmrhelper.data.local.db.entity.AudioEntity>>
}
