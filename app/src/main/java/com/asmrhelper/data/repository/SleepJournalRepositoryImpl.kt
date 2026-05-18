package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.SleepJournalDao
import com.asmrhelper.data.local.db.entity.SleepJournalEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepJournalRepositoryImpl @Inject constructor(
    private val dao: SleepJournalDao
) {
    fun getAll(): Flow<List<SleepJournalEntity>> = dao.getAll()

    suspend fun getByDate(date: Long): SleepJournalEntity? = dao.getByDate(date)

    suspend fun insert(journal: SleepJournalEntity): Long = dao.insert(journal)

    suspend fun updateEnd(id: Long, endTimeMs: Long, durationMinutes: Int) {
        dao.updateEnd(id, endTimeMs, durationMinutes)
    }

    suspend fun saveQualityAndNotes(id: Long, quality: Int, notes: String) {
        dao.updateQuality(id, quality, notes)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    data class SleepStats(
        val totalSessions: Int = 0,
        val avgDurationMinutes: Double = 0.0,
        val avgQuality: Double = 0.0,
        val totalDurationHours: Double = 0.0
    )

    suspend fun getStats(): SleepStats {
        return SleepStats(
            totalSessions = dao.getTotalCount(),
            avgDurationMinutes = dao.getAverageDuration() ?: 0.0,
            avgQuality = dao.getAverageQuality() ?: 0.0,
            totalDurationHours = (dao.getTotalDurationMinutes() ?: 0L) / 60.0
        )
    }
}
