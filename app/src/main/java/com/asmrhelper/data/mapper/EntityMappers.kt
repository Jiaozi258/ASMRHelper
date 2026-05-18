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
