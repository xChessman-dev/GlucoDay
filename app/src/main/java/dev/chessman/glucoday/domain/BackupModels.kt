package dev.chessman.glucoday.domain

data class BackupPreview(
    val glucoseCount: Int,
    val mealCount: Int,
    val weightCount: Int,
    val medicationCount: Int,
    val intakeCount: Int,
    val exportedAt: Long,
) {
    val totalRecords: Int get() = glucoseCount + mealCount + weightCount + medicationCount + intakeCount
}

data class BackupPayload(val version: Int, val exportedAt: Long, val snapshot: AppSnapshot)

object BackupValidation {
    const val VERSION = 1
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_RECORDS = 50_000

    fun validate(payload: BackupPayload, now: Long = System.currentTimeMillis()): BackupPreview {
        require(payload.version == VERSION) { "Неподдерживаемая версия резервной копии: ${payload.version}" }
        require(payload.exportedAt >= 0) { "Некорректная дата резервной копии" }
        val s = payload.snapshot
        val count = s.glucose.size.toLong() + s.meals.size + s.weights.size + s.medications.size + s.intakes.size
        require(count <= MAX_RECORDS) { "Слишком много записей в резервной копии (максимум $MAX_RECORDS)" }
        InputValidation.profile(s.profile)
        s.glucose.forEach { InputValidation.glucose(it, now) }
        s.meals.forEach { InputValidation.meal(it, now) }
        s.weights.forEach { InputValidation.weight(it, now) }
        s.medications.forEach(InputValidation::medication)
        s.intakes.forEach { InputValidation.intake(it, now) }
        unique(s.glucose.map { it.id })
        unique(s.meals.map { it.id })
        unique(s.weights.map { it.id })
        unique(s.medications.map { it.id })
        unique(s.intakes.map { it.id })
        val scheduleIds = s.medications.mapTo(hashSetOf()) { it.id }
        require(s.intakes.all { it.scheduleId in scheduleIds }) { "Приём лекарства ссылается на отсутствующее расписание" }
        require(s.intakes.map { it.scheduleId to it.localDate }.distinct().size == s.intakes.size) {
            "Повторные отметки приёма одного лекарства за один день"
        }
        return BackupPreview(s.glucose.size, s.meals.size, s.weights.size, s.medications.size, s.intakes.size, payload.exportedAt)
    }

    private fun unique(ids: List<String>) {
        require(ids.distinct().size == ids.size) { "Повторяющиеся идентификаторы в резервной копии" }
    }
}
