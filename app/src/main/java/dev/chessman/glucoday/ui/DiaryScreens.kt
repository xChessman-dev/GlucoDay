package dev.chessman.glucoday.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chessman.glucoday.domain.*
import dev.chessman.glucoday.platform.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun TodayScreen(state: AppSnapshot, steps: StepsSnapshot?, addGlucose: () -> Unit, addMeal: () -> Unit, addWeight: () -> Unit, toggle: (String, Boolean) -> Unit, openActivity: () -> Unit, openProfile: () -> Unit) {
    val today = LocalDate.now()
    val last = state.glucose.firstOrNull()
    val unit = state.profile.glucoseUnit
    val todayMeals = state.meals.filter { dateDay(it.timestamp) == today }
    ScreenList {
        item { Heading(if (state.profile.name.isBlank()) "Ваш день. Спокойнее." else "Здравствуйте, ${state.profile.name}", today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))).replaceFirstChar { it.uppercase() }) }
        item {
            Panel(highlighted = true) {
                SectionLabel("Последнее измерение", Icons.Outlined.WaterDrop)
                if (last == null) {
                    Text("Начнём с одного числа", style = MaterialTheme.typography.headlineMedium)
                    Text("Запишите показание глюкометра — оно останется в вашем дневнике.")
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(glucoseText(last.valueMmol, unit), style = MaterialTheme.typography.displayMedium)
                        Text(unit.label(), Modifier.padding(bottom = 8.dp))
                    }
                    Text("${last.context.label()} · ${dateText(last.timestamp)}")
                    val low = state.profile.glucoseTargetMinMmol
                    val high = state.profile.glucoseTargetMaxMmol
                    if (low != null && high != null) Text(when {
                        last.valueMmol < low -> "Ниже вашего заданного диапазона"
                        last.valueMmol > high -> "Выше вашего заданного диапазона"
                        else -> "В вашем заданном диапазоне"
                    }, style = MaterialTheme.typography.bodyMedium)
                }
                Button(addGlucose, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить сахар") }
            }
        }
        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    SectionLabel("Шаги")
                    Text(steps?.today?.count?.toString() ?: "—", style = MaterialTheme.typography.headlineMedium)
                    Muted(if (steps?.today?.count == null) "Нет данных" else "Сегодня")
                    TextButton(openActivity) { Text("Активность") }
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    SectionLabel("Еда")
                    Text(if (todayMeals.isEmpty()) "—" else "${number(todayMeals.sumOf { it.carbsGrams }, 0)} г", style = MaterialTheme.typography.headlineMedium)
                    Muted("Углеводы, г")
                    TextButton(addMeal) { Text("Добавить") }
                }
            }
        }
        item {
            Panel {
                SectionLabel("Таблетки сегодня", Icons.Outlined.Medication)
                if (state.medications.isEmpty()) {
                    Muted("Добавьте назначенные лекарства и время приёма. Приложение не меняет вашу схему лечения.")
                    ActionButton("Настроить", Icons.Outlined.Schedule, openProfile)
                } else state.medications.forEach { med ->
                    val taken = state.intakes.any { it.scheduleId == med.id && it.localDate == today.toString() }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(med.name, style = MaterialTheme.typography.titleMedium); Muted("${med.time} · ${if (taken) "Приём отмечен" else "Не отмечен"}"); if (med.dosage.isNotBlank()) Muted(med.dosage) }
                        Checkbox(taken, { toggle(med.id, it) }, Modifier.semantics { contentDescription = "Отметить приём ${med.name} в ${med.time}" })
                    }
                }
            }
        }
        item { Panel { SectionLabel("Вес", Icons.Outlined.MonitorWeight); Text(state.weights.firstOrNull()?.let { "${number(it.kilograms)} кг · ${dateText(it.timestamp)}" } ?: "Пока нет записей"); ActionButton("Записать вес", onClick = addWeight) } }
        item { Muted("Это личный дневник, не медицинское заключение. При плохом самочувствии не полагайтесь только на цифры в приложении — обратитесь за медицинской помощью.") }
    }
}

