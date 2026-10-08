package dev.chessman.glucoday.domain

import dev.chessman.glucoday.data.BackupCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val scheduleId = "00000000-0000-4000-8000-000000000001"
    private val intakeId = "00000000-0000-4000-8000-000000000002"
    private val glucoseId = "00000000-0000-4000-8000-000000000003"
    private val mealId = "00000000-0000-4000-8000-000000000004"
    private val weightId = "00000000-0000-4000-8000-000000000005"

    @Test fun roundTripPreservesEveryStoredFieldAndDoublePrecision() {
        val payload = example()
        val decoded = BackupCodec.decode(BackupCodec.encode(payload))
        assertEquals(payload, decoded)
        assertEquals(5.1234567890123, decoded.snapshot.glucose.single().valueMmol, 0.0)
        assertEquals("2020-01-02", decoded.snapshot.intakes.single().localDate)
    }

    @Test fun nullValuesRemainNullAndEmptyAppHasNoPatientData() {
        val payload = BackupPayload(1, 1000, AppSnapshot(loaded = true))
        val decoded = BackupCodec.decode(BackupCodec.encode(payload))
        assertEquals(payload, decoded)
        assertNull(decoded.snapshot.profile.heightCm)
        assertNull(decoded.snapshot.profile.glucoseTargetMinMmol)
        assertEquals("", decoded.snapshot.profile.name)
        assertTrue(decoded.snapshot.glucose.isEmpty())
    }

    @Test fun rejectsUnknownVersionWrongNumericTypeInvalidEnumAndMissingField() {
        val original = BackupCodec.encode(example())
        rejects { BackupCodec.decode(JSONObject(original).put("version", 99).toString()) }
        rejects { BackupCodec.decode(JSONObject(original).put("exportedAt", "1000").toString()) }
        val enumCase = JSONObject(original)
        enumCase.getJSONObject("profile").put("glucoseUnit", "guess")
        rejects { BackupCodec.decode(enumCase.toString()) }
        val missing = JSONObject(original)
        missing.getJSONObject("profile").remove("stepGoal")
        rejects { BackupCodec.decode(missing.toString()) }
        val wrongBoolean = JSONObject(original)
        wrongBoolean.getJSONArray("medications").getJSONObject(0).put("enabled", "true")
        rejects { BackupCodec.decode(wrongBoolean.toString()) }
    }

    @Test fun rejectsOversizeDeeplyNestedAndTrailingGarbage() {
        rejects { BackupCodec.decode(" ".repeat(BackupValidation.MAX_BYTES + 1)) }
        rejects { BackupCodec.decode("[".repeat(20) + "0" + "]".repeat(20)) }
        rejects { BackupCodec.decode(BackupCodec.encode(example()) + "extra") }
        rejects { BackupCodec.decode("not JSON") }
    }

    @Test fun preservesEscapedJsonTextAndAcceptsUtf8Bom() {
        val payload = example()
        val encoded = BackupCodec.encode(payload)
        assertEquals(payload, BackupCodec.decode("\uFEFF$encoded"))
        assertEquals("Заметка: \"еда\"\nСтрока \\ путь", BackupCodec.decode(encoded).snapshot.glucose.single().note)
    }

    @Test fun jsonGrammarRejectsRepeatedKeysAndLenientAndroidSyntax() {
        listOf(
            "{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":2}",
            "{'a':1}", "{a:1}", "{\"a\":01}", "{\"a\":+1}",
            "{\"a\":1,}", "[1,]", "{\"a\":NaN}", "{\"a\":1/*comment*/}",
            "{\"a\":\"unescaped\nnewline\"}",
        ).forEach { rejects { StrictJson.validate(it) } }
        StrictJson.validate("{\"a\": [true, false, null, -1.25e+3, \"text\\nline\"]}")
    }

    private fun example() = BackupPayload(1, 1_600_000_000_000, AppSnapshot(
        profile = Profile(name = "", heightCm = 173.25, birthYear = 1990, glucoseUnit = GlucoseUnit.MG_DL,
            glucoseTargetMinMmol = 4.25, glucoseTargetMaxMmol = 8.75, stepGoal = 6500,
            theme = AppTheme.SYSTEM, extraTopInsetDp = 24, diabetesType = "Указано пользователем"),
        glucose = listOf(GlucoseRecord(5.1234567890123, GlucoseContext.AFTER_MEAL,
            "Заметка: \"еда\"\nСтрока \\ путь", 1_577_923_200_001, glucoseId)),
        meals = listOf(MealRecord("Блюдо, \"тест\"", 12.345678901, null, 1_577_923_200_002, mealId)),
        weights = listOf(WeightRecord(70.123456789, 1_577_923_200_003, weightId)),
        medications = listOf(MedicationSchedule("Запись лекарства", "Текст пользователя", "08:35", false, scheduleId)),
        intakes = listOf(MedicationIntake(scheduleId, 1_577_923_200_004, "2020-01-02", intakeId)), loaded = true,
    ))

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected an IllegalArgumentException") } catch (_: IllegalArgumentException) { }
    }
}
