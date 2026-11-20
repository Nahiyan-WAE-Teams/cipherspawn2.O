package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.NumberFormatter
import com.example.data.local.CachedChannelEntity
import com.example.data.remote.ApiAuthStatus
import com.example.ui.components.AnimatedOdometerText
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.ChannelTelemetryCard
import com.example.ui.components.CompactMetricChip
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.OfflineModeBanner
import com.example.ui.theme.MonoMetricSmall

@Composable
fun PulseDashboardScreen(
    channels: List<CachedChannelEntity>,
    selectedChannelId: String?,
    isOnline: Boolean,
    isLiveStreamActive: Boolean,
    isSyncing: Boolean,
    countdownSeconds: Int,
    apiAuthStatus: ApiAuthStatus,
    lastSyncError: String?,
    onToggleLiveStream: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenAddChannel: () -> Unit,
    onAuthorizeOAuth: () -> Unit,
    onSelectChannel: (CachedChannelEntity) -> Unit,
    onTogglePin: (CachedChannelEntity) -> Unit,
    onToggleAlerts: (CachedChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val featuredChannel = channels.firstOrNull { it.pinnedToWidget } ?: channels.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("pulse_dashboard_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top Command Bar
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TubePulse",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (channels.isEmpty()) "Broadcast Command Center"
                        else "${channels.size} channel${if (channels.size == 1) "" else "s"} synced",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LivePulseBadge(
                        isLive = isLiveStreamActive,
                        isOnline = isOnline,
                        countdownSeconds = countdownSeconds,
                        onToggle = onToggleLiveStream,
                    )
                    IconButton(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sync_now_button"),
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Sync all channels now",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }
        }

        // Offline Resilience Banner
        item(key = "offline_banner") {
            OfflineModeBanner(visible = !isOnline)
        }

        // OAuth / API Key Setup Callout when neither is active
        if (!apiAuthStatus.isReadyForRequests) {
            item(key = "api_setup_callout") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Connect YouTube Data API v3",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = "Authorize OAuth 2.0 to stream live YouTube subscriber, video, and like counts securely, or add YOUTUBE_API_KEY in AI Studio Secrets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onAuthorizeOAuth,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("dashboard_oauth_button"),
                        ) {
                            Text(stringResource(R.string.connect_youtube_oauth))
                        }
                    }
                }
            }
        }

        if (!lastSyncError.isNullOrBlank()) {
            item(key = "sync_error") {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = lastSyncError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }
            }
        }

        if (featuredChannel == null) {
            item(key = "empty_state") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_radar),
                            contentDescription = "Channel tracking radar illustration",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(18.dp)),
                        )
                        Text(
                            text = "Lock Onto Any YouTube Channel",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Search any public creator by @handle, name, or UC ID—or import your own channel via OAuth 2.0—to track subscribers, views, videos, and likes in real time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = onOpenAddChannel,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("empty_add_channel_button"),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.add_channel))
                            }
                            if (!apiAuthStatus.oauthConnected) {
                                OutlinedButton(
                                    onClick = onAuthorizeOAuth,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(50.dp),
                                ) {
                                    Text("OAuth 2.0")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Hero Spotlight Telemetry Card
            item(key = "hero_spotlight") {
                val delta = featuredChannel.subscriberCount - featuredChannel.previousSubscriberCount
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
                            )
                        ),
                    ),
                    onClick = { onSelectChannel(featuredChannel) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_spotlight_card"),
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                                        MaterialTheme.colorScheme.surface,
                                    )
                                )
                            )
                            .padding(18.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ChannelAvatar(
                                    imageUrl = featuredChannel.thumbnailUrl,
                                    title = featuredChannel.title,
                                    size = 54.dp,
                                    borderColor = MaterialTheme.colorScheme.secondary,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (featuredChannel.pinnedToWidget) "WIDGET SPOTLIGHT" else "TOP CHANNEL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                    Text(
                                        text = featuredChannel.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (featuredChannel.handle.isNotBlank()) {
                                        Text(
                                            text = featuredChannel.handle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (delta != 0L) {
                                    val deltaColor = if (delta > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = deltaColor.copy(alpha = 0.16f),
                                    ) {
                                        Text(
                                            text = NumberFormatter.delta(delta),
                                            style = MonoMetricSmall,
                                            color = deltaColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "LIVE SUBSCRIBER COUNT",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                AnimatedOdometerText(
                                    targetValue = featuredChannel.subscriberCount,
                                    large = true,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CompactMetricChip(
                                    icon = Icons.Rounded.Visibility,
                                    label = "Total Views",
                                    value = NumberFormatter.compact(featuredChannel.viewCount),
                                    accent = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactMetricChip(
                                    icon = Icons.Rounded.VideoLibrary,
                                    label = "Videos",
                                    value = NumberFormatter.exact(featuredChannel.videoCount),
                                    accent = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactMetricChip(
                                    icon = Icons.Rounded.ThumbUp,
                                    label = "Recent Likes",
                                    value = NumberFormatter.compact(featuredChannel.latestLikeCount),
                                    accent = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "section_channels") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "TRACKED CHANNELS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Tap for videos & curves",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(channels, key = { it.channelId }) { channel ->
                ChannelTelemetryCard(
                    channel = channel,
                    isSelected = channel.channelId == selectedChannelId,
                    onClick = { onSelectChannel(channel) },
                    onTogglePin = { onTogglePin(channel) },
                    onToggleAlerts = { onToggleAlerts(channel) },
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(76.dp))
        }
    }
}
