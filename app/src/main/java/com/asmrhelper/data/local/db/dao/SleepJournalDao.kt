package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmrhelper.data.local.db.entity.SleepJournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepJournalDao {
    @Query("SELECT * FROM sleep_journals ORDER BY date DESC")
    fun getAll(): Flow<List<SleepJournalEntity>>

    @Query("SELECT * FROM sleep_journals WHERE date = :date")
    suspend fun getByDate(date: Long): SleepJournalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(journal: SleepJournalEntity): Long

    @Query("UPDATE sleep_journals SET end_time_ms = :endTimeMs, duration_minutes = :durationMinutes WHERE id = :id")
    suspend fun updateEnd(id: Long, endTimeMs: Long, durationMinutes: Int)

    @Query("UPDATE sleep_journals SET quality = :quality, notes = :notes WHERE id = :id")
    suspend fun updateQuality(id: Long, quality: Int, notes: String)

    @Query("DELETE FROM sleep_journals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT AVG(duration_minutes) FROM sleep_journals WHERE duration_minutes > 0")
    suspend fun getAverageDuration(): Double?

    @Query("SELECT AVG(quality) FROM sleep_journals WHERE quality > 0")
    suspend fun getAverageQuality(): Double?

    @Query("SELECT COUNT(*) FROM sleep_journals")
    suspend fun getTotalCount(): Int

    @Query("SELECT SUM(duration_minutes) FROM sleep_journals WHERE duration_minutes > 0")
    suspend fun getTotalDurationMinutes(): Long?
}
