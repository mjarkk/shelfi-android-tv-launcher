package com.mkopenga.tvhomescreen

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal fun subtitleFor(isGame: Boolean, lastUsedAt: Long?, now: Long): String {
    val kind = if (isGame) "Game" else "App"
    if (lastUsedAt == null) return kind
    val verb = if (isGame) "Last played" else "Last opened"
    return "$kind · $verb ${relativeTime(lastUsedAt, now)}"
}

internal fun relativeTime(then: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val minutes = (now - then) / 60_000
    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(then).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
    )
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${count(minutes, "minute")} ago"
        days <= 0 -> "today"
        days == 1L -> "yesterday"
        days < 7 -> "$days days ago"
        days < 30 -> "${count(days / 7, "week")} ago"
        days < 365 -> "${count(days / 30, "month")} ago"
        else -> "${count(days / 365, "year")} ago"
    }
}

private fun count(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
