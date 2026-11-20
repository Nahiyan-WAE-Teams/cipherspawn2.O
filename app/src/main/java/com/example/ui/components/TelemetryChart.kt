package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.NumberFormatter
import com.example.data.local.CachedSnapshotEntity
import com.example.ui.theme.MonoMetricSmall
import kotlin.math.roundToInt

enum class ChartMetric(val label: String) {
    SUBSCRIBERS("Subs"),
    VIEWS("Views"),
    LIKES("Likes"),
}

@Composable
fun InteractiveTelemetryChart(
    snapshots: List<CachedSnapshotEntity>,
    modifier: Modifier = Modifier,
) {
    var selectedMetric by remember { mutableStateOf(ChartMetric.SUBSCRIBERS) }
    var scrubbedIndex by remember { mutableIntStateOf(-1) }

    val lineColor = when (selectedMetric) {
        ChartMetric.SUBSCRIBERS -> MaterialTheme.colorScheme.primary
        ChartMetric.VIEWS -> MaterialTheme.colorScheme.tertiary
        ChartMetric.LIKES -> MaterialTheme.colorScheme.secondary
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val values = remember(snapshots, selectedMetric) {
        snapshots.map { snap ->
            when (selectedMetric) {
                ChartMetric.SUBSCRIBERS -> snap.subscriberCount
                ChartMetric.VIEWS -> snap.viewCount
                ChartMetric.LIKES -> snap.likeCount
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "TELEMETRY CURVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val activeValue = if (scrubbedIndex in values.indices) {
                        values[scrubbedIndex]
                    } else {
                        values.lastOrNull() ?: 0L
                    }
                    val activeTime = if (scrubbedIndex in snapshots.indices) {
                        NumberFormatter.relativeTime(snapshots[scrubbedIndex].recordedAtMillis)
                    } else if (snapshots.isNotEmpty()) {
                        "Latest · ${NumberFormatter.relativeTime(snapshots.last().recordedAtMillis)}"
                    } else {
                        "Awaiting snapshots"
                    }
                    Text(
                        text = "${NumberFormatter.exact(activeValue)} · $activeTime",
                        style = MonoMetricSmall,
                        color = lineColor,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChartMetric.entries.forEach { metric ->
                        FilterChip(
                            selected = selectedMetric == metric,
                            onClick = {
                                selectedMetric = metric
                                scrubbedIndex = -1
                            },
                            label = {
                                Text(
                                    text = metric.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = lineColor.copy(alpha = 0.2f),
                                selectedLabelColor = lineColor,
                            ),
                            modifier = Modifier.testTag("chart_metric_${metric.name.lowercase()}"),
                        )
                    }
                }
            }

            if (values.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (values.isEmpty()) {
                            "Sync channel to begin recording live snapshots."
                        } else {
                            "1 snapshot recorded (${NumberFormatter.exact(values.first())}). Next sync builds the live curve."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(values) {
                            detectTapGestures { offset ->
                                val idx = ((offset.x / size.width.coerceAtLeast(1)) * (values.size - 1))
                                    .roundToInt()
                                    .coerceIn(0, values.lastIndex)
                                scrubbedIndex = idx
                            }
                        }
                        .pointerInput(values) {
                            detectDragGestures(
                                onDragEnd = { scrubbedIndex = -1 },
                                onDragCancel = { scrubbedIndex = -1 },
                            ) { change, _ ->
                                change.consume()
                                val idx = ((change.position.x / size.width.coerceAtLeast(1)) * (values.size - 1))
                                    .roundToInt()
                                    .coerceIn(0, values.lastIndex)
                                scrubbedIndex = idx
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val padV = 18f
                        val usableH = (h - padV * 2f).coerceAtLeast(1f)

                        // Studio gridlines
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        for (step in 0..3) {
                            val y = padV + (usableH / 3f) * step
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.5f,
                                pathEffect = dashEffect,
                            )
                        }

                        val minVal = values.minOrNull() ?: 0L
                        val maxVal = values.maxOrNull() ?: 1L
                        val span = (maxVal - minVal).coerceAtLeast(1L).toFloat()

                        val points = values.mapIndexed { index, v ->
                            val x = (index.toFloat() / (values.size - 1).toFloat()) * w
                            val norm = (v - minVal).toFloat() / span
                            val y = (padV + usableH) - (norm * usableH)
                            Offset(x, y)
                        }

                        val strokePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val curr = points[i]
                                val midX = (prev.x + curr.x) / 2f
                                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(strokePath)
                            lineTo(points.last().x, h)
                            lineTo(points.first().x, h)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.32f),
                                    lineColor.copy(alpha = 0.0f),
                                ),
                                startY = 0f,
                                endY = h,
                            ),
                        )

                        drawPath(
                            path = strokePath,
                            color = lineColor,
                            style = Stroke(
                                width = 5f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )

                        val focusIndex = if (scrubbedIndex in points.indices) {
                            scrubbedIndex
                        } else {
                            points.lastIndex
                        }
                        val focusPoint = points[focusIndex]

                        if (scrubbedIndex in points.indices) {
                            drawLine(
                                color = lineColor.copy(alpha = 0.55f),
                                start = Offset(focusPoint.x, 0f),
                                end = Offset(focusPoint.x, h),
                                strokeWidth = 2f,
                            )
                        }

                        drawCircle(
                            color = lineColor.copy(alpha = 0.25f),
                            radius = 14f,
                            center = focusPoint,
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 7f,
                            center = focusPoint,
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3f,
                            center = focusPoint,
                        )
                    }
                }
            }
        }
    }
}
