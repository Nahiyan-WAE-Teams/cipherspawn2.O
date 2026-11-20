package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.NumberFormatter
import com.example.data.local.CachedChannelEntity
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.VelocityAmber
import kotlin.math.roundToLong

@Composable
fun LivePulseBadge(
    isLive: Boolean,
    isOnline: Boolean,
    countdownSeconds: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val badgeColor = when {
        !isOnline -> VelocityAmber
        isLive -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = badgeColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onToggle)
            .testTag("live_pulse_toggle"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(if (isLive && isOnline) pulseAlpha else 1f)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Text(
                text = when {
                    !isOnline -> "CACHED"
                    isLive -> "LIVE · ${countdownSeconds}s"
                    else -> "PAUSED"
                },
                style = MaterialTheme.typography.labelMedium,
                color = badgeColor,
            )
            Icon(
                imageVector = if (isLive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isLive) "Pause live stream" else "Resume live stream",
                tint = badgeColor,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
fun OfflineModeBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = VelocityAmber.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, VelocityAmber.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("offline_mode_banner"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = VelocityAmber,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.offline_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun AnimatedOdometerText(
    targetValue: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    large: Boolean = true,
) {
    val animatedFloat by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f),
        label = "odometer_counter",
    )
    val displayValue = animatedFloat.roundToLong().coerceAtLeast(0L)
    Text(
        text = NumberFormatter.exact(displayValue),
        style = if (large) {
            MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
        } else {
            MonoMetricLarge
        },
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun ChannelAvatar(
    imageUrl: String,
    title: String,
    size: Dp = 48.dp,
    borderColor: Color = MaterialTheme.colorScheme.outline,
) {
    if (imageUrl.isNotBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "$title channel avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.5.dp, borderColor, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                        )
                    )
                )
                .border(1.5.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "Y",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ChannelTelemetryCard(
    channel: CachedChannelEntity,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subDelta = channel.subscriberCount - channel.previousSubscriberCount
    val borderColor = if (isSelected || channel.pinnedToWidget) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("channel_card_${channel.channelId}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChannelAvatar(
                    imageUrl = channel.thumbnailUrl,
                    title = channel.title,
                    size = 48.dp,
                    borderColor = if (channel.pinnedToWidget) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = channel.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (channel.pinnedToWidget) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                            ) {
                                Text(
                                    text = "WIDGET",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = buildString {
                            if (channel.handle.isNotBlank()) append(channel.handle).append(" · ")
                            append(NumberFormatter.relativeTime(channel.lastSyncedAtMillis))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pin_widget_${channel.channelId}"),
                ) {
                    Icon(
                        imageVector = if (channel.pinnedToWidget) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (channel.pinnedToWidget) "Unpin from widget" else "Pin to widget",
                        tint = if (channel.pinnedToWidget) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = onToggleAlerts,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_alerts_${channel.channelId}"),
                ) {
                    Icon(
                        imageVector = if (channel.alertsEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsOff,
                        contentDescription = if (channel.alertsEnabled) "Disable milestone alerts" else "Enable milestone alerts",
                        tint = if (channel.alertsEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // Primary Subscriber Odometer + Delta
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = if (channel.hiddenSubscriberCount) "SUBSCRIBERS (HIDDEN)" else "SUBSCRIBERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedOdometerText(
                        targetValue = channel.subscriberCount,
                        large = false,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (subDelta != 0L) {
                    val isPositive = subDelta > 0L
                    val deltaColor = if (isPositive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = deltaColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = NumberFormatter.delta(subDelta),
                            style = MonoMetricSmall,
                            color = deltaColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Secondary KPIs Row: Views, Videos, Likes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactMetricChip(
                    icon = Icons.Rounded.Visibility,
                    label = "Views",
                    value = NumberFormatter.compact(channel.viewCount),
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                CompactMetricChip(
                    icon = Icons.Rounded.VideoLibrary,
                    label = "Videos",
                    value = NumberFormatter.compact(channel.videoCount),
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                CompactMetricChip(
                    icon = Icons.Rounded.ThumbUp,
                    label = "Recent Likes",
                    value = NumberFormatter.compact(channel.latestLikeCount),
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun CompactMetricChip(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                style = MonoMetricSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
