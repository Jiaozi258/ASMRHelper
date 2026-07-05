package com.asmrhelper.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.asmrhelper.data.local.db.dao.AudioDao
import com.asmrhelper.data.local.db.dao.BackgroundImageDao
import com.asmrhelper.data.local.db.dao.BookmarkDao
import com.asmrhelper.data.local.db.dao.PlaylistDao
import com.asmrhelper.data.local.db.dao.SceneDao
import com.asmrhelper.data.local.db.dao.SleepJournalDao
import com.asmrhelper.data.local.db.dao.TriggerPadDao
import com.asmrhelper.data.local.db.dao.ImageAlbumDao
import com.asmrhelper.data.local.db.dao.ImageLibraryDao
import com.asmrhelper.data.local.db.dao.PlayHistoryDao
import com.asmrhelper.data.local.db.dao.VideoAudioDao
import com.asmrhelper.data.local.db.entity.AudioBgBinding
import com.asmrhelper.data.local.db.entity.AudioEntity
import com.asmrhelper.data.local.db.entity.BackgroundImageEntity
import com.asmrhelper.data.local.db.entity.BookmarkEntity
import com.asmrhelper.data.local.db.entity.ImageAlbumEntity
import com.asmrhelper.data.local.db.entity.ImageLibraryEntity
import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import com.asmrhelper.data.local.db.entity.PlaylistAudioCrossRef
import com.asmrhelper.data.local.db.entity.PlaylistEntity
import com.asmrhelper.data.local.db.entity.SceneEntity
import com.asmrhelper.data.local.db.entity.SleepJournalEntity
import com.asmrhelper.data.local.db.entity.TriggerPadEntity
import com.asmrhelper.data.local.db.entity.VideoAudioEntity

@Database(
    entities = [
        AudioEntity::class,
        PlaylistEntity::class,
        PlaylistAudioCrossRef::class,
        BackgroundImageEntity::class,
        AudioBgBinding::class,
        TriggerPadEntity::class,
        SleepJournalEntity::class,
        SceneEntity::class,
        BookmarkEntity::class,
        VideoAudioEntity::class,
        PlayHistoryEntity::class,
        ImageLibraryEntity::class,
        ImageAlbumEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AsmrDatabase : RoomDatabase() {
    abstract fun audioDao(): AudioDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun backgroundImageDao(): BackgroundImageDao
    abstract fun triggerPadDao(): TriggerPadDao
    abstract fun sleepJournalDao(): SleepJournalDao
    abstract fun sceneDao(): SceneDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun videoAudioDao(): VideoAudioDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun imageLibraryDao(): ImageLibraryDao
    abstract fun imageAlbumDao(): ImageAlbumDao
}
