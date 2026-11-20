package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Creator",
    val email: String? = null,
    val photoUrl: String? = null,
    val oauthConnected: Boolean = false,
    val syncIntervalSeconds: Long = 15L,
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class TrackedChannel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val title: String = "",
    val handle: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val subscriberCount: Long = 0L,
    val viewCount: Long = 0L,
    val videoCount: Long = 0L,
    val latestLikeCount: Long = 0L,
    val milestoneStep: Long = 10_000L,
    val alertsEnabled: Boolean = true,
    val pinnedToWidget: Boolean = false,
    val hiddenSubscriberCount: Boolean = false,
    val uploadsPlaylistId: String? = null,
    @ServerTimestamp val lastSyncedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChannelSnapshot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val subscriberCount: Long = 0L,
    val viewCount: Long = 0L,
    val videoCount: Long = 0L,
    val likeCount: Long = 0L,
    @ServerTimestamp val recordedAt: Timestamp? = null,
)

enum class AlertMetricType {
    SUBSCRIBERS,
    VIEWS,
    LIKES,
    VIDEOS;

    companion object {
        fun fromRaw(raw: String?): AlertMetricType =
            enumValues<AlertMetricType>().firstOrNull { it.name == raw } ?: SUBSCRIBERS
    }
}

@Keep
@IgnoreExtraProperties
data class AlertEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    val metricType: String = AlertMetricType.SUBSCRIBERS.name,
    val title: String = "",
    val message: String = "",
    val milestoneValue: Long = 0L,
    val deltaValue: Long = 0L,
    val acknowledged: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class YouTubeChannelSearchResult(
    val channelId: String,
    val title: String,
    val handle: String,
    val description: String,
    val thumbnailUrl: String,
    val subscriberCount: Long,
    val viewCount: Long,
    val videoCount: Long,
    val hiddenSubscriberCount: Boolean,
    val uploadsPlaylistId: String,
)
