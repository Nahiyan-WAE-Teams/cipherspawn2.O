package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.model.UserProfile
import com.example.data.remote.ApiAuthStatus
import com.example.ui.components.ChannelAvatar
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.UiState
import com.example.worker.ChannelSyncWorker
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun StudioSettingsScreen(
    userProfileState: UiState<UserProfile?>,
    apiAuthStatus: ApiAuthStatus,
    themePreference: ThemePreference,
    pinnedChannelTitle: String?,
    onAuthorizeOAuth: () -> Unit,
    onUpdateSyncInterval: (Long) -> Unit,
    onToggleMasterNotifications: (Boolean) -> Unit,
    onSetThemePreference: (ThemePreference) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentUser = Firebase.auth.currentUser
    val profile = (userProfileState as? UiState.Success)?.data
    val currentInterval = profile?.syncIntervalSeconds ?: 15L
    val notificationsEnabled = profile?.notificationsEnabled ?: true

    val intervalOptions = listOf(10L, 15L, 30L, 60L, 300L)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("studio_settings_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "settings_header") {
            Column {
                Text(
                    text = "Studio & Security",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "OAuth 2.0 credentials, sync engine cadence, widgets & account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Account Card
        item(key = "account_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ChannelAvatar(
                            imageUrl = currentUser?.photoUrl?.toString().orEmpty(),
                            title = currentUser?.displayName ?: "Creator",
                            size = 50.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.displayName ?: profile?.displayName ?: "Creator Studio",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = currentUser?.email ?: "Signed in with Google",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        // OAuth 2.0 & API Security Card
        item(key = "oauth_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "YouTube OAuth 2.0 & API Management",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    StatusRow(
                        label = "OAuth 2.0 Scope (youtube.readonly)",
                        active = apiAuthStatus.oauthConnected,
                        activeText = "Authorized",
                        inactiveText = "Not connected",
                    )
                    StatusRow(
                        label = "YouTube API Key (Secrets / BuildConfig)",
                        active = apiAuthStatus.apiKeyConfigured,
                        activeText = "Configured",
                        inactiveText = "Set in AI Studio Secrets",
                    )

                    Button(
                        onClick = onAuthorizeOAuth,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_oauth_button"),
                    ) {
                        Text(
                            if (apiAuthStatus.oauthConnected) "Refresh YouTube OAuth 2.0 Token"
                            else "Authorize YouTube OAuth 2.0"
                        )
                    }
                }
            }
        }

        // Sync Engine & Notifications
        item(key = "sync_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = "Real-Time Polling & Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Milestone System Alerts",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Trigger push alerts on subscriber milestones & new uploads",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = onToggleMasterNotifications,
                            modifier = Modifier.testTag("master_notifications_switch"),
                        )
                    }

                    Text(
                        text = "Live Dashboard Polling Cadence",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(intervalOptions) { sec ->
                            val label = if (sec >= 60L) "${sec / 60L}m" else "${sec}s"
                            FilterChip(
                                selected = currentInterval == sec,
                                onClick = { onUpdateSyncInterval(sec) },
                                label = { Text(label) },
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val req = OneTimeWorkRequestBuilder<ChannelSyncWorker>().build()
                            WorkManager.getInstance(context).enqueueUniqueWork(
                                "tubepulse_manual_bg_sync",
                                ExistingWorkPolicy.KEEP,
                                req,
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Run Background Widget Sync Worker Now")
                    }
                }
            }
        }

        // Widget & Theme Card
        item(key = "widget_theme_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "Home Screen Widget & Theme",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = if (pinnedChannelTitle != null) {
                            "Pinned widget channel: $pinnedChannelTitle. Long-press your Android home screen to add the TubePulse Live Stats widget."
                        } else {
                            "No channel pinned yet. Pin any channel from the Pulse tab, then add the TubePulse Live Stats widget to your home screen."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = "Studio Appearance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = themePreference == pref,
                                onClick = { onSetThemePreference(pref) },
                                label = {
                                    Text(
                                        when (pref) {
                                            ThemePreference.DARK -> "Obsidian Dark"
                                            ThemePreference.LIGHT -> "Studio Light"
                                            ThemePreference.SYSTEM -> "System"
                                        }
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // Security Architecture Disclosure
        item(key = "security_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Zero-Trust Security Architecture",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Per-user isolated Firestore rules, HTTPS-only YouTube Data API v3 transport, in-memory short-lived OAuth 2.0 access tokens, and encrypted local Room cache.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "settings_spacer") {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    active: Boolean,
    activeText: String,
    inactiveText: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (active) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = if (active) activeText else inactiveText,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
