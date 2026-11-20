package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.NumberFormatter
import com.example.data.local.CachedChannelEntity
import com.example.ui.components.ChannelAvatar
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricSmall

@Composable
fun AnalyticsScreen(
    channels: List<CachedChannelEntity>,
    onSelectChannel: (CachedChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSubs = channels.sumOf { it.subscriberCount }
    val totalViews = channels.sumOf { it.viewCount }
    val totalVideos = channels.sumOf { it.videoCount }
    val totalLikes = channels.sumOf { it.latestLikeCount }
    val maxSubs = channels.maxOfOrNull { it.subscriberCount }?.coerceAtLeast(1L) ?: 1L

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("analytics_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "analytics_header") {
            Column {
                Text(
                    text = "Portfolio Analytics",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Aggregated audience reach and video engagement across tracked channels",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "portfolio_totals") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PortfolioStatTile(
                        title = "COMBINED SUBS",
                        value = NumberFormatter.compact(totalSubs),
                        subtitle = "${NumberFormatter.exact(totalSubs)} exact",
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    PortfolioStatTile(
                        title = "COMBINED VIEWS",
                        value = NumberFormatter.compact(totalViews),
                        subtitle = "${NumberFormatter.exact(totalVideos)} videos",
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
                PortfolioStatTile(
                    title = "AGGREGATED RECENT VIDEO LIKES",
                    value = NumberFormatter.exact(totalLikes),
                    subtitle = "Across latest 10 uploads per channel",
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (channels.isEmpty()) {
            item(key = "analytics_empty") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = "Track at least one public YouTube channel from the Pulse tab to unlock comparative analytics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                    )
                }
            }
        } else {
            item(key = "leaderboard_label") {
                Text(
                    text = "CHANNEL SHARE & ENGAGEMENT LEADERBOARD",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(channels.sortedByDescending { it.subscriberCount }, key = { it.channelId }) { channel ->
                val ratio = (channel.subscriberCount.toFloat() / maxSubs.toFloat()).coerceIn(0.04f, 1f)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectChannel(channel) },
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ChannelAvatar(
                                imageUrl = channel.thumbnailUrl,
                                title = channel.title,
                                size = 38.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${NumberFormatter.compact(channel.viewCount)} views · ${NumberFormatter.compact(channel.latestLikeCount)} recent likes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = NumberFormatter.compact(channel.subscriberCount),
                                style = MonoMetricSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }

        item(key = "analytics_spacer") {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun PortfolioStatTile(
    title: String,
    value: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MonoMetricLarge,
                color = accent,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
