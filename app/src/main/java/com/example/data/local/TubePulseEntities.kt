package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_channels",
    indices = [Index(value = ["userId"])]
)
data class CachedChannelEntity(
    @PrimaryKey val channelId: String,
    val userId: String,
    val title: String,
    val handle: String,
    val description: String,
    val thumbnailUrl: String,
    val subscriberCount: Long,
    val previousSubscriberCount: Long,
    val viewCount: Long,
    val previousViewCount: Long,
    val videoCount: Long,
    val latestLikeCount: Long,
    val previousLikeCount: Long,
    val milestoneStep: Long,
    val alertsEnabled: Boolean,
    val pinnedToWidget: Boolean,
    val hiddenSubscriberCount: Boolean,
    val uploadsPlaylistId: String,
    val lastSyncedAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "cached_videos",
    indices = [Index(value = ["channelId", "userId"])]
)
data class CachedVideoEntity(
    @PrimaryKey val videoId: String,
    val channelId: String,
    val userId: String,
    val title: String,
    val thumbnailUrl: String,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val publishedAt: String,
    val durationIso: String,
    val cachedAtMillis: Long,
)

@Entity(
    tableName = "cached_snapshots",
    indices = [Index(value = ["channelId", "userId", "recordedAtMillis"])]
)
data class CachedSnapshotEntity(
    @PrimaryKey val snapshotId: String,
    val channelId: String,
    val userId: String,
    val subscriberCount: Long,
    val viewCount: Long,
    val videoCount: Long,
    val likeCount: Long,
    val recordedAtMillis: Long,
)
