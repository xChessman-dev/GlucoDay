package dev.chessman.glucoday.platform

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class DayWindow(val date: LocalDate, val start: Instant, val end: Instant)

/** Calendar days in the selected zone, including 23/25 hour daylight-saving days. */
internal fun lastSevenDayWindows(now: Instant, zone: ZoneId): List<DayWindow> {
    val today = now.atZone(zone).toLocalDate()
    return (6 downTo 0).map { daysAgo ->
        val day = today.minusDays(daysAgo.toLong())
        DayWindow(
            date = day,
            start = day.atStartOfDay(zone).toInstant(),
            end = if (daysAgo == 0) now else day.plusDays(1).atStartOfDay(zone).toInstant(),
        )
    }
}

/**
 * One reminder per local calendar day. A nonexistent DST time moves forward through the gap;
 * an overlapping time uses its first occurrence. Tomorrow is a calendar day, never +24 hours.
 */
internal fun nextReminderTime(now: Instant, zone: ZoneId, hour: Int, minute: Int): Instant {
    val localTime = LocalTime.of(hour, minute)
    val today = now.atZone(zone).toLocalDate()
    val candidate = today.atTime(localTime).atZone(zone).toInstant()
    return if (candidate > now) candidate else today.plusDays(1).atTime(localTime).atZone(zone).toInstant()
}
