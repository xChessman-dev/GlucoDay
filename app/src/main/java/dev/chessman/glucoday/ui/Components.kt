package dev.chessman.glucoday.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chessman.glucoday.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

fun number(value: Double, decimals: Int = 1) = String.format(Locale.forLanguageTag("ru"), "%.$decimals" + "f", value)
fun GlucoseUnit.label() = if (this == GlucoseUnit.MMOL_L) "ммоль/л" else "мг/дл"
fun glucoseText(value: Double, unit: GlucoseUnit) = number(GlucoseConversions.fromMmol(value, unit), if (unit == GlucoseUnit.MMOL_L) 1 else 0)
fun GlucoseContext.label() = when (this) {
    GlucoseContext.FASTING -> "Натощак"; GlucoseContext.BEFORE_MEAL -> "До еды"; GlucoseContext.AFTER_MEAL -> "После еды"
    GlucoseContext.BEDTIME -> "Перед сном"; GlucoseContext.OTHER -> "Другое"
}
fun dateText(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.forLanguageTag("ru")))
fun dateDay(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable fun ScreenList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable fun Heading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        subtitle?.let { Muted(it) }
    }
}
@Composable fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}
@Composable fun Panel(modifier: Modifier = Modifier, highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun ActionButton(text: String, icon: ImageVector = Icons.Outlined.Add, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick, modifier.heightIn(min = 48.dp)) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(text) }
}
@Composable fun SectionLabel(text: String, icon: ImageVector? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
@Composable fun <T> Choice(label: String, value: T, choices: List<T>, name: (T) -> String, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Muted(label)
        Box {
            OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(name(value), Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(expanded, { expanded = false }) {
                choices.forEach { choice -> DropdownMenuItem(text = { Text(name(choice)) }, onClick = { onChange(choice); expanded = false }) }
            }
        }
    }
}
