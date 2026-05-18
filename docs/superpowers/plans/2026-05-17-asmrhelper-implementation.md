# ASMRHelper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the ASMRHelper local ASMR audio player from scratch — Gradle config, Room DB, dual-ExoPlayer engine, and animated Compose UI.

**Architecture:** Single-module Clean Architecture (ui → domain → data) with Hilt DI. Single Activity hosts Compose Navigation with AnimatedContent transitions. Foreground Service manages two ExoPlayer instances for dual-track playback.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Media3 ExoPlayer, Room + KSP, Hilt, Coroutines + Flow, Coil, Gradle KTS + Version Catalog

---

## Task 1: Gradle 项目骨架

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: 创建 settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ASMRHelper"
include(":app")
```

- [ ] **Step 2: 创建 gradle/libs.versions.toml**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
hilt = "2.53.1"
hilt-navigation-compose = "1.2.0"
compose-bom = "2024.12.01"
media3 = "1.5.1"
room = "2.6.1"
lifecycle = "2.8.7"
navigation = "2.8.5"
coroutines = "1.9.0"
coil = "2.7.0"
core-ktx = "1.15.0"
activity-compose = "1.9.3"

[libraries]
# Compose BOM
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-animation = { group = "androidx.compose.animation", name = "animation" }

# Navigation
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Lifecycle
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }

# Media3
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }

# Coroutines
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Coil
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

# Core
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
kotlin-ksp = { id = "org.jetbrains.kotlin.plugin.ksp", version.ref = "kotlin" }
```

- [ ] **Step 3: 创建根 build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 4: 创建 app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.asmrhelper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.asmrhelper"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Coil
    implementation(libs.coil.compose)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
}
```

- [ ] **Step 5: 创建 gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 6: 创建 gradle-wrapper.properties**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

---

## Task 2: AndroidManifest + Application + MainActivity

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/asmrhelper/AsmrApplication.kt`
- Create: `app/src/main/java/com/asmrhelper/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: 创建 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".AsmrApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.ASMRHelper">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.ASMRHelper">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".player.AsmrMediaService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />
    </application>
</manifest>
```

- [ ] **Step 2: 创建 AsmrApplication.kt**

```kotlin
package com.asmrhelper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AsmrApplication : Application()
```

- [ ] **Step 3: 创建 MainActivity.kt**

```kotlin
package com.asmrhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.asmrhelper.ui.theme.ASMRHelperTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ASMRHelperTheme {
                // 后续 Task 替换为 AsmrNavHost
            }
        }
    }
}
```

- [ ] **Step 4: 创建 strings.xml + themes.xml**

`app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">ASMRHelper</string>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.ASMRHelper" parent="android:Theme.Material.NoActionBar" />
</resources>
```

- [ ] **Step 5: 创建 proguard-rules.pro** (空文件即可)

---

## Task 3: Compose 主题 (Theme / Color / Typography)

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/theme/Color.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/theme/Type.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/theme/Theme.kt`

- [ ] **Step 1: 创建 Color.kt**

```kotlin
package com.asmrhelper.ui.theme

import androidx.compose.ui.graphics.Color

// 主色调 — 深色沉浸式基调
val DarkBackground = Color(0xFF0D0D0D)
val DarkSurface = Color(0xFF1A1A2E)
val DarkSurfaceVariant = Color(0xFF252540)
val AccentPurple = Color(0xFFBB86FC)
val AccentPurpleVariant = Color(0xFF9C64E8)
val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFFB0B0B0)
val TextHint = Color(0xFF6A6A6A)
val ControlWhite = Color(0xFFFFFFFF)
val ErrorRed = Color(0xFFCF6679)
```

- [ ] **Step 2: 创建 Type.kt**

```kotlin
package com.asmrhelper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

- [ ] **Step 3: 创建 Theme.kt**

```kotlin
package com.asmrhelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentPurple,
    secondary = AccentPurpleVariant,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = ControlWhite,
    onSecondary = ControlWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed
)

@Composable
fun ASMRHelperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
```

---

## Task 4: Room 实体类与 DAO

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/local/db/entity/AudioEntity.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/entity/PlaylistEntity.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/entity/PlaylistAudioCrossRef.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/entity/BackgroundImageEntity.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/entity/AudioBgBinding.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/dao/AudioDao.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/dao/PlaylistDao.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/dao/BackgroundImageDao.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/AsmrDatabase.kt`

- [ ] **Step 1: 创建 AudioEntity.kt**

```kotlin
package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio")
data class AudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String = "",
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: 创建 PlaylistEntity.kt**