private data class DiaryRow(val id: String, val timestamp: Long, val title: String, val subtitle: String, val type: Int, val value: Any)

@Composable fun DiaryScreen(state: AppSnapshot, editGlucose: (GlucoseRecord) -> Unit, editMeal: (MealRecord) -> Unit, editWeight: (WeightRecord) -> Unit, delete: (Int, String) -> Unit, addGlucose: () -> Unit, addMeal: () -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var range by rememberSaveable { mutableIntStateOf(7) }
    val cutoff = LocalDate.now().minusDays(range.toLong() - 1)
    val glucose = state.glucose.filter { dateDay(it.timestamp) >= cutoff }
    val rows = buildList {
        if (filter == 0 || filter == 1) addAll(glucose.map { DiaryRow(it.id, it.timestamp, "${glucoseText(it.valueMmol, state.profile.glucoseUnit)} ${state.profile.glucoseUnit.label()}", "${it.context.label()}${if (it.note.isNotBlank()) " · ${it.note}" else ""}", 1, it) })
        if (filter == 0 || filter == 2) addAll(state.meals.filter { dateDay(it.timestamp) >= cutoff }.map { DiaryRow(it.id, it.timestamp, it.title, "${number(it.carbsGrams)} г углеводов${it.caloriesKcal?.let { kcal -> " · ${number(kcal, 0)} ккал" } ?: ""}", 2, it) })
        if (filter == 0 || filter == 3) addAll(state.weights.filter { dateDay(it.timestamp) >= cutoff }.map { DiaryRow(it.id, it.timestamp, "${number(it.kilograms)} кг", "Запись веса", 3, it) })
    }.sortedByDescending { it.timestamp }
    ScreenList {
        item { Heading("Дневник", "Сахар, еда и вес — рядом, с точным временем.") }
        item { Choice("Период", range, listOf(7, 30, 90, 3650), { if (it == 3650) "Вся история (до 10 лет)" else "$it дней" }, { range = it }) }
        item { Panel { SectionLabel("Измерения сахара"); GlucosePlot(glucose, state.profile); Muted("Каждая точка — ручное измерение. Между точками сахар неизвестен; среднее по записям не равно среднему за сутки.") } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Всё", "Сахар", "Еда", "Вес").forEachIndexed { index, label -> FilterChip(filter == index, { filter = index }, { Text(label) }) } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ActionButton("Сахар", Icons.Outlined.WaterDrop, addGlucose); ActionButton("Еда", Icons.Outlined.Restaurant, addMeal) } }
        if (rows.isEmpty()) item { Panel { Text("Здесь появится ваша история", style = MaterialTheme.typography.titleMedium); Muted("За выбранный период записей нет. Можно добавить измерение или сменить фильтр.") } }
        items(rows, key = { "${it.type}:${it.id}" }) { row ->
            Panel {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(row.title, style = MaterialTheme.typography.titleMedium); Muted(row.subtitle); Muted(dateText(row.timestamp)) }
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton({ expanded = true }) { Icon(Icons.Outlined.MoreVert, "Действия с записью") }
                        DropdownMenu(expanded, { expanded = false }) {
                            DropdownMenuItem({ Text("Изменить") }, { expanded = false; when (val value = row.value) { is GlucoseRecord -> editGlucose(value); is MealRecord -> editMeal(value); is WeightRecord -> editWeight(value) } })
                            DropdownMenuItem({ Text("Удалить") }, { expanded = false; delete(row.type, row.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun GlucosePlot(records: List<GlucoseRecord>, profile: Profile) {
    if (records.isEmpty()) { Muted("Добавьте измерения, чтобы увидеть график."); return }
    val sorted = records.sortedBy { it.timestamp }
    val minimum = (sorted.minOf { it.valueMmol } - 1).coerceAtLeast(0.0)
    val maximum = sorted.maxOf { it.valueMmol } + 1
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Muted("${glucoseText(minimum, profile.glucoseUnit)}–${glucoseText(maximum, profile.glucoseUnit)} ${profile.glucoseUnit.label()}"); Muted("${sorted.size} измерений") }
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Точки ручных измерений сахара. Подробные значения перечислены в дневнике." }) {
        val pad = 8.dp.toPx()
        repeat(3) { index -> val y = pad + (size.height - pad * 2) * index / 2; drawLine(grid, Offset(pad, y), Offset(size.width - pad, y), 1.dp.toPx()) }
        val start = sorted.first().timestamp
        val span = (sorted.last().timestamp - start).coerceAtLeast(1)
        sorted.forEach { record ->
            val x = if (sorted.size == 1) size.width / 2 else pad + ((record.timestamp - start).toDouble() / span).toFloat() * (size.width - 2 * pad)
            val y = size.height - pad - ((record.valueMmol - minimum) / (maximum - minimum)).toFloat() * (size.height - 2 * pad)
            drawCircle(color, 4.dp.toPx(), Offset(x, y))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Muted(dateText(sorted.first().timestamp)); if (sorted.size > 1) Muted(dateText(sorted.last().timestamp)) }
}

@Composable fun ActivityScreen(state: AppSnapshot, steps: StepsSnapshot?, loading: Boolean, connect: () -> Unit, refresh: () -> Unit, settings: () -> Unit, addWeight: () -> Unit) {
    ScreenList {
        item { Heading("Движение без суеты", "Шаги с телефона и ваша история веса.") }
        item { Panel(highlighted = true) {
            SectionLabel("Сегодня", Icons.Outlined.DirectionsWalk)
            Text(steps?.today?.count?.toString() ?: "—", style = MaterialTheme.typography.displayMedium)
            Text(if (steps?.today?.count == null) "Пока нет данных о шагах" else "шагов")
            if (state.profile.stepGoal > 0) {
                val count = steps?.today?.count
                Text("Ваша цель · ${state.profile.stepGoal}")
                if (count != null) LinearProgressIndicator(progress = { (count.toFloat() / state.profile.stepGoal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        } }
        item { Panel {
            SectionLabel("Health Connect", Icons.Outlined.Link)
            Muted(steps?.status?.message ?: "Проверяем доступ к шагам…")
            if (steps?.status?.state in setOf(StepsState.PERMISSION_REQUIRED, StepsState.UPDATE_REQUIRED)) ActionButton("Подключить шаги", Icons.Outlined.Link, connect)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(refresh, enabled = !loading) { Text(if (loading) "Читаем…" else "Обновить") }; TextButton(settings) { Text("Настройки") } }
            steps?.lastSyncedAt?.let { Muted("Последнее успешное чтение: ${dateText(it.toEpochMilli())}") }
            Muted("GPS не используется. При отсутствии записей отображается прочерк, а не ноль. Источник шагов настраивается в Health Connect.")
        } }
        item { Panel {
            SectionLabel("Последние 7 дней")
            val days = steps?.days.orEmpty()
            val max = days.mapNotNull { it.count }.maxOrNull()?.coerceAtLeast(1) ?: 1
            if (days.isEmpty()) Muted("История появится после подключения.")
            days.forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(day.date.format(DateTimeFormatter.ofPattern("dd.MM")), Modifier.width(48.dp), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { (day.count?.toFloat() ?: 0f) / max }, Modifier.weight(1f))
                    Text(day.count?.toString() ?: "—", Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } }
        item { Panel { SectionLabel("Вес", Icons.Outlined.MonitorWeight); ActionButton("Записать вес", onClick = addWeight); if (state.weights.isEmpty()) Muted("Добавьте первое взвешивание."); state.weights.take(10).forEach { Text("${number(it.kilograms)} кг · ${dateText(it.timestamp)}") }; Muted("Все записи можно изменить или удалить в дневнике.") } }
    }
}
