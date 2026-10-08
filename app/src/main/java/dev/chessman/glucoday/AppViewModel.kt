package dev.chessman.glucoday

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.chessman.glucoday.domain.*
import dev.chessman.glucoday.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as GlucoApplication).repository
    val state = repository.snapshot
    val health = HealthStepsRepository(application)
    val reminders = ReminderScheduler(application)
    val steps = MutableStateFlow<StepsSnapshot?>(null)
    val readingSteps = MutableStateFlow(false)
    val notification = MutableStateFlow(reminders.notificationStatus())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    init {
        viewModelScope.launch {
            state.filter { it.loaded }.map { it.medications }.distinctUntilChanged().collect { list ->
                val result = withContext(Dispatchers.IO) {
                    reminders.replaceAll(list.map { m ->
                        val parts = m.time.split(':')
                        ReminderSpec(m.id, m.name, parts[0].toInt(), parts[1].toInt(), m.enabled)
                    })
                }
                result.error?.let { messages.emit(it) }
            }
        }
    }

    fun refresh() {
        notification.value = reminders.notificationStatus()
        refreshSteps()
    }
    fun refreshSteps() {
        if (readingSteps.value) return
        readingSteps.value = true
        viewModelScope.launch {
            try { steps.value = health.readLastSevenDays() }
            finally { readingSteps.value = false }
        }
    }
    fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { messages.emit(e.message ?: "Не удалось выполнить действие. Повторите попытку.") }
        }
    }
    fun message(text: String) { messages.tryEmit(text) }

    suspend fun writeExport(uri: Uri, backup: Boolean) = withContext(Dispatchers.IO) {
        val text = if (backup) repository.exportBackup() else repository.exportCsv()
        val resolver = getApplication<Application>().contentResolver
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "Не удалось открыть файл" }
            .bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        messages.emit("Файл сохранён. Он содержит личные данные — храните его в безопасном месте.")
    }
    suspend fun readImport(uri: Uri): Pair<String, BackupPreview> = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        requireNotNull(resolver.openInputStream(uri)) { "Не удалось прочитать файл" }.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 10 * 1024 * 1024) { "Копия слишком большая: максимум 10 МБ" }
                output.write(buffer, 0, count)
            }
            val json = output.toString("UTF-8")
            json to repository.previewBackup(json)
        }
    }
}