```kotlin
package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 3: 创建 PlaylistAudioCrossRef.kt**

```kotlin
package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "playlist_audio_cross_ref",
    primaryKeys = ["playlist_id", "audio_id"]
)
data class PlaylistAudioCrossRef(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "audio_id") val audioId: Long
)
```

- [ ] **Step 4: 创建 BackgroundImageEntity.kt**

```kotlin
package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "background_image")
data class BackgroundImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 5: 创建 AudioBgBinding.kt**

```kotlin
package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "audio_bg_binding",
    primaryKeys = ["audio_id", "image_id"]
)
data class AudioBgBinding(
    @ColumnInfo(name = "audio_id") val audioId: Long,
    @ColumnInfo(name = "image_id") val imageId: Long
)
```

- [ ] **Step 6: 创建 AudioDao.kt**

```kotlin
package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asmrhelper.data.local.db.entity.AudioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio ORDER BY added_at DESC")
    fun getAllAudio(): Flow<List<AudioEntity>>

    @Query("SELECT * FROM audio WHERE is_favorite = 1 ORDER BY added_at DESC")
    fun getFavorites(): Flow<List<AudioEntity>>

    @Query("SELECT * FROM audio WHERE id = :id")
    suspend fun getById(id: Long): AudioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(audioList: List<AudioEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audio: AudioEntity): Long

    @Update
    suspend fun update(audio: AudioEntity)

    @Query("DELETE FROM audio WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM audio WHERE file_path LIKE '%' || :keyword || '%' OR title LIKE '%' || :keyword || '%'")
    suspend fun search(keyword: String): List<AudioEntity>
}
```

- [ ] **Step 7: 创建 PlaylistDao.kt**

```kotlin
package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.asmrhelper.data.local.db.entity.PlaylistAudioCrossRef
import com.asmrhelper.data.local.db.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY created_at DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

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
```

- [ ] **Step 8: 创建 BackgroundImageDao.kt**

```kotlin
package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.asmrhelper.data.local.db.entity.AudioBgBinding
import com.asmrhelper.data.local.db.entity.BackgroundImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackgroundImageDao {
    @Query("SELECT * FROM background_image ORDER BY added_at DESC")
    fun getAllImages(): Flow<List<BackgroundImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: BackgroundImageEntity): Long

    @Query("DELETE FROM background_image WHERE id = :imageId")
    suspend fun deleteImage(imageId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bindAudioToImage(binding: AudioBgBinding)

    @Query("DELETE FROM audio_bg_binding WHERE audio_id = :audioId AND image_id = :imageId")
    suspend fun unbindAudioFromImage(audioId: Long, imageId: Long)

    @Query("""
        SELECT bi.* FROM background_image bi
        INNER JOIN audio_bg_binding b ON bi.id = b.image_id
        WHERE b.audio_id = :audioId
        LIMIT 1
    """)
    suspend fun getBindingForAudio(audioId: Long): BackgroundImageEntity?

    @Query("DELETE FROM audio_bg_binding WHERE audio_id = :audioId")
    suspend fun clearBindingsForAudio(audioId: Long)
}
```

- [ ] **Step 9: 创建 AsmrDatabase.kt**

```kotlin
package com.asmrhelper.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.asmrhelper.data.local.db.dao.AudioDao
import com.asmrhelper.data.local.db.dao.BackgroundImageDao
import com.asmrhelper.data.local.db.dao.PlaylistDao
import com.asmrhelper.data.local.db.entity.AudioBgBinding
import com.asmrhelper.data.local.db.entity.AudioEntity
import com.asmrhelper.data.local.db.entity.BackgroundImageEntity
import com.asmrhelper.data.local.db.entity.PlaylistAudioCrossRef
import com.asmrhelper.data.local.db.entity.PlaylistEntity

@Database(
    entities = [
        AudioEntity::class,
        PlaylistEntity::class,
        PlaylistAudioCrossRef::class,
        BackgroundImageEntity::class,
        AudioBgBinding::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AsmrDatabase : RoomDatabase() {
    abstract fun audioDao(): AudioDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun backgroundImageDao(): BackgroundImageDao
}
```

---

## Task 5: Domain 层 (模型 + Repository 接口 + UseCase)

