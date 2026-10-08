package dev.chessman.glucoday.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.chessman.glucoday.domain.*
import java.time.*
import java.util.Locale
import kotlinx.coroutines.*

@Composable fun Editor(title: String, close: () -> Unit, save: suspend () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!saving) close() }) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.heightIn(max = 720.dp).imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    TextButton(close, enabled = !saving) { Text("Отмена") }
                    Button({
                        saving = true
                        scope.launch {
                            try { save(); close() }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Не удалось сохранить" }
                            finally { saving = false }
                        }
                    }, enabled = !saving) { Text(if (saving) "Сохраняем…" else "Сохранить") }
                }
            }
        }
    }
}

@Composable fun Input(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = false, multiline: Boolean = false) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = !multiline,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        minLines = if (multiline) 2 else 1, maxLines = if (multiline) 4 else 1)
}

@Composable fun RecordTime(value: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    val current = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    Muted("Время записи: ${dateText(value)}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({
            DatePickerDialog(context, { _, year, month, day ->
                onChange(LocalDate.of(year, month + 1, day).atTime(current.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            }, current.year, current.monthValue - 1, current.dayOfMonth).apply {
                datePicker.minDate = 0L
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }) { Text("Дата") }
        OutlinedButton({
            TimePickerDialog(context, { _, hour, minute -> onChange(current.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli()) }, current.hour, current.minute, true).show()
        }) { Text("Время") }
    }
}

@Composable fun GlucoseEditor(unit: GlucoseUnit, existing: GlucoseRecord?, close: () -> Unit, save: suspend (GlucoseRecord) -> Unit) {
    val originalText = remember(existing?.id, unit) { existing?.let { glucoseText(it.valueMmol, unit) } ?: "" }
    var raw by rememberSaveable(existing?.id, unit) { mutableStateOf(originalText) }
    var context by rememberSaveable { mutableStateOf(existing?.context ?: GlucoseContext.FASTING) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var timestamp by rememberSaveable { mutableLongStateOf(existing?.timestamp ?: System.currentTimeMillis()) }
    Editor(if (existing == null) "Добавить сахар" else "Изменить показание", close, {
        val value = NumericEdits.required(raw, originalText, existing?.valueMmol) { InputValidation.glucoseMmol(it, unit) }
        val record = (existing ?: GlucoseRecord(value)).copy(valueMmol = value, context = context, note = note, timestamp = timestamp)
        InputValidation.glucose(record)
        save(record)
    }) {
        Muted("Введите показание глюкометра. Телефон сам не измеряет сахар.")
        Input("Глюкоза, ${unit.label()}", raw, { raw = it }, numeric = true)
        Choice("Когда измеряли", context, GlucoseContext.entries, { it.label() }, { context = it })
        RecordTime(timestamp) { timestamp = it }
        Input("Заметка — необязательно", note, { note = it }, multiline = true)
    }
}

@Composable fun MealEditor(existing: MealRecord?, close: () -> Unit, save: suspend (MealRecord) -> Unit) {
    val originalCarbs = remember(existing?.id) { existing?.let { number(it.carbsGrams) } ?: "" }
    val originalKcal = remember(existing?.id) { existing?.caloriesKcal?.let { number(it, 0) } ?: "" }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var carbs by rememberSaveable(existing?.id) { mutableStateOf(originalCarbs) }
    var kcal by rememberSaveable(existing?.id) { mutableStateOf(originalKcal) }
    var timestamp by rememberSaveable { mutableLongStateOf(existing?.timestamp ?: System.currentTimeMillis()) }
    Editor(if (existing == null) "Добавить еду" else "Изменить еду", close, {
        val carbValue = NumericEdits.required(carbs, originalCarbs, existing?.carbsGrams) {
            InputValidation.decimal(it, "Углеводы", 0.0, 3000.0)
        }
        val calorieValue = NumericEdits.optional(kcal, originalKcal, existing?.caloriesKcal) {
            InputValidation.optionalDecimal(it, "Калории", 0.0, 30000.0)
        }
        val record = (existing ?: MealRecord(title, carbValue)).copy(
            title = title.trim(), carbsGrams = carbValue, caloriesKcal = calorieValue, timestamp = timestamp,
        )
        InputValidation.meal(record)
        save(record)
    }) {
        Input("Что ели", title, { title = it })
        Input("Углеводы в вашей порции, г", carbs, { carbs = it }, true)
        Input("Ккал — необязательно", kcal, { kcal = it }, true)
        RecordTime(timestamp) { timestamp = it }
        Muted("Можно взять данные с упаковки или добавить блюдо из раздела «Еда».")
    }
}

@Composable fun WeightEditor(existing: WeightRecord?, close: () -> Unit, save: suspend (WeightRecord) -> Unit) {
    val originalText = remember(existing?.id) { existing?.let { number(it.kilograms) } ?: "" }
    var raw by rememberSaveable(existing?.id) { mutableStateOf(originalText) }
    var timestamp by rememberSaveable { mutableLongStateOf(existing?.timestamp ?: System.currentTimeMillis()) }
    Editor("Записать вес", close, {
        val weight = NumericEdits.required(raw, originalText, existing?.kilograms) { InputValidation.decimal(it, "Вес", 1.0, 1000.0) }
        val record = (existing ?: WeightRecord(weight)).copy(kilograms = weight, timestamp = timestamp)
        InputValidation.weight(record)
        save(record)
    }) { Input("Вес, кг", raw, { raw = it }, true); RecordTime(timestamp) { timestamp = it } }
}

@Composable fun MedicationEditor(existing: MedicationSchedule?, close: () -> Unit, save: suspend (MedicationSchedule) -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var dose by rememberSaveable { mutableStateOf(existing?.dosage ?: "") }
    var time by rememberSaveable { mutableStateOf(existing?.time ?: "09:00") }
    var enabled by rememberSaveable { mutableStateOf(existing?.enabled ?: true) }
    Editor(if (existing == null) "Добавить приём" else "Изменить приём", close, {
        val record = (existing ?: MedicationSchedule(name)).copy(name = name.trim(), dosage = dose.trim(), time = time, enabled = enabled)
        InputValidation.medication(record)
        save(record)
    }) {
        Muted("Только ваша назначенная врачом схема. Для второго времени приёма добавьте отдельную запись.")
        Input("Название лекарства", name, { name = it })
        Input("Дозировка / заметка из назначения", dose, { dose = it })
        OutlinedButton({ val parts = time.split(':'); TimePickerDialog(context, { _, h, m -> time = String.format(Locale.ROOT, "%02d:%02d", h, m) }, parts[0].toInt(), parts[1].toInt(), true).show() }, Modifier.fillMaxWidth()) { Text("Каждый день · $time") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Напоминать", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
    }
}

@Composable fun ProfileEditor(profile: Profile, close: () -> Unit, save: suspend (Profile) -> Unit) {
    val originalLow = remember(profile.glucoseUnit) { profile.glucoseTargetMinMmol?.let { glucoseText(it, profile.glucoseUnit) } ?: "" }
    val originalHigh = remember(profile.glucoseUnit) { profile.glucoseTargetMaxMmol?.let { glucoseText(it, profile.glucoseUnit) } ?: "" }
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var height by rememberSaveable { mutableStateOf(profile.heightCm?.toString() ?: "") }
    var year by rememberSaveable { mutableStateOf(profile.birthYear?.toString() ?: "") }
    var type by rememberSaveable { mutableStateOf(profile.diabetesType) }
    var goal by rememberSaveable { mutableStateOf(if (profile.stepGoal == 0) "" else profile.stepGoal.toString()) }
    var low by rememberSaveable(profile.glucoseUnit) { mutableStateOf(originalLow) }
    var high by rememberSaveable(profile.glucoseUnit) { mutableStateOf(originalHigh) }
    Editor("Личные настройки", close, {
        val lowerBound = NumericEdits.optional(low, originalLow, profile.glucoseTargetMinMmol) {
            if (it.isBlank()) null else InputValidation.glucoseMmol(it, profile.glucoseUnit)
        }
        val upperBound = NumericEdits.optional(high, originalHigh, profile.glucoseTargetMaxMmol) {
            if (it.isBlank()) null else InputValidation.glucoseMmol(it, profile.glucoseUnit)
        }
        val updated = profile.copy(name = name.trim(), heightCm = InputValidation.optionalDecimal(height, "Рост", 30.0, 300.0),
            birthYear = if (year.isBlank()) null else InputValidation.integer(year, "Год рождения", 1900, LocalDate.now().year), diabetesType = type.trim(),
            stepGoal = if (goal.isBlank()) 0 else InputValidation.integer(goal, "Цель шагов", 0, 100000),
            glucoseTargetMinMmol = lowerBound, glucoseTargetMaxMmol = upperBound)
        InputValidation.profile(updated)
        save(updated)
    }) {
        Muted("Все поля необязательные. Данные остаются на телефоне.")
        Input("Как к вам обращаться", name, { name = it })
        Input("Рост, см", height, { height = it }, true)
        Input("Год рождения", year, { year = it }, true)
        Input("Тип диабета — если хотите указать", type, { type = it })
        Input("Личная цель шагов на день", goal, { goal = it }, true)
        Muted("Диапазон сахара — только согласованный с врачом. Не задан по умолчанию и не является назначением.")
        Input("Нижняя граница, ${profile.glucoseUnit.label()}", low, { low = it }, true)
        Input("Верхняя граница, ${profile.glucoseUnit.label()}", high, { high = it }, true)
    }
}
