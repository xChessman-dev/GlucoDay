package dev.chessman.glucoday.platform

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDayTimesTest {
    @Test fun `seven days include today and have contiguous windows across spring DST`() {
        val windows = lastSevenDayWindows(Instant.parse("2026-03-10T12:00:00Z"), ZoneId.of("America/New_York"))
        assertEquals(7, windows.size)
        assertEquals("2026-03-04", windows.first().date.toString())
        assertEquals("2026-03-10", windows.last().date.toString())
        assertEquals(Instant.parse("2026-03-10T12:00:00Z"), windows.last().end)
        windows.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        val springDay = windows.first { it.date.toString() == "2026-03-08" }
        assertEquals(23L, Duration.between(springDay.start, springDay.end).toHours())
    }

    @Test fun `fall DST day contains twenty five hours`() {
        val windows = lastSevenDayWindows(Instant.parse("2026-11-03T12:00:00Z"), ZoneId.of("America/New_York"))
        val fallDay = windows.first { it.date.toString() == "2026-11-01" }
        assertEquals(25L, Duration.between(fallDay.start, fallDay.end).toHours())
    }

    @Test fun `local date is used rather than UTC date`() {
        val windows = lastSevenDayWindows(Instant.parse("2026-10-08T20:30:00Z"), ZoneId.of("Asia/Yekaterinburg"))
        assertEquals("2026-10-09", windows.last().date.toString())
        assertEquals(Instant.parse("2026-10-08T19:00:00Z"), windows.last().start)
    }

    @Test fun `reminder remains at local time when the following day is shorter`() {
        val zone = ZoneId.of("America/New_York")
        val next = nextReminderTime(Instant.parse("2026-03-07T14:00:00Z"), zone, 8, 0)
        assertEquals(Instant.parse("2026-03-08T12:00:00Z"), next)
    }

    @Test fun `nonexistent DST reminder time is shifted through the gap`() {
        val next = nextReminderTime(Instant.parse("2026-03-08T06:00:00Z"), ZoneId.of("America/New_York"), 2, 30)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), next)
    }

    @Test fun `overlapping reminder time is delivered only once per local day`() {
        val next = nextReminderTime(Instant.parse("2026-11-01T05:45:00Z"), ZoneId.of("America/New_York"), 1, 30)
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), next)
    }

    @Test fun `exactly due reminder reschedules for tomorrow`() {
        val now = Instant.parse("2026-12-31T18:00:00Z")
        val next = nextReminderTime(now, ZoneId.of("UTC"), 18, 0)
        assertEquals(Instant.parse("2027-01-01T18:00:00Z"), next)
        assertTrue(next > now)
    }
}