**Files:**
- Create: `app/src/main/java/com/asmrhelper/domain/model/Audio.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/model/Playlist.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/model/BackgroundImage.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/model/PlayerState.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/model/LoopMode.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/repository/AudioRepository.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/repository/PlaylistRepository.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/repository/SettingsRepository.kt`

- [ ] **Step 1: 创建 Audio.kt**

```kotlin
package com.asmrhelper.domain.model

data class Audio(
    val id: Long = 0,
    val title: String,
    val artist: String = "",
    val filePath: String,
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: 创建 Playlist.kt + BackgroundImage.kt**

```kotlin
package com.asmrhelper.domain.model

data class Playlist(
    val id: Long = 0,
    val name: String,
    val audioCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
```

```kotlin
package com.asmrhelper.domain.model

data class BackgroundImage(
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 3: 创建 PlayerState.kt + LoopMode.kt**

```kotlin
package com.asmrhelper.domain.model

enum class LoopMode {
    NONE,       // 播完即止
    SINGLE,     // 单曲循环
    LIST        // 列表循环
}
```

```kotlin
package com.asmrhelper.domain.model

import com.asmrhelper.domain.model.LoopMode

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentAudio: Audio? = null,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val loopMode: LoopMode = LoopMode.NONE,
    val isBackgroundPlaying: Boolean = false,
    val isPrivacyMode: Boolean = false
)
```

- [ ] **Step 4: 创建 Repository 接口**

```kotlin
package com.asmrhelper.domain.repository

import com.asmrhelper.domain.model.Audio
import kotlinx.coroutines.flow.Flow

interface AudioRepository {
    fun getAllAudio(): Flow<List<Audio>>
    fun getFavorites(): Flow<List<Audio>>
    suspend fun getById(id: Long): Audio?
    suspend fun addAudio(audio: Audio): Long
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
    suspend fun deleteAudio(id: Long)
    suspend fun searchAudio(keyword: String): List<Audio>
}
```

```kotlin
package com.asmrhelper.domain.repository

import com.asmrhelper.domain.model.Playlist
import com.asmrhelper.domain.model.Audio
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(id: Long, newName: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun addAudioToPlaylist(playlistId: Long, audioId: Long)
    suspend fun removeAudioFromPlaylist(playlistId: Long, audioId: Long)
    fun getPlaylistAudios(playlistId: Long): Flow<List<Audio>>
}
```

```kotlin
package com.asmrhelper.domain.repository

import com.asmrhelper.domain.model.BackgroundImage
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getBackgroundImages(): Flow<List<BackgroundImage>>
    suspend fun addBackgroundImage(name: String, filePath: String): Long
    suspend fun deleteBackgroundImage(id: Long)
    suspend fun bindAudioToImage(audioId: Long, imageId: Long)
    suspend fun unbindAudioFromImage(audioId: Long, imageId: Long)
    suspend fun getBindingForAudio(audioId: Long): BackgroundImage?
    fun isPrivacyMode(): Flow<Boolean>
    suspend fun setPrivacyMode(enabled: Boolean)
}
```

---

## Task 6: Data 层实现

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/mapper/EntityMappers.kt`
- Create: `app/src/main/java/com/asmrhelper/data/repository/AudioRepositoryImpl.kt`
- Create: `app/src/main/java/com/asmrhelper/data/repository/PlaylistRepositoryImpl.kt`
- Create: `app/src/main/java/com/asmrhelper/data/repository/SettingsRepositoryImpl.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/scanner/AudioScanner.kt`

- [ ] **Step 1: 创建 EntityMappers.kt**

```kotlin
package com.asmrhelper.data.mapper

import com.asmrhelper.data.local.db.entity.AudioEntity
import com.asmrhelper.data.local.db.entity.BackgroundImageEntity
import com.asmrhelper.data.local.db.entity.PlaylistEntity
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.BackgroundImage
import com.asmrhelper.domain.model.Playlist

fun AudioEntity.toDomain() = Audio(
    id = id,
    title = title,
    artist = artist,
    filePath = filePath,
    durationMs = durationMs,
    isFavorite = isFavorite,
    addedAt = addedAt
)

fun Audio.toEntity() = AudioEntity(
    id = id,
    title = title,
    artist = artist,
    filePath = filePath,
    durationMs = durationMs,
    isFavorite = isFavorite,
    addedAt = addedAt
)

fun PlaylistEntity.toDomain(audioCount: Int = 0) = Playlist(
    id = id,
    name = name,
    audioCount = audioCount,
    createdAt = createdAt
)

fun BackgroundImageEntity.toDomain() = BackgroundImage(
    id = id,
    name = name,
    filePath = filePath,
    addedAt = addedAt
)

fun BackgroundImage.toEntity() = BackgroundImageEntity(
    id = id,
    name = name,
    filePath = filePath,
    addedAt = addedAt
)
```

- [ ] **Step 2: 创建 AudioRepositoryImpl.kt**

```kotlin
package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.AudioDao
import com.asmrhelper.data.mapper.toDomain
import com.asmrhelper.data.mapper.toEntity
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.repository.AudioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepositoryImpl @Inject constructor(
    private val audioDao: AudioDao
) : AudioRepository {

    override fun getAllAudio(): Flow<List<Audio>> =
        audioDao.getAllAudio().map { list -> list.map { it.toDomain() } }

    override fun getFavorites(): Flow<List<Audio>> =
        audioDao.getFavorites().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Audio? =
        audioDao.getById(id)?.toDomain()

    override suspend fun addAudio(audio: Audio): Long =
        audioDao.insert(audio.toEntity())

    override suspend fun updateFavorite(id: Long, isFavorite: Boolean) {
        audioDao.getById(id)?.let { entity ->
            audioDao.update(entity.copy(isFavorite = isFavorite))
        }
    }

    override suspend fun deleteAudio(id: Long) =
        audioDao.deleteById(id)

    override suspend fun searchAudio(keyword: String): List<Audio> =
        audioDao.search(keyword).map { it.toDomain() }
}
```

- [ ] **Step 3: 创建 PlaylistRepositoryImpl.kt**

```kotlin
package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.PlaylistDao
import com.asmrhelper.data.local.db.entity.PlaylistAudioCrossRef
import com.asmrhelper.data.local.db.entity.PlaylistEntity
import com.asmrhelper.data.mapper.toDomain
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.Playlist
import com.asmrhelper.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylists().map { list -> list.map { it.toDomain() } }

    override suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun renamePlaylist(id: Long, newName: String) {
        playlistDao.insertPlaylist(PlaylistEntity(id = id, name = newName))
    }

    override suspend fun deletePlaylist(id: Long) =
        playlistDao.deletePlaylist(id)

    override suspend fun addAudioToPlaylist(playlistId: Long, audioId: Long) =
        playlistDao.addAudioToPlaylist(PlaylistAudioCrossRef(playlistId, audioId))

    override suspend fun removeAudioFromPlaylist(playlistId: Long, audioId: Long) =
        playlistDao.removeAudioFromPlaylist(playlistId, audioId)

    override fun getPlaylistAudios(playlistId: Long): Flow<List<Audio>> =
        playlistDao.getPlaylistAudios(playlistId).map { list -> list.map { it.toDomain() } }
}
```

- [ ] **Step 4: 创建 SettingsRepositoryImpl.kt**

```kotlin
package com.asmrhelper.data.repository

import android.content.Context
import com.asmrhelper.data.local.db.dao.BackgroundImageDao
import com.asmrhelper.data.local.db.entity.AudioBgBinding
import com.asmrhelper.data.local.db.entity.BackgroundImageEntity
import com.asmrhelper.data.mapper.toDomain
import com.asmrhelper.data.mapper.toEntity
import com.asmrhelper.domain.model.BackgroundImage
import com.asmrhelper.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val imageDao: BackgroundImageDao,
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val prefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)
    private val _privacyMode = MutableStateFlow(prefs.getBoolean("privacy_mode", false))

    override fun getBackgroundImages(): Flow<List<BackgroundImage>> =
        imageDao.getAllImages().map { list -> list.map { it.toDomain() } }

    override suspend fun addBackgroundImage(name: String, filePath: String): Long =
        imageDao.insertImage(BackgroundImageEntity(name = name, filePath = filePath))

    override suspend fun deleteBackgroundImage(id: Long) =
        imageDao.deleteImage(id)

    override suspend fun bindAudioToImage(audioId: Long, imageId: Long) =
        imageDao.bindAudioToImage(AudioBgBinding(audioId, imageId))

    override suspend fun unbindAudioFromImage(audioId: Long, imageId: Long) =
        imageDao.unbindAudioFromImage(audioId, imageId)

    override suspend fun getBindingForAudio(audioId: Long): BackgroundImage? =
        imageDao.getBindingForAudio(audioId)?.toDomain()

    override fun isPrivacyMode(): Flow<Boolean> = _privacyMode

    override suspend fun setPrivacyMode(enabled: Boolean) {
        prefs.edit().putBoolean("privacy_mode", enabled).apply()
        _privacyMode.value = enabled
    }
}
```

- [ ] **Step 5: 创建 AudioScanner.kt**

```kotlin
package com.asmrhelper.data.local.scanner

