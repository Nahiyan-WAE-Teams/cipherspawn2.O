package com.example.core

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object NumberFormatter {
    private val intFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

    fun exact(value: Long): String = intFormat.format(value)

    fun compact(value: Long): String {
        val absVal = abs(value)
        val sign = if (value < 0) "-" else ""
        return when {
            absVal >= 1_000_000_000L -> String.format(Locale.US, "%s%.2fB", sign, absVal / 1_000_000_000.0)
            absVal >= 1_000_000L -> String.format(Locale.US, "%s%.2fM", sign, absVal / 1_000_000.0)
            absVal >= 10_000L -> String.format(Locale.US, "%s%.1fK", sign, absVal / 1_000.0)
            else -> intFormat.format(value)
        }
    }

    fun delta(value: Long): String = when {
        value > 0 -> "+${compact(value)}"
        value < 0 -> compact(value)
        else -> "0"
    }

    fun relativeTime(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestampMillis <= 0L) return "Pending sync"
        val diffSeconds = ((nowMillis - timestampMillis) / 1000L).coerceAtLeast(0L)
        return when {
            diffSeconds < 10L -> "Just now"
            diffSeconds < 60L -> "${diffSeconds}s ago"
            diffSeconds < 3600L -> "${diffSeconds / 60L}m ago"
            diffSeconds < 86400L -> "${diffSeconds / 3600L}h ago"
            else -> "${diffSeconds / 86400L}d ago"
        }
    }

    fun engagementPercent(likes: Long, views: Long): String {
        if (views <= 0L) return "0.0%"
        val pct = (likes.toDouble() / views.toDouble()) * 100.0
        return String.format(Locale.US, "%.2f%%", pct)
    }
}
