package com.asmrhelper.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.asmrhelper.data.local.db.AsmrDatabase
import com.asmrhelper.data.local.db.dao.AudioDao
import com.asmrhelper.data.local.db.dao.BackgroundImageDao
import com.asmrhelper.data.local.db.dao.BookmarkDao
import com.asmrhelper.data.local.db.dao.PlaylistDao
import com.asmrhelper.data.local.db.dao.SceneDao
import com.asmrhelper.data.local.db.dao.SleepJournalDao
import com.asmrhelper.data.local.db.dao.TriggerPadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS scenes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    audioFilePath TEXT NOT NULL DEFAULT '',
                    bgColorIndex INTEGER NOT NULL DEFAULT 0,
                    binauralPresetName TEXT NOT NULL DEFAULT '',
                    timerMinutes INTEGER NOT NULL DEFAULT 0,
                    noiseType TEXT NOT NULL DEFAULT '',
                    spatialMode TEXT NOT NULL DEFAULT '',
                    visualizerEnabled INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS bookmarks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    audioId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    positionMs INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AsmrDatabase =
        Room.databaseBuilder(
            context,
            AsmrDatabase::class.java,
            "asmr_helper.db"
        ).addMigrations(MIGRATION_2_3)
         .build()

    @Provides
    fun provideAudioDao(db: AsmrDatabase): AudioDao = db.audioDao()

    @Provides
    fun providePlaylistDao(db: AsmrDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideBackgroundImageDao(db: AsmrDatabase): BackgroundImageDao = db.backgroundImageDao()

    @Provides
    fun provideTriggerPadDao(db: AsmrDatabase): TriggerPadDao = db.triggerPadDao()

    @Provides
    fun provideSleepJournalDao(db: AsmrDatabase): SleepJournalDao = db.sleepJournalDao()

    @Provides
    fun provideSceneDao(db: AsmrDatabase): SceneDao = db.sceneDao()

    @Provides
    fun provideBookmarkDao(db: AsmrDatabase): BookmarkDao = db.bookmarkDao()
}
