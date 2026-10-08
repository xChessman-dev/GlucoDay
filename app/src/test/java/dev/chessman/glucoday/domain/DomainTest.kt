package dev.chessman.glucoday.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private val firstId = "00000000-0000-4000-8000-000000000001"
    private val secondId = "00000000-0000-4000-8000-000000000002"
    private val thirdId = "00000000-0000-4000-8000-000000000003"

    @Test fun glucoseConversionRoundTripsWithoutStorageRounding() {
        val original = 5.678901234
        val converted = GlucoseConversions.fromMmol(original, GlucoseUnit.MG_DL)
        assertEquals(original, GlucoseConversions.toMmol(converted, GlucoseUnit.MG_DL), 0.0000000001)
        assertEquals(90.091, GlucoseConversions.fromMmol(5.0, GlucoseUnit.MG_DL), 0.000001)
        assertEquals(original, GlucoseConversions.toMmol(original, GlucoseUnit.MMOL_L), 0.0)
    }

    @Test fun acceptsCommaDecimalAndConvertsUserUnit() {
        assertEquals(5.6, InputValidation.glucoseMmol(" 5,6 ", GlucoseUnit.MMOL_L), 0.0)
        assertEquals(5.0, InputValidation.glucoseMmol("90.091", GlucoseUnit.MG_DL), 0.000001)
        assertNull(InputValidation.optionalDecimal("  ", "Вес"))
        assertEquals(0.0, InputValidation.decimal("0", "Углеводы"), 0.0)
    }

    @Test fun rejectsNonFiniteNegativeAndAmbiguousNumericInput() {
        listOf("NaN", "Infinity", "-1", "1,2.3", "1e3", "1 000", "", ".4", "4,", "+5").forEach { raw ->
            rejects { InputValidation.decimal(raw, "Число") }
        }
        rejects { InputValidation.glucoseValue(Double.NaN) }
        rejects { InputValidation.glucoseValue(Double.POSITIVE_INFINITY) }
        rejects { InputValidation.weight(WeightRecord(-2.0)) }
        rejects { InputValidation.timestamp(1001, now = 1000) }
        rejects { InputValidation.timestamp(-1, now = 1000) }
    }

    @Test fun glucoseRangeHasExplicitInclusiveBoundaries() {
        assertEquals(0.1, InputValidation.glucoseValue(0.1), 0.0)
        assertEquals(60.0, InputValidation.glucoseValue(60.0), 0.0)
        rejects { InputValidation.glucoseValue(0.09) }
        rejects { InputValidation.glucoseValue(60.1) }
    }

    @Test fun profileStartsWithoutMedicalTargets() {
        val profile = Profile()
        assertNull(profile.glucoseTargetMinMmol)
        assertNull(profile.glucoseTargetMaxMmol)
        assertEquals(0, profile.stepGoal)
        InputValidation.profile(profile)
        rejects { InputValidation.profile(profile.copy(glucoseTargetMinMmol = 4.0)) }
        rejects { InputValidation.profile(profile.copy(glucoseTargetMinMmol = 8.0, glucoseTargetMaxMmol = 4.0)) }
        rejects { InputValidation.profile(profile.copy(heightCm = Double.NaN)) }
    }

    @Test fun snapshotsAreNewestFirstWithStableTieOrder() {
        val snapshot = AppSnapshot(glucose = listOf(
            GlucoseRecord(5.0, timestamp = 100, id = secondId),
            GlucoseRecord(5.0, timestamp = 300, id = thirdId),
            GlucoseRecord(5.0, timestamp = 100, id = firstId),
        )).chronological()
        assertEquals(listOf(thirdId, firstId, secondId), snapshot.glucose.map { it.id })
    }

    @Test fun dayBoundsRespectTimeZoneAndMidnight() {
        val zone = ZoneId.of("Asia/Yekaterinburg")
        val day = LocalDate.of(2026, 10, 8)
        val bounds = RecordDates.dayBounds(day, zone)
        val midnight = Instant.parse("2026-10-07T19:00:00Z").toEpochMilli()
        assertEquals(midnight, bounds.first)
        assertTrue(midnight in bounds)
        assertFalse(midnight - 1 in bounds)
        assertFalse(midnight + 86_400_000 in bounds)
        assertEquals(day, RecordDates.localDate(midnight, zone))
    }

    @Test fun daylightSavingDaysDoNotAssumeTwentyFourHours() {
        val zone = ZoneId.of("Europe/Berlin")
        val spring = RecordDates.dayBounds(LocalDate.of(2026, 3, 29), zone)
        val autumn = RecordDates.dayBounds(LocalDate.of(2026, 10, 25), zone)
        assertEquals(23 * 3_600_000L, spring.last - spring.first + 1)
        assertEquals(25 * 3_600_000L, autumn.last - autumn.first + 1)
    }

    @Test fun backupRejectsDuplicateIdsFutureRecordsAndUnknownVersions() {
        val record = GlucoseRecord(5.0, timestamp = 100, id = firstId)
        val payload = BackupPayload(1, 200, AppSnapshot(glucose = listOf(record)))
        assertEquals(1, BackupValidation.validate(payload, now = 1000).glucoseCount)
        rejects { BackupValidation.validate(payload.copy(version = 2)) }
        rejects { BackupValidation.validate(payload.copy(snapshot = AppSnapshot(glucose = listOf(record, record)))) }
        rejects { BackupValidation.validate(payload.copy(snapshot = AppSnapshot(glucose = listOf(record.copy(timestamp = 1001)))), now = 1000) }
    }

    @Test fun backupRejectsOrphanedAndDuplicateMedicationIntakes() {
        val medicine = MedicationSchedule("Лекарство", id = firstId)
        val intake = MedicationIntake(firstId, timestamp = 1000, localDate = "2020-01-02", id = secondId)
        val valid = BackupPayload(1, 2000, AppSnapshot(medications = listOf(medicine), intakes = listOf(intake)))
        assertEquals(1, BackupValidation.validate(valid).intakeCount)
        rejects { BackupValidation.validate(valid.copy(snapshot = valid.snapshot.copy(medications = emptyList()))) }
        rejects { BackupValidation.validate(valid.copy(snapshot = valid.snapshot.copy(intakes = listOf(intake, intake.copy(id = thirdId))))) }
    }

    @Test fun medicationTimeRequiresRealTwentyFourHourClock() {
        InputValidation.medication(MedicationSchedule("Лекарство", time = "00:00"))
        InputValidation.medication(MedicationSchedule("Лекарство", time = "23:59"))
        listOf("24:00", "12:60", "9:00", "-1:00", "09:00:00").forEach { time ->
            rejects { InputValidation.medication(MedicationSchedule("Лекарство", time = time)) }
        }
    }

    @Test fun csvEscapesQuotesNewlinesAndFormulaInjection() {
        assertEquals("\"hello, \"\"world\"\"\"", CsvExport.cell("hello, \"world\""))
        assertEquals("\"line\none\"", CsvExport.cell("line\none"))
        listOf("=SUM(A1)", "+123", "-1+2", "@SUM(A1)", "   =1", "\t=1", "\uFEFF=1").forEach {
            assertTrue(CsvExport.cell(it).startsWith("\"'"))
        }
        val csv = CsvExport.encode(AppSnapshot(glucose = listOf(GlucoseRecord(5.123456789, timestamp = 0, id = firstId))))
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("5.123456789"))
        assertTrue(csv.contains("1970-01-01T00:00:00Z"))
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected an IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