import android.content.Context
import android.provider.MediaStore
import com.asmrhelper.domain.model.Audio
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** 扫描系统媒体库中的音频文件 */
    fun scanMediaStore(): List<Audio> {
        val audioList = mutableListOf<Audio>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        )
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null,
            MediaStore.Audio.Media.DATE_ADDED + " DESC"
        ) ?: return audioList

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                audioList.add(
                    Audio(
                        title = it.getString(titleCol) ?: "未知音频",
                        artist = it.getString(artistCol) ?: "未知艺术家",
                        filePath = it.getString(dataCol) ?: continue,
                        durationMs = it.getLong(durationCol)
                    )
                )
            }
        }
        return audioList
    }
}
```

---

## Task 7: Hilt DI 模块

**Files:**
- Create: `app/src/main/java/com/asmrhelper/di/DatabaseModule.kt`
- Create: `app/src/main/java/com/asmrhelper/di/RepositoryModule.kt`
- Create: `app/src/main/java/com/asmrhelper/di/PlayerModule.kt`

- [ ] **Step 1: 创建所有 DI 模块**

```kotlin
package com.asmrhelper.di

import android.content.Context
import androidx.room.Room
import com.asmrhelper.data.local.db.AsmrDatabase
import com.asmrhelper.data.local.db.dao.AudioDao
import com.asmrhelper.data.local.db.dao.BackgroundImageDao
import com.asmrhelper.data.local.db.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AsmrDatabase =
        Room.databaseBuilder(
            context,
            AsmrDatabase::class.java,
            "asmr_helper.db"
        ).build()

    @Provides fun provideAudioDao(db: AsmrDatabase): AudioDao = db.audioDao()
    @Provides fun providePlaylistDao(db: AsmrDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideBackgroundImageDao(db: AsmrDatabase): BackgroundImageDao = db.backgroundImageDao()
}
```

```kotlin
package com.asmrhelper.di

