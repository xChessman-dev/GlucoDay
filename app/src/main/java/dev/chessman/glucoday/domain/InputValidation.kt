package dev.chessman.glucoday.domain

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

object InputValidation {
    private val decimalPattern = Regex("[0-9]+(?:[.,][0-9]+)?")
    private val clockPattern = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")

    fun decimal(raw: String, label: String, min: Double = 0.0, max: Double = Double.MAX_VALUE): Double {
        val text = raw.trim()
        require(decimalPattern.matches(text)) { "$label: введите число, например 5,6" }
        val value = text.replace(',', '.').toDoubleOrNull()
        require(value != null && value.isFinite()) { "$label: некорректное число" }
        return number(value, label, min, max)
    }

    fun optionalDecimal(raw: String, label: String, min: Double = 0.0, max: Double = Double.MAX_VALUE): Double? =
        if (raw.isBlank()) null else decimal(raw, label, min, max)

    fun integer(raw: String, label: String, min: Int = 0, max: Int = Int.MAX_VALUE): Int {
        require(Regex("[0-9]+").matches(raw.trim())) { "$label: введите целое число" }
        val value = raw.trim().toIntOrNull()
        require(value != null && value in min..max) { "$label: допустимо от $min до $max" }
        return value
    }

    fun glucoseMmol(raw: String, unit: GlucoseUnit): Double = glucoseValue(
        GlucoseConversions.toMmol(decimal(raw, "Глюкоза"), unit),
    )

    fun glucoseValue(value: Double): Double {
        require(value.isFinite() && value in 0.1..60.0) {
            "Проверьте значение и единицы: допустимо 0,1–60 ммоль/л (примерно 1,8–1081,1 мг/дл). Значение не сохранено."
        }
        return value
    }

    fun number(value: Double, label: String, min: Double, max: Double): Double {
        require(value.isFinite() && value in min..max) { "$label: допустимо от $min до $max" }
        return value
    }

    fun timestamp(value: Long, now: Long = System.currentTimeMillis()): Long {
        require(value >= 0) { "Дата должна быть не раньше 1 января 1970 года" }
        require(value <= now) { "Запись не может относиться к будущему" }
        return value
    }

    fun uuid(value: String) {
        require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
            "Некорректный идентификатор записи"
        }
    }

    fun profile(value: Profile) {
        text(value.name, "Имя", 120)
        text(value.diabetesType, "Тип диабета", 120)
        value.heightCm?.let { number(it, "Рост", 30.0, 300.0) }
        value.birthYear?.let { require(it in 1900..LocalDate.now().year) { "Проверьте год рождения" } }
        value.glucoseTargetMinMmol?.let(::glucoseValue)
        value.glucoseTargetMaxMmol?.let(::glucoseValue)
        require((value.glucoseTargetMinMmol == null) == (value.glucoseTargetMaxMmol == null)) {
            "Укажите обе границы целевого диапазона или оставьте обе пустыми"
        }
        if (value.glucoseTargetMinMmol != null && value.glucoseTargetMaxMmol != null) {
            require(value.glucoseTargetMinMmol < value.glucoseTargetMaxMmol) { "Нижняя граница должна быть меньше верхней" }
        }
        require(value.stepGoal in 0..100_000) { "Цель шагов: от 0 до 100000; 0 — без цели" }
        require(value.extraTopInsetDp in 0..160) { "Верхний отступ: от 0 до 160" }
    }

    fun glucose(value: GlucoseRecord, now: Long = System.currentTimeMillis()) {
        uuid(value.id)
        glucoseValue(value.valueMmol)
        timestamp(value.timestamp, now)
        text(value.note, "Заметка", 10_000)
    }

    fun meal(value: MealRecord, now: Long = System.currentTimeMillis()) {
        uuid(value.id)
        text(value.title, "Название еды", 300, required = true)
        number(value.carbsGrams, "Углеводы", 0.0, 3_000.0)
        value.caloriesKcal?.let { number(it, "Калории", 0.0, 30_000.0) }
        timestamp(value.timestamp, now)
    }

    fun weight(value: WeightRecord, now: Long = System.currentTimeMillis()) {
        uuid(value.id)
        number(value.kilograms, "Вес", 1.0, 1_000.0)
        timestamp(value.timestamp, now)
    }

    fun medication(value: MedicationSchedule) {
        uuid(value.id)
        text(value.name, "Название лекарства", 200, required = true)
        text(value.dosage, "Дозировка", 300)
        require(clockPattern.matches(value.time)) { "Время лекарства: используйте формат ЧЧ:ММ" }
        LocalTime.parse(value.time)
    }

    fun intake(value: MedicationIntake, now: Long = System.currentTimeMillis()) {
        uuid(value.id)
        uuid(value.scheduleId)
        timestamp(value.timestamp, now)
        val day = runCatching { LocalDate.parse(value.localDate) }.getOrNull()
        require(day != null && day.toString() == value.localDate && day >= LocalDate.of(1970, 1, 1)) {
            "Некорректная дата приёма лекарства"
        }
        require(!day.isAfter(LocalDate.now())) { "Нельзя отметить приём лекарства за будущий день" }
    }

    private fun text(value: String, label: String, max: Int, required: Boolean = false) {
        require(!required || value.isNotBlank()) { "$label: заполните поле" }
        require(value.length <= max) { "$label: не больше $max символов" }
        require('\u0000' !in value) { "$label: недопустимый символ" }
    }
}
