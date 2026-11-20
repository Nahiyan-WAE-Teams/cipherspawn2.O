package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import com.example.core.NumberFormatter
import com.example.data.local.CachedChannelEntity
import com.example.data.local.TubePulseDatabase
import com.example.worker.ChannelSyncWorker

class TubePulseGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, MEDIUM_SIZE, LARGE_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = TubePulseDatabase.getInstance(context.applicationContext).tubePulseDao()
        val channels = dao.getWidgetChannelsSync()

        provideContent {
            val prefs = currentState<Preferences>()
            val revision = prefs[KEY_REVISION] ?: 0
            GlanceTheme {
                WidgetBody(channels = channels, revision = revision)
            }
        }
    }

    @Composable
    private fun WidgetBody(
        channels: List<CachedChannelEntity>,
        revision: Int,
    ) {
        val size = LocalSize.current
        val isCompact = size.width < 220.dp
        val bgColor = ColorProvider(Color(0xFF0E121B))
        val cardColor = ColorProvider(Color(0xFF171D2A))
        val textPrimary = ColorProvider(Color(0xFFF3F6FC))
        val textSecondary = ColorProvider(Color(0xFF9BA7BE))
        val crimson = ColorProvider(Color(0xFFFF2A4B))
        val emerald = ColorProvider(Color(0xFF00E5A3))
        val sky = ColorProvider(Color(0xFF38BDF8))

        val primary = channels.firstOrNull()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgColor)
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top,
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TUBEPULSE · LIVE",
                    style = TextStyle(
                        color = emerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Box(
                    modifier = GlanceModifier
                        .background(cardColor)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(actionRunCallback<RefreshTubePulseWidgetAction>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_notification_pulse),
                            contentDescription = "Refresh widget stats",
                            modifier = GlanceModifier.size(14.dp),
                        )
                        if (!isCompact) {
                            Spacer(modifier = GlanceModifier.width(4.dp))
                            Text(
                                text = "Sync",
                                style = TextStyle(
                                    color = textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (primary == null) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(cardColor)
                        .cornerRadius(14.dp)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No channel pinned",
                            style = TextStyle(
                                color = textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = "Tap to track a YouTube channel in TubePulse",
                            style = TextStyle(color = textSecondary, fontSize = 11.sp),
                        )
                    }
                }
            } else {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(cardColor)
                        .cornerRadius(14.dp)
                        .padding(12.dp),
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = primary.title,
                                maxLines = 1,
                                style = TextStyle(
                                    color = textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            if (primary.handle.isNotBlank()) {
                                Text(
                                    text = primary.handle,
                                    maxLines = 1,
                                    style = TextStyle(color = textSecondary, fontSize = 11.sp),
                                )
                            }
                        }
                        Text(
                            text = NumberFormatter.relativeTime(primary.lastSyncedAtMillis),
                            style = TextStyle(color = textSecondary, fontSize = 10.sp),
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    Text(
                        text = NumberFormatter.exact(primary.subscriberCount),
                        style = TextStyle(
                            color = crimson,
                            fontSize = if (isCompact) 20.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "SUBSCRIBERS",
                        style = TextStyle(
                            color = textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )

                    if (!isCompact) {
                        Spacer(modifier = GlanceModifier.height(10.dp))
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            WidgetMiniMetric(
                                label = "VIEWS",
                                value = NumberFormatter.compact(primary.viewCount),
                                valueColor = sky,
                                labelColor = textSecondary,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            WidgetMiniMetric(
                                label = "VIDEOS",
                                value = NumberFormatter.compact(primary.videoCount),
                                valueColor = textPrimary,
                                labelColor = textSecondary,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            WidgetMiniMetric(
                                label = "LIKES",
                                value = NumberFormatter.compact(primary.latestLikeCount),
                                valueColor = emerald,
                                labelColor = textSecondary,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                        }
                    }
                }
            }
            if (revision < 0) {
                // Keeps revision read active in composition
                Spacer(modifier = GlanceModifier.height(0.dp))
            }
        }
    }

    @Composable
    private fun WidgetMiniMetric(
        label: String,
        value: String,
        valueColor: ColorProvider,
        labelColor: ColorProvider,
        modifier: GlanceModifier = GlanceModifier,
    ) {
        Column(modifier = modifier) {
            Text(
                text = value,
                style = TextStyle(
                    color = valueColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = label,
                style = TextStyle(
                    color = labelColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }

    companion object {
        val KEY_REVISION = intPreferencesKey("widget_revision")
        private val COMPACT_SIZE = DpSize(130.dp, 100.dp)
        private val MEDIUM_SIZE = DpSize(250.dp, 130.dp)
        private val LARGE_SIZE = DpSize(320.dp, 200.dp)

        suspend fun pushWidgetUpdate(context: Context) {
            val appContext = context.applicationContext
            val manager = GlanceAppWidgetManager(appContext)
            val widget = TubePulseGlanceWidget()
            val glanceIds = manager.getGlanceIds(TubePulseGlanceWidget::class.java)
            for (glanceId in glanceIds) {
                updateAppWidgetState(appContext, glanceId) { prefs ->
                    prefs[KEY_REVISION] = (prefs[KEY_REVISION] ?: 0) + 1
                }
                widget.update(appContext, glanceId)
            }
        }
    }
}

class RefreshTubePulseWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val request = OneTimeWorkRequestBuilder<ChannelSyncWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "tubepulse_widget_manual_sync",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
