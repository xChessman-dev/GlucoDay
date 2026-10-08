package dev.chessman.glucoday

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chessman.glucoday.domain.*
import dev.chessman.glucoday.nutrition.FoodScreen
import dev.chessman.glucoday.platform.*
import dev.chessman.glucoday.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val model by viewModels<AppViewModel>()
    private val incoming = MutableStateFlow<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consume(intent)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            GlucoTheme(state.profile.theme) {
                val background = MaterialTheme.colorScheme.background
                SideEffect {
                    val light = background.luminance() > 0.5f
                    WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
                }
                Surface(Modifier.fillMaxSize(), color = background) {
                    GlucoApp(model, incoming) { incoming.value = null }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); consume(intent) }
    private fun consume(intent: Intent?) {
        val action = intent?.action
        if (action in setOf(PlatformActions.QUICK_ADD_GLUCOSE, PlatformActions.OPEN_MEDICATIONS, PlatformActions.SHOW_PRIVACY)) {
            incoming.value = action
            intent?.action = null
        }
    }
}

@Composable private fun GlucoApp(vm: AppViewModel, incoming: MutableStateFlow<String?>, consumed: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()
    val loading by vm.readingSteps.collectAsStateWithLifecycle()
    val notification by vm.notification.collectAsStateWithLifecycle()
    val action by incoming.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletion by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Pair<String, BackupPreview>?>(null) }
    var includeProfile by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun openEditor(kind: String, id: String? = null) { editId = id; editor = kind }
    fun launchSafe(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { vm.message("Не удалось открыть системный экран. Проверьте настройки Android.") } }
    val stepsPermission = rememberLauncherForActivityResult(HealthStepsRepository.permissionContract()) { vm.refreshSteps() }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.perform { vm.writeExport(uri, true) } } }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> vm.perform { vm.writeExport(uri, false) } } }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> vm.perform { pendingImport = vm.readImport(uri); includeProfile = false } } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(action, state.loaded) {
        if (state.loaded && action != null) {
            when (action) { PlatformActions.QUICK_ADD_GLUCOSE -> openEditor("glucose"); PlatformActions.OPEN_MEDICATIONS -> page = 0; PlatformActions.SHOW_PRIVACY -> privacy = true }
            consumed()
        }
    }
    BackHandler(privacy || page != 0) { if (privacy) privacy = false else page = 0 }
    val names = listOf("Сегодня", "Дневник", "Еда", "Шаги", "Профиль")
    val icons = listOf(Icons.Outlined.WbSunny, Icons.Outlined.MenuBook, Icons.Outlined.Restaurant, Icons.Outlined.DirectionsWalk, Icons.Outlined.PersonOutline)
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(top = state.profile.extraTopInsetDp.dp),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.WaterDrop, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text("GlucoDay", style = MaterialTheme.typography.titleMedium)
                }
                if (privacy) TextButton({ privacy = false }) { Text("Закрыть") } else Muted("ЛОКАЛЬНО")
            }
        },
        bottomBar = {
            if (!privacy) NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                names.forEachIndexed { index, title -> NavigationBarItem(page == index, { page = index }, icon = { Icon(icons[index], null) }, label = { Text(title, maxLines = 1) }) }
            }
        }, snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                if (!state.loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else if (privacy) PrivacyContent()
                else when (page) {
                    0 -> TodayScreen(state, steps, { openEditor("glucose") }, { openEditor("meal") }, { openEditor("weight") }, { id, checked -> vm.perform { vm.repository.setMedicationIntake(id, checked) } }, { page = 3 }, { page = 4 })
                    1 -> DiaryScreen(state, { openEditor("glucose", it.id) }, { openEditor("meal", it.id) }, { openEditor("weight", it.id) }, { type, id -> deletion = type to id }, { openEditor("glucose") }, { openEditor("meal") })
                    2 -> FoodScreen(onLogMeal = { title, carbs, kcal -> vm.perform { vm.repository.upsertMeal(MealRecord(title, carbs, kcal)); vm.message("Еда добавлена в дневник") } })
                    3 -> ActivityScreen(state, steps, loading, {
                        if (steps?.status?.state == StepsState.PERMISSION_REQUIRED) runCatching { stepsPermission.launch(HealthStepsRepository.REQUIRED_PERMISSIONS) }.onFailure { vm.message("Откройте Health Connect и разрешите доступ к шагам.") }
                        else launchSafe(vm.health.providerInstallIntent())
                    }, vm::refreshSteps, { launchSafe(vm.health.manageAccessIntent()) }, { openEditor("weight") })
                    4 -> ProfileScreen(state, notification, { openEditor("profile") }, { profile -> vm.perform { vm.repository.updateProfile(profile) } }, { openEditor("med", it?.id) }, { deletion = 4 to it }, {
                        if (Build.VERSION.SDK_INT >= 33 && !notification.permissionGranted) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else launchSafe(vm.reminders.notificationSettingsIntent())
                    }, { if (!QuickGlucoseWidgetProvider.requestPin(context)) vm.message("Добавьте виджет GlucoDay через меню виджетов вашего рабочего стола.") }, { backup ->
                        if (backup) exportJson.launch("GlucoDay-${LocalDate.now()}.json") else exportCsv.launch("GlucoDay-${LocalDate.now()}.csv")
                    }, { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, { privacy = true })
                }
            }
        }
    }
    val close = { editor = null; editId = null }
    when (editor) {
        "glucose" -> GlucoseEditor(state.profile.glucoseUnit, state.glucose.find { it.id == editId }, close, vm.repository::upsertGlucose)
        "meal" -> MealEditor(state.meals.find { it.id == editId }, close, vm.repository::upsertMeal)
        "weight" -> WeightEditor(state.weights.find { it.id == editId }, close, vm.repository::upsertWeight)
        "med" -> MedicationEditor(state.medications.find { it.id == editId }, close, vm.repository::upsertMedication)
        "profile" -> ProfileEditor(state.profile, close, vm.repository::updateProfile)
    }
    deletion?.let { (type, id) -> AlertDialog(onDismissRequest = { deletion = null }, title = { Text("Удалить запись?") }, text = { Text(if (type == 4) "Будут удалены это расписание и все отметки его приёма. Остальные лекарства останутся." else "Эту запись нельзя будет вернуть без сохранённой резервной копии.") }, confirmButton = { TextButton({ vm.perform { when (type) { 1 -> vm.repository.deleteGlucose(id); 2 -> vm.repository.deleteMeal(id); 3 -> vm.repository.deleteWeight(id); 4 -> vm.repository.deleteMedication(id) }; deletion = null } }) { Text("Удалить") } }, dismissButton = { TextButton({ deletion = null }) { Text("Отмена") } }) }
    pendingImport?.let { (json, preview) ->
        AlertDialog(onDismissRequest = { if (!importing) pendingImport = null }, title = { Text("Восстановить копию?") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Сахар: ${preview.glucoseCount}\nЕда: ${preview.mealCount}\nВес: ${preview.weightCount}\nРасписания: ${preview.medicationCount}\nПриёмы: ${preview.intakeCount}")
                Text("Записи объединятся с дневником. Совпадающие идентификаторы будут обновлены из копии. Перед импортом рекомендуется сохранить текущую копию.")
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(includeProfile, { includeProfile = it }); Text("Также заменить личные настройки") }
            }
        }, confirmButton = { TextButton({ importing = true; scope.launch { try { vm.repository.importBackup(json, includeProfile); pendingImport = null; vm.message("Копия восстановлена") } catch (e: Exception) { vm.message(e.message ?: "Импорт не выполнен") } finally { importing = false } } }, enabled = !importing) { Text(if (importing) "Импорт…" else "Восстановить") } }, dismissButton = { TextButton({ pendingImport = null }, enabled = !importing) { Text("Отмена") } })
    }
}

class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GlucoTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding()) { Column { TextButton({ finish() }) { Text("Назад") }; Box(Modifier.weight(1f)) { PrivacyContent() } } } } }
    }
}
