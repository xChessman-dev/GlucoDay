package dev.chessman.glucoday.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chessman.glucoday.domain.*
import dev.chessman.glucoday.platform.*

@Composable fun ProfileScreen(
    state: AppSnapshot, notification: NotificationStatus, editProfile: () -> Unit, update: (Profile) -> Unit,
    editMedication: (MedicationSchedule?) -> Unit, deleteMedication: (String) -> Unit,
    requestNotifications: () -> Unit, pinWidget: () -> Unit, export: (Boolean) -> Unit, importBackup: () -> Unit, privacy: () -> Unit,
) {
    val profile = state.profile
    var inset by remember(profile.extraTopInsetDp) { mutableFloatStateOf(profile.extraTopInsetDp.toFloat()) }
    ScreenList {
        item { Heading("Всё под вас", "Личный профиль, привычки и настройки.") }
        item { Panel {
            SectionLabel(if (profile.name.isBlank()) "Ваш профиль" else profile.name, Icons.Outlined.PersonOutline)
            Muted(listOfNotNull(profile.heightCm?.let { "Рост ${number(it, 0)} см" }, profile.birthYear?.let { "$it г. р." }, profile.diabetesType.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "Можно указать рост, год рождения и личную цель шагов." })
            ActionButton("Изменить профиль", Icons.Outlined.Edit, editProfile)
            Choice("Единицы сахара", profile.glucoseUnit, GlucoseUnit.entries, { it.label() }, { update(profile.copy(glucoseUnit = it)) })
            if (profile.glucoseTargetMinMmol != null && profile.glucoseTargetMaxMmol != null) {
                Text("Ваш диапазон: ${glucoseText(profile.glucoseTargetMinMmol, profile.glucoseUnit)}–${glucoseText(profile.glucoseTargetMaxMmol, profile.glucoseUnit)} ${profile.glucoseUnit.label()}")
            } else Muted("Целевой диапазон не задан. При необходимости внесите границы, согласованные с врачом.")
        } }
        item { Panel {
            SectionLabel("Таблетки и напоминания", Icons.Outlined.Medication)
            Muted("Запишите схему из назначения врача. Для нескольких приёмов одного лекарства добавьте несколько записей с разным временем.")
            state.medications.forEach { med ->
                Row {
                    Column(Modifier.weight(1f)) { Text(med.name, style = MaterialTheme.typography.titleMedium); Muted("${med.time} · ${if (med.enabled) "Напоминание включено" else "Без напоминания"}"); if (med.dosage.isNotBlank()) Muted(med.dosage) }
                    IconButton({ editMedication(med) }) { Icon(Icons.Outlined.Edit, "Изменить ${med.name}") }
                    IconButton({ deleteMedication(med.id) }) { Icon(Icons.Outlined.DeleteOutline, "Удалить ${med.name}") }
                }
            }
            ActionButton("Добавить приём", onClick = { editMedication(null) })
            Muted(notification.message)
            if (!notification.canNotify) OutlinedButton(requestNotifications) { Text("Разрешить уведомления") }
            Muted(ReminderScheduler.TIMING_NOTICE)
        } }
        item { Panel {
            SectionLabel("Внешний вид", Icons.Outlined.Palette)
            Choice("Тема", profile.theme, AppTheme.entries, { when (it) { AppTheme.DARK -> "Тёмная"; AppTheme.LIGHT -> "Светлая"; AppTheme.SYSTEM -> "Как в системе" } }, { update(profile.copy(theme = it)) })
            Text("Дополнительный отступ сверху · ${inset.toInt()} dp")
            Slider(inset, { inset = it }, valueRange = 0f..120f, steps = 14, onValueChangeFinished = { update(profile.copy(extraTopInsetDp = inset.toInt())) })
            Muted("Вырез камеры учитывается автоматически. Если ваш «остров» перекрывает интерфейс, увеличьте отступ.")
            ActionButton("Виджет «Добавить сахар»", Icons.Outlined.Widgets, pinWidget)
        } }
        item { Panel {
            SectionLabel("Ваши данные", Icons.Outlined.FolderOpen)
            Muted("Без аккаунта и облака. Чтобы не потерять дневник при удалении приложения или смене телефона, сохраните копию JSON.")
            ActionButton("Сохранить копию JSON", Icons.Outlined.SaveAlt, { export(true) })
            OutlinedButton(importBackup, Modifier.heightIn(min = 48.dp)) { Text("Восстановить из копии") }
            OutlinedButton({ export(false) }, Modifier.heightIn(min = 48.dp)) { Text("Экспорт дневника CSV") }
            Muted("Файлы не зашифрованы и содержат личные записи. JSON включает дневник, профиль и расписание; свои продукты и избранное пока сохраняются только в приложении.")
        } }
        item { Panel {
            SectionLabel("GlucoDay · 0.1.0")
            Muted("Личный дневник для Android. Не измеряет сахар, не ставит диагноз и не подбирает лечение. Порции и углеводы — ориентиры, а не назначение.")
            TextButton(privacy) { Text("Конфиденциальность и ограничения") }
        } }
    }
}

@Composable fun PrivacyContent() {
    ScreenList {
        item { Heading("Данные остаются у вас", "GlucoDay · конфиденциальность") }
        item { Panel { SectionLabel("Что хранится"); Text("Введённые измерения, еда, вес, профиль и отметки приёма сохраняются в базе на телефоне. Автоматическое облачное резервирование отключено. База защищена песочницей Android, но не отдельным паролем приложения.") } }
        item { Panel { SectionLabel("Шаги"); Text("Приложение читает только шаги за последние 7 дней через Health Connect, когда вы его открываете. Не читает геолокацию и не записывает медицинские данные в Health Connect. Доступ можно отозвать в его настройках.") } }
        item { Panel { SectionLabel("Без сетевых запросов"); Text("В этой версии нет разрешения на интернет, рекламы, аналитики и облачного ИИ. Приложение не отправляет записи разработчику. Ссылки на источники открываются отдельно вашим браузером.") } }
        item { Panel { SectionLabel("Копии и удаление"); Text("JSON и CSV создаются только по вашему запросу и содержат личные данные в открытом виде. Удаление приложения удалит локальную базу, но не сохранённые вами файлы. Копия JSON не включает свои продукты и избранное. Записи можно удалять в дневнике, а все данные — через настройки Android.") } }
        item { Panel { SectionLabel("Границы возможностей"); Text("GlucoDay — дневник, не медицинское изделие. Цели сахара и схема лекарств задаются вами по рекомендациям врача. Нет расчёта доз, диагноза или гарантии предотвращения осложнений. Уведомления могут задерживаться системой. Расчёты питания приблизительны: сверяйте порцию и упаковку. При плохом самочувствии обратитесь за медицинской помощью.") } }
    }
}
