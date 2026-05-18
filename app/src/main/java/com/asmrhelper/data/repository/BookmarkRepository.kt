package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.BookmarkDao
import com.asmrhelper.data.local.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val dao: BookmarkDao
) {
    fun getByAudioId(audioId: Long): Flow<List<BookmarkEntity>> = dao.getByAudioId(audioId)

    suspend fun save(bookmark: BookmarkEntity): Long = dao.insert(bookmark)

    suspend fun delete(bookmark: BookmarkEntity) = dao.delete(bookmark)
}
