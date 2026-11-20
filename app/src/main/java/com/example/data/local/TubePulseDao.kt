package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TubePulseDao {

    @Query("SELECT * FROM cached_channels WHERE userId = :userId ORDER BY pinnedToWidget DESC, subscriberCount DESC")
    fun observeChannels(userId: String): Flow<List<CachedChannelEntity>>

    @Query("SELECT * FROM cached_channels WHERE userId = :userId AND channelId = :channelId LIMIT 1")
    fun observeChannel(userId: String, channelId: String): Flow<CachedChannelEntity?>

    @Query("SELECT * FROM cached_channels WHERE userId = :userId ORDER BY pinnedToWidget DESC, subscriberCount DESC")
    suspend fun getChannelsSync(userId: String): List<CachedChannelEntity>

    @Query("SELECT * FROM cached_channels ORDER BY pinnedToWidget DESC, lastSyncedAtMillis DESC LIMIT 4")
    suspend fun getWidgetChannelsSync(): List<CachedChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<CachedChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannel(channel: CachedChannelEntity)

    @Query("DELETE FROM cached_channels WHERE userId = :userId AND channelId = :channelId")
    suspend fun deleteChannel(userId: String, channelId: String)

    @Query("DELETE FROM cached_channels WHERE userId = :userId")
    suspend fun clearChannelsForUser(userId: String)

    // Videos
    @Query("SELECT * FROM cached_videos WHERE userId = :userId AND channelId = :channelId ORDER BY publishedAt DESC")
    fun observeVideosForChannel(userId: String, channelId: String): Flow<List<CachedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideos(videos: List<CachedVideoEntity>)

    @Query("DELETE FROM cached_videos WHERE userId = :userId AND channelId = :channelId")
    suspend fun deleteVideosForChannel(userId: String, channelId: String)

    // Snapshots
    @Query("SELECT * FROM cached_snapshots WHERE userId = :userId AND channelId = :channelId ORDER BY recordedAtMillis ASC")
    fun observeSnapshots(userId: String, channelId: String): Flow<List<CachedSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshots(snapshots: List<CachedSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: CachedSnapshotEntity)

    @Query("DELETE FROM cached_snapshots WHERE userId = :userId AND channelId = :channelId")
    suspend fun deleteSnapshotsForChannel(userId: String, channelId: String)
}