import com.asmrhelper.data.repository.AudioRepositoryImpl
import com.asmrhelper.data.repository.PlaylistRepositoryImpl
import com.asmrhelper.data.repository.SettingsRepositoryImpl
import com.asmrhelper.domain.repository.AudioRepository
import com.asmrhelper.domain.repository.PlaylistRepository
import com.asmrhelper.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAudioRepository(impl: AudioRepositoryImpl): AudioRepository

    @Binds @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
```

```kotlin
package com.asmrhelper.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideMainPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context).build()

    @Provides
    @Singleton
    fun provideBackgroundPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context).build()
}
```

---

## Task 8: 播放引擎 (PlayerManager + Foreground Service)

**Files:**
- Create: `app/src/main/java/com/asmrhelper/player/PlayerEvent.kt`
- Create: `app/src/main/java/com/asmrhelper/player/PlayerManager.kt`
- Create: `app/src/main/java/com/asmrhelper/player/AsmrMediaService.kt`
- Create: `app/src/main/java/com/asmrhelper/util/Constants.kt`

- [ ] **Step 1: 创建 PlayerEvent.kt + Constants.kt**

```kotlin
package com.asmrhelper.player

import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.LoopMode

sealed interface PlayerEvent {
    data class Play(val audio: Audio, val playlist: List<Audio> = emptyList()) : PlayerEvent
    data object Pause : PlayerEvent
    data object Resume : PlayerEvent
    data object Next : PlayerEvent
    data object Previous : PlayerEvent
    data class SeekTo(val positionMs: Long) : PlayerEvent
    data class SetLoopMode(val mode: LoopMode) : PlayerEvent
    data object ToggleBackground : PlayerEvent
}
```

```kotlin
package com.asmrhelper.util

object Constants {
    const val NOTIFICATION_CHANNEL_ID = "asmr_playback"
    const val NOTIFICATION_CHANNEL_NAME = "ASMR 播放"
    const val NOTIFICATION_ID = 1001

