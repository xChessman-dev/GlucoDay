package dev.chessman.glucoday.data

import dev.chessman.glucoday.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Versioned, UTF-8 JSON. All canonical numeric values are written without display rounding. */
object BackupCodec {
    fun encode(payload: BackupPayload): String {
        BackupValidation.validate(payload)
        val s = payload.snapshot
        val result = obj(
            "format" to "GlucoDay", "version" to payload.version, "exportedAt" to payload.exportedAt,
            "profile" to s.profile.let { p -> obj(
                "name" to p.name, "heightCm" to p.heightCm, "birthYear" to p.birthYear,
                "glucoseUnit" to p.glucoseUnit.name, "glucoseTargetMinMmol" to p.glucoseTargetMinMmol,
                "glucoseTargetMaxMmol" to p.glucoseTargetMaxMmol, "stepGoal" to p.stepGoal,
                "theme" to p.theme.name, "extraTopInsetDp" to p.extraTopInsetDp, "diabetesType" to p.diabetesType,
            ) },
            "glucose" to array(s.glucose.map { obj("id" to it.id, "timestamp" to it.timestamp,
                "valueMmol" to it.valueMmol, "context" to it.context.name, "note" to it.note) }),
            "meals" to array(s.meals.map { obj("id" to it.id, "timestamp" to it.timestamp,
                "title" to it.title, "carbsGrams" to it.carbsGrams, "caloriesKcal" to it.caloriesKcal) }),
            "weights" to array(s.weights.map { obj("id" to it.id, "timestamp" to it.timestamp,
                "kilograms" to it.kilograms) }),
            "medications" to array(s.medications.map { obj("id" to it.id, "name" to it.name,
                "dosage" to it.dosage, "time" to it.time, "enabled" to it.enabled) }),
            "intakes" to array(s.intakes.map { obj("id" to it.id, "scheduleId" to it.scheduleId,
                "timestamp" to it.timestamp, "localDate" to it.localDate) }),
        ).toString(2)
        checkSize(result)
        return result
    }

    fun decode(json: String): BackupPayload {
        checkSize(json)
        StrictJson.validate(json.removePrefix("\uFEFF"))
        try {
            val tokens = JSONTokener(json.removePrefix("\uFEFF"))
            val root = tokens.nextValue() as? JSONObject
                ?: throw IllegalArgumentException("Резервная копия должна содержать JSON-объект")
            require(tokens.nextClean() == '\u0000') { "Лишние данные после резервной копии" }
            require(root.string("format") == "GlucoDay") { "Это не резервная копия GlucoDay" }
            val version = root.int("version")
            require(version == BackupValidation.VERSION) { "Неподдерживаемая версия резервной копии: $version" }
            val glucose = root.array("glucose")
            val meals = root.array("meals")
            val weights = root.array("weights")
            val medications = root.array("medications")
            val intakes = root.array("intakes")
            val count = glucose.length().toLong() + meals.length() + weights.length() + medications.length() + intakes.length()
            require(count <= BackupValidation.MAX_RECORDS) { "Слишком много записей в резервной копии" }
            val p = root.getJSONObject("profile")
            val snapshot = AppSnapshot(
                profile = Profile(
                    name = p.string("name"), heightCm = p.nullableDouble("heightCm"), birthYear = p.nullableInt("birthYear"),
                    glucoseUnit = enumValueOf(p.string("glucoseUnit")),
                    glucoseTargetMinMmol = p.nullableDouble("glucoseTargetMinMmol"),
                    glucoseTargetMaxMmol = p.nullableDouble("glucoseTargetMaxMmol"),
                    stepGoal = p.int("stepGoal"), theme = enumValueOf(p.string("theme")),
                    extraTopInsetDp = p.int("extraTopInsetDp"), diabetesType = p.string("diabetesType"),
                ),
                glucose = glucose.objects().map { GlucoseRecord(
                    valueMmol = it.double("valueMmol"), context = enumValueOf(it.string("context")),
                    note = it.string("note"), timestamp = it.long("timestamp"), id = it.string("id"),
                ) },
                meals = meals.objects().map { MealRecord(
                    title = it.string("title"), carbsGrams = it.double("carbsGrams"),
                    caloriesKcal = it.nullableDouble("caloriesKcal"), timestamp = it.long("timestamp"), id = it.string("id"),
                ) },
                weights = weights.objects().map { WeightRecord(it.double("kilograms"), it.long("timestamp"), it.string("id")) },
                medications = medications.objects().map { MedicationSchedule(
                    it.string("name"), it.string("dosage"), it.string("time"), it.boolean("enabled"), it.string("id"),
                ) },
                intakes = intakes.objects().map { MedicationIntake(
                    it.string("scheduleId"), it.long("timestamp"), it.string("localDate"), it.string("id"),
                ) }, loaded = true,
            )
            return BackupPayload(version, root.long("exportedAt"), snapshot).also { BackupValidation.validate(it) }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Не удалось прочитать резервную копию: повреждённый файл или неверные поля", error)
        }
    }

    private fun checkSize(json: String) {
        require(json.length <= BackupValidation.MAX_BYTES && json.toByteArray(Charsets.UTF_8).size <= BackupValidation.MAX_BYTES) {
            "Резервная копия превышает 10 МБ"
        }
        require(json.isNotBlank()) { "Файл резервной копии пуст" }
    }

    private fun obj(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }
    private fun array(values: List<JSONObject>): JSONArray = JSONArray().apply { values.forEach { put(it) } }
    private fun JSONObject.array(key: String): JSONArray = get(key) as? JSONArray
        ?: throw IllegalArgumentException("Поле $key должно содержать массив")
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { index ->
        get(index) as? JSONObject ?: throw IllegalArgumentException("Некорректная запись в резервной копии")
    }
    private fun JSONObject.string(key: String): String = get(key) as? String
        ?: throw IllegalArgumentException("Поле $key должно содержать текст")
    private fun JSONObject.boolean(key: String): Boolean = get(key) as? Boolean
        ?: throw IllegalArgumentException("Некорректное логическое поле $key")
    private fun JSONObject.double(key: String): Double {
        val value = get(key) as? Number ?: throw IllegalArgumentException("Поле $key должно содержать число")
        return value.toDouble().also { require(it.isFinite()) { "Некорректное число в поле $key" } }
    }
    private fun JSONObject.long(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "Поле $key должно содержать целое число" }
        return (value as Number).toLong()
    }
    private fun JSONObject.int(key: String): Int = long(key).let {
        require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "Число в поле $key слишком большое" }
        it.toInt()
    }
    private fun JSONObject.nullableDouble(key: String): Double? = if (get(key) === JSONObject.NULL) null else double(key)
    private fun JSONObject.nullableInt(key: String): Int? = if (get(key) === JSONObject.NULL) null else int(key)
}
