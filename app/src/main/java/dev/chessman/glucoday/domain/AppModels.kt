package dev.chessman.glucoday.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class GlucoseUnit { MMOL_L, MG_DL }
enum class GlucoseContext { FASTING, BEFORE_MEAL, AFTER_MEAL, BEDTIME, OTHER }
enum class AppTheme { SYSTEM, LIGHT, DARK }

data class Profile(
    val name: String = "",
    val heightCm: Double? = null,
    val birthYear: Int? = null,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val glucoseTargetMinMmol: Double? = null,
    val glucoseTargetMaxMmol: Double? = null,
    val stepGoal: Int = 0,
    val theme: AppTheme = AppTheme.DARK,
    val extraTopInsetDp: Int = 0,
    val diabetesType: String = "",
)

data class GlucoseRecord(
    val valueMmol: Double,
    val context: GlucoseContext = GlucoseContext.OTHER,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
)

data class MealRecord(
    val title: String,
    val carbsGrams: Double,
    val caloriesKcal: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
)

data class WeightRecord(
    val kilograms: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
)

data class MedicationSchedule(
    val name: String,
    val dosage: String = "",
    val time: String = "09:00",
    val enabled: Boolean = true,
    val id: String = UUID.randomUUID().toString(),
)

data class MedicationIntake(
    val scheduleId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val localDate: String = LocalDate.now().toString(),
    val id: String = UUID.randomUUID().toString(),
)

data class AppSnapshot(
    val profile: Profile = Profile(),
    val glucose: List<GlucoseRecord> = emptyList(),
    val meals: List<MealRecord> = emptyList(),
    val weights: List<WeightRecord> = emptyList(),
    val medications: List<MedicationSchedule> = emptyList(),
    val intakes: List<MedicationIntake> = emptyList(),
    val loaded: Boolean = false,
) {
    fun chronological(): AppSnapshot = copy(
        glucose = glucose.sortedWith(compareByDescending<GlucoseRecord> { it.timestamp }.thenBy { it.id }),
        meals = meals.sortedWith(compareByDescending<MealRecord> { it.timestamp }.thenBy { it.id }),
        weights = weights.sortedWith(compareByDescending<WeightRecord> { it.timestamp }.thenBy { it.id }),
        medications = medications.sortedWith(compareBy<MedicationSchedule> { it.time }.thenBy { it.name }.thenBy { it.id }),
        intakes = intakes.sortedWith(compareByDescending<MedicationIntake> { it.timestamp }.thenBy { it.id }),
    )
}

object GlucoseConversions {
    // The same factor is used both ways; canonical storage never rounds values.
    const val MG_DL_PER_MMOL_L = 18.0182
    fun toMmol(value: Double, unit: GlucoseUnit): Double = when (unit) {
        GlucoseUnit.MMOL_L -> value
        GlucoseUnit.MG_DL -> value / MG_DL_PER_MMOL_L
    }
    fun fromMmol(value: Double, unit: GlucoseUnit): Double = when (unit) {
        GlucoseUnit.MMOL_L -> value
        GlucoseUnit.MG_DL -> value * MG_DL_PER_MMOL_L
    }
}

object RecordDates {
    fun localDate(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    /** End is exclusive: this also works for 23- and 25-hour daylight-saving days. */
    fun dayBounds(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LongRange =
        day.atStartOfDay(zone).toInstant().toEpochMilli() until
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
