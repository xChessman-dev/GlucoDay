package dev.chessman.glucoday.domain

import java.time.Instant

/** CSV is for viewing; the JSON backup is the restore format. UTF-8 BOM helps spreadsheet apps. */
object CsvExport {
    private val header = listOf("type", "id", "timestamp_utc", "local_date", "name", "value", "unit",
        "context", "carbs_g", "calories_kcal", "dosage", "schedule_time", "enabled", "medication_id", "note")

    fun encode(snapshot: AppSnapshot): String = buildString {
        append('\uFEFF')
        append(row(header))
        val s = snapshot.chronological()
        s.glucose.forEach { append(row(listOf("glucose", it.id, iso(it.timestamp), "", "", it.valueMmol,
            "mmol/L", it.context.name, "", "", "", "", "", "", it.note))) }
        s.meals.forEach { append(row(listOf("meal", it.id, iso(it.timestamp), "", it.title, "", "", "",
            it.carbsGrams, it.caloriesKcal ?: "", "", "", "", "", ""))) }
        s.weights.forEach { append(row(listOf("weight", it.id, iso(it.timestamp), "", "", it.kilograms,
            "kg", "", "", "", "", "", "", "", ""))) }
        s.medications.forEach { append(row(listOf("medication", it.id, "", "", it.name, "", "", "", "", "",
            it.dosage, it.time, it.enabled, "", ""))) }
        val schedules = s.medications.associateBy { it.id }
        s.intakes.forEach {
            val medication = schedules[it.scheduleId]
            append(row(listOf("intake", it.id, iso(it.timestamp), it.localDate, medication?.name ?: "",
                "", "", "", "", "", medication?.dosage ?: "", "", "", it.scheduleId, "")))
        }
    }

    private fun iso(value: Long): String = Instant.ofEpochMilli(value).toString()
    private fun row(values: List<Any>): String = values.joinToString(",") { cell(it.toString()) } + "\r\n"

    /** Quote every cell and neutralize spreadsheet formula prefixes, including whitespace obfuscation. */
    fun cell(value: String): String {
        val significant = value.dropWhile { it.isWhitespace() || it.code < 32 || it == '\uFEFF' }.firstOrNull()
        val safe = if (significant in listOf('=', '+', '-', '@') || value.firstOrNull() in listOf('\t', '\r', '\n')) {
            "'$value"
        } else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
