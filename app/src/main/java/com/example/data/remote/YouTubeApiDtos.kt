package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YouTubeChannelsResponse(
    @Json(name = "items") val items: List<YouTubeChannelItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class YouTubeChannelItem(
    @Json(name = "id") val id: String = "",
    @Json(name = "snippet") val snippet: ChannelSnippet? = null,
    @Json(name = "statistics") val statistics: ChannelStatistics? = null,
    @Json(name = "contentDetails") val contentDetails: ChannelContentDetails? = null,
)

@JsonClass(generateAdapter = true)
data class ChannelSnippet(
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String = "",
    @Json(name = "customUrl") val customUrl: String? = null,
    @Json(name = "thumbnails") val thumbnails: ThumbnailSet? = null,
)

@JsonClass(generateAdapter = true)
data class ChannelStatistics(
    @Json(name = "viewCount") val viewCount: String? = null,
    @Json(name = "subscriberCount") val subscriberCount: String? = null,
    @Json(name = "hiddenSubscriberCount") val hiddenSubscriberCount: Boolean = false,
    @Json(name = "videoCount") val videoCount: String? = null,
)

@JsonClass(generateAdapter = true)
data class ChannelContentDetails(
    @Json(name = "relatedPlaylists") val relatedPlaylists: RelatedPlaylists? = null,
)

@JsonClass(generateAdapter = true)
data class RelatedPlaylists(
    @Json(name = "uploads") val uploads: String? = null,
)

@JsonClass(generateAdapter = true)
data class ThumbnailSet(
    @Json(name = "default") val defaultThumb: ThumbnailItem? = null,
    @Json(name = "medium") val medium: ThumbnailItem? = null,
    @Json(name = "high") val high: ThumbnailItem? = null,
) {
    fun bestUrl(): String = high?.url ?: medium?.url ?: defaultThumb?.url.orEmpty()
}

@JsonClass(generateAdapter = true)
data class ThumbnailItem(
    @Json(name = "url") val url: String = "",
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchResponse(
    @Json(name = "items") val items: List<YouTubeSearchItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchItem(
    @Json(name = "id") val id: YouTubeSearchId? = null,
    @Json(name = "snippet") val snippet: SearchSnippet? = null,
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchId(
    @Json(name = "kind") val kind: String? = null,
    @Json(name = "channelId") val channelId: String? = null,
)

@JsonClass(generateAdapter = true)
data class SearchSnippet(
    @Json(name = "channelId") val channelId: String? = null,
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String = "",
    @Json(name = "thumbnails") val thumbnails: ThumbnailSet? = null,
)

@JsonClass(generateAdapter = true)
data class YouTubePlaylistItemsResponse(
    @Json(name = "items") val items: List<PlaylistItemDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PlaylistItemDto(
    @Json(name = "contentDetails") val contentDetails: PlaylistContentDetails? = null,
)

@JsonClass(generateAdapter = true)
data class PlaylistContentDetails(
    @Json(name = "videoId") val videoId: String? = null,
    @Json(name = "videoPublishedAt") val videoPublishedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class YouTubeVideosResponse(
    @Json(name = "items") val items: List<YouTubeVideoItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoItem(
    @Json(name = "id") val id: String = "",
    @Json(name = "snippet") val snippet: VideoSnippet? = null,
    @Json(name = "statistics") val statistics: VideoStatistics? = null,
    @Json(name = "contentDetails") val contentDetails: VideoContentDetails? = null,
)

@JsonClass(generateAdapter = true)
data class VideoSnippet(
    @Json(name = "title") val title: String = "",
    @Json(name = "publishedAt") val publishedAt: String = "",
    @Json(name = "thumbnails") val thumbnails: ThumbnailSet? = null,
)

@JsonClass(generateAdapter = true)
data class VideoStatistics(
    @Json(name = "viewCount") val viewCount: String? = null,
    @Json(name = "likeCount") val likeCount: String? = null,
    @Json(name = "commentCount") val commentCount: String? = null,
)

@JsonClass(generateAdapter = true)
data class VideoContentDetails(
    @Json(name = "duration") val duration: String? = null,
)