    const val PLAYER_MAIN = "main_player"
    const val PLAYER_BACKGROUND = "background_player"
}
```

- [ ] **Step 2: 创建 PlayerManager.kt**

```kotlin
package com.asmrhelper.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.domain.model.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerManager @Inject constructor(
    private val mainPlayer: ExoPlayer,
    private val backgroundPlayer: ExoPlayer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentPlaylist: List<Audio> = emptyList()
    private var currentIndex: Int = -1

    init {
        mainPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
                }
            }
        })

        // 每秒更新进度
        scope.launch {
            while (true) {
                if (mainPlayer.isPlaying) {
                    _state.update {
                        it.copy(
                            progressMs = mainPlayer.currentPosition,
                            durationMs = mainPlayer.duration.takeIf { d -> d > 0 } ?: it.durationMs
                        )
                    }
                }
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    fun handleEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.Play -> play(event.audio, event.playlist)
            PlayerEvent.Pause -> mainPlayer.pause()
            PlayerEvent.Resume -> mainPlayer.play()
            PlayerEvent.Next -> skipToNext()
            PlayerEvent.Previous -> skipToPrevious()
            is PlayerEvent.SeekTo -> mainPlayer.seekTo(event.positionMs)
            is PlayerEvent.SetLoopMode -> _state.update { it.copy(loopMode = event.mode) }
            PlayerEvent.ToggleBackground -> toggleBackground()
        }
    }

    private fun play(audio: Audio, playlist: List<Audio>) {
        currentPlaylist = playlist.ifEmpty { listOf(audio) }
        currentIndex = currentPlaylist.indexOfFirst { it.id == audio.id }.coerceAtLeast(0)

        val mediaItem = MediaItem.fromUri(audio.filePath)
        mainPlayer.setMediaItem(mediaItem)
        mainPlayer.prepare()
        mainPlayer.play()
        _state.update { it.copy(currentAudio = audio) }
    }

    private fun skipToNext() {
        if (currentPlaylist.isEmpty() || currentIndex < 0) return
        val nextIndex = (currentIndex + 1) % currentPlaylist.size
        if (nextIndex == 0 && _state.value.loopMode != LoopMode.LIST) return
        play(currentPlaylist[nextIndex], currentPlaylist)
    }

    private fun skipToPrevious() {
        if (currentPlaylist.isEmpty() || currentIndex < 0) return
        val prevIndex = if (mainPlayer.currentPosition > 3000L) currentIndex
        else (currentIndex - 1 + currentPlaylist.size) % currentPlaylist.size
        play(currentPlaylist[prevIndex], currentPlaylist)
    }

    private fun handlePlaybackEnded() {
        when (_state.value.loopMode) {
            LoopMode.SINGLE -> {
                mainPlayer.seekTo(0)
                mainPlayer.play()
            }
            LoopMode.LIST -> skipToNext()
            LoopMode.NONE -> {
                _state.update { it.copy(isPlaying = false) }
            }
        }
    }

    private fun toggleBackground() {
        val isPlaying = backgroundPlayer.isPlaying
        if (isPlaying) {
            backgroundPlayer.pause()
        } else {
            backgroundPlayer.play()
        }
        _state.update { it.copy(isBackgroundPlaying = !isPlaying) }
    }

    /** 设置背景音轨的音频文件 */
    fun setBackgroundAudio(filePath: String) {
        backgroundPlayer.setMediaItem(MediaItem.fromUri(filePath))
        backgroundPlayer.prepare()
        backgroundPlayer.playWhenReady = true
    }

    /** 停止并释放背景音轨 */
    fun stopBackground() {
        backgroundPlayer.stop()
        _state.update { it.copy(isBackgroundPlaying = false) }
    }

    fun release() {
        mainPlayer.release()
        backgroundPlayer.release()
    }
}
```

- [ ] **Step 3: 创建 AsmrMediaService.kt**

```kotlin
package com.asmrhelper.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.asmrhelper.MainActivity
import com.asmrhelper.R
import com.asmrhelper.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AsmrMediaService : Service() {

    @Inject lateinit var playerManager: PlayerManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ASMRHelper")
            .setContentText("音频播放中...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(true)
            .build()

        startForeground(Constants.NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        playerManager.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
```

---

## Task 9: 导航框架 (Screen 路由 + BottomNavBar + NavHost)

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/navigation/Screen.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/navigation/BottomNavBar.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt`
- Create: `app/src/main/java/com/asmrhelper/util/PrivacyMaskUtil.kt`
- Create: placeholder Screens (Profile, Settings, Play 骨架)

- [ ] **Step 1: 创建 Screen.kt**

```kotlin
package com.asmrhelper.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Play : Screen("play", "播放", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow)
    data object Profile : Screen("profile", "主页", Icons.Filled.Person, Icons.Outlined.Person)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)

    companion object {
        val bottomNavItems = listOf(Play, Profile, Settings)
    }
}
```

- [ ] **Step 2: 创建 PrivacyMaskUtil.kt**

```kotlin
package com.asmrhelper.util

/** 隐私模式：除首尾字符外替换为 * */
fun String.maskPrivacy(): String {
    if (length <= 2) return this
    return first() + "*".repeat(length - 2) + last()
}
```

- [ ] **Step 3: 创建 BottomNavBar.kt**

```kotlin
package com.asmrhelper.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asmrhelper.ui.theme.AccentPurple
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar(
        containerColor = DarkSurface,
        tonalElevation = 0.dp
    ) {
        Screen.bottomNavItems.forEach { screen ->
            val isSelected = currentRoute == screen.route

            val iconColor by animateColorAsState(
                targetValue = if (isSelected) AccentPurple else TextHint,
                animationSpec = tween(300)
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) TextPrimary else TextHint,
                animationSpec = tween(300)
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(screen) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                        contentDescription = screen.label,
                        modifier = Modifier.size(24.dp),
                        tint = iconColor
                    )
                },
                label = {
                    Text(
                        text = screen.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = AccentPurple.copy(alpha = 0.12f)
                )
            )
        }
    }
}
```

- [ ] **Step 4: 创建占位 Screen + NavHost**

```kotlin
package com.asmrhelper.ui.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlayScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("🎵 播放页 — 骨架占位")
    }
}
```

```kotlin
package com.asmrhelper.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("👤 主页 — 骨架占位")
    }
}
```

```kotlin
package com.asmrhelper.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("⚙️ 设置 — 骨架占位")
    }
}
```

- [ ] **Step 5: 创建 AsmrNavHost.kt**

```kotlin
package com.asmrhelper.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.asmrhelper.ui.play.PlayScreen
import com.asmrhelper.ui.profile.ProfileScreen
import com.asmrhelper.ui.settings.SettingsScreen

@Composable
fun AsmrNavHost(modifier: Modifier = Modifier) {
    var currentScreen by rememberSaveable { mutableStateOf<Screen>(Screen.Play) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            BottomNavBar(
                currentRoute = currentScreen.route,
                onNavigate = { currentScreen = it }
            )
        },
        containerColor = com.asmrhelper.ui.theme.DarkBackground
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                val screenOrder = listOf("play", "profile", "settings")
                val fromIdx = screenOrder.indexOf(initialState.route)
                val toIdx = screenOrder.indexOf(targetState.route)
                val direction = if (toIdx >= fromIdx) 1 else -1
                (slideInHorizontally { it * direction } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it * direction } + fadeOut())
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                Screen.Play -> PlayScreen()
                Screen.Profile -> ProfileScreen()
                Screen.Settings -> SettingsScreen()
            }
        }
    }
}
```

- [ ] **Step 6: 更新 MainActivity.kt — 挂载 NavHost**

将 MainActivity 的 `setContent` 内容更新为：
```kotlin
ASMRHelperTheme {
    AsmrNavHost()
}
```

---

## Task 10: 播放器主界面 PlayScreen (完整 UI)

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/components/PlayPauseButton.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/components/AsmrDropdownMenu.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/components/BackgroundImage.kt`

- [ ] **Step 1: 创建 PlayViewModel.kt**

```kotlin
package com.asmrhelper.ui.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.domain.model.PlayerState
import com.asmrhelper.domain.repository.AudioRepository
import com.asmrhelper.domain.repository.SettingsRepository
import com.asmrhelper.player.PlayerEvent
import com.asmrhelper.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayUiState(
    val playerState: PlayerState = PlayerState(),
    val audioList: List<Audio> = emptyList(),
    val displayTitle: String = "",
    val backgroundImagePath: String? = null,
    val menuExpanded: Boolean = false
)

@HiltViewModel
class PlayViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val audioRepository: AudioRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState = combine(
        playerManager.state,
        audioRepository.getAllAudio(),
        settingsRepository.isPrivacyMode()
    ) { playerState, audioList, isPrivacy ->
        val title = playerState.currentAudio?.title ?: ""
        PlayUiState(
            playerState = playerState,
            audioList = audioList,
            displayTitle = if (isPrivacy) title.maskPrivacy() else title
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayUiState())

    fun play(audio: Audio) {
        viewModelScope.launch {
            playerManager.handleEvent(PlayerEvent.Play(audio, uiState.value.audioList))
            // 查询绑定的背景图
            settingsRepository.getBindingForAudio(audio.id)?.let {
                // 背景图路径通过 uiState 更新 — 此处留待后续 Task 完善
            }
        }
    }

    fun togglePlayPause() {
        val event = if (uiState.value.playerState.isPlaying) PlayerEvent.Pause
        else PlayerEvent.Resume
        playerManager.handleEvent(event)
    }

    fun next() = playerManager.handleEvent(PlayerEvent.Next)
    fun previous() = playerManager.handleEvent(PlayerEvent.Previous)
    fun setLoopMode(mode: LoopMode) = playerManager.handleEvent(PlayerEvent.SetLoopMode(mode))
    fun toggleBackground() = playerManager.handleEvent(PlayerEvent.ToggleBackground)
    fun toggleMenu() { /* TODO: 后续 Task 完善下拉菜单状态 */ }
}
```

- [ ] **Step 2: 创建 PlayPauseButton.kt**

```kotlin
package com.asmrhelper.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.asmrhelper.ui.theme.AccentPurple
import com.asmrhelper.ui.theme.ControlWhite

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f)
    )

    Box(
        modifier = modifier
            .size(96.dp)
            .scale(scale)
            .background(AccentPurple, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            modifier = Modifier.size(48.dp),
            tint = ControlWhite
        )
    }
}
```

- [ ] **Step 3: 创建 AsmrDropdownMenu.kt**

```kotlin
package com.asmrhelper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

data class MenuItem(
    val id: String,
    val label: String
)

@Composable
fun AsmrDropdownMenu(
    items: List<MenuItem>,
    onItemClick: (MenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "菜单",
                tint = TextPrimary
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = false }
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .padding(4.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        Text(
                            text = item.label,
                            color = TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = false
                                    onItemClick(item)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        if (index < items.lastIndex) {
                            Divider(color = TextSecondary.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: 重写 PlayScreen.kt**

```kotlin
package com.asmrhelper.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.ui.components.AsmrDropdownMenu
import com.asmrhelper.ui.components.MenuItem
import com.asmrhelper.ui.components.PlayPauseButton
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

@Composable
fun PlayScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 右上角下拉菜单
        AsmrDropdownMenu(
            items = listOf(
                MenuItem("playlist", "播放列表"),
                MenuItem("favorites", "我的收藏"),
                MenuItem("scan", "本地文件扫描"),
                MenuItem("file_manager", "文件管理器"),
                MenuItem("playback_settings", "播放设置")
            ),
            onItemClick = { /* TODO: 后续 Task 处理导航 */ },
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // 中部播放控制区
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 音频标题
            AnimatedVisibility(
                visible = state.displayTitle.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = state.displayTitle.ifEmpty { "未在播放" },
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 艺术家
            state.playerState.currentAudio?.artist?.let { artist ->
                Text(
                    text = artist,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(48.dp))
            } ?: Spacer(modifier = Modifier.height(48.dp))

            // 播放控制行：上一首 | 播放/暂停 | 下一首
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(40.dp),
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                PlayPauseButton(
                    isPlaying = state.playerState.isPlaying,
                    onClick = { viewModel.togglePlayPause() }
                )

                Spacer(modifier = Modifier.width(24.dp))

                IconButton(onClick = { viewModel.next() }) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(40.dp),
                        tint = TextPrimary
                    )
                }
            }
        }

        // 进度条区域（底部）
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // 时间显示行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(state.playerState.progressMs),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = formatDuration(state.playerState.durationMs),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
```

---

## 验证清单

完成所有 Task 后：
- [ ] `app/build.gradle.kts` 无红色依赖错误
- [ ] Room 编译通过 (KSP 生成 DAO 实现)
- [ ] Hilt 编译通过 (Dagger 生成 DI 代码)
- [ ] 项目可编译安装到模拟器
- [ ] 底部三 Tab 切换有 fade+slide 动画
- [ ] 播放/暂停按钮有点按缩放反馈
- [ ] 右上角菜单可展开/收起

---

## 后续迭代 (不在此 Plan 范围)

- 定时器倒计时 UI 与逻辑
- 播放列表详情页 (增删改查 UI)
- 本地文件扫描 UI 触发
- 文件管理器浏览器
- 背景图库管理页
- 设置页完整 UI
- 主页账户登录/注册 UI
- 通知栏媒体控制 (MediaSession)
