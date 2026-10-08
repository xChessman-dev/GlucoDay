package dev.chessman.glucoday.data

import android.content.Context
import androidx.room.withTransaction
import dev.chessman.glucoday.domain.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class AppRepository internal constructor(
    private val db: AppDatabase,
    private val scope: CoroutineScope,
) {
    constructor(context: Context) : this(AppDatabase.get(context), CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val dao = db.dao()

    // Each emission is a single consistent transaction, including after a multi-table import.
    val snapshot: StateFlow<AppSnapshot> = db.invalidationTracker.createFlow(
        "profile", "glucose", "meals", "weights", "medications", "intakes",
    ).map { db.withTransaction { readSnapshot() } }
        .stateIn(scope, SharingStarted.Eagerly, AppSnapshot())

    suspend fun loadProfile(): Profile = dao.profile()?.toDomain() ?: Profile()
    suspend fun updateProfile(profile: Profile) {
        InputValidation.profile(profile)
        dao.upsert(profile.toEntity())
    }
    suspend fun upsertGlucose(record: GlucoseRecord) {
        InputValidation.glucose(record)
        dao.upsert(record.toEntity())
    }
    suspend fun upsertMeal(record: MealRecord) {
        InputValidation.meal(record)
        dao.upsert(record.toEntity())
    }
    suspend fun upsertWeight(record: WeightRecord) {
        InputValidation.weight(record)
        dao.upsert(record.toEntity())
    }
    suspend fun upsertMedication(record: MedicationSchedule) {
        InputValidation.medication(record)
        dao.upsert(record.toEntity())
    }
    suspend fun deleteGlucose(id: String) { InputValidation.uuid(id); dao.deleteGlucose(id) }
    suspend fun deleteMeal(id: String) { InputValidation.uuid(id); dao.deleteMeal(id) }
    suspend fun deleteWeight(id: String) { InputValidation.uuid(id); dao.deleteWeight(id) }
    /** Removing a schedule also removes its intake history, enforced by a foreign key. */
    suspend fun deleteMedication(id: String) { InputValidation.uuid(id); dao.deleteMedication(id) }

    suspend fun toggleMedicationIntake(scheduleId: String, day: LocalDate = LocalDate.now()) {
        db.withTransaction {
            if (dao.intake(scheduleId, day.toString()) != null) {
                dao.deleteIntake(scheduleId, day.toString())
            } else {
                setMedicationIntakeInsideTransaction(scheduleId, day)
            }
        }
    }

    /** Idempotent alternative for controls which submit a desired checked state. */
    suspend fun setMedicationIntake(scheduleId: String, taken: Boolean, day: LocalDate = LocalDate.now()) {
        db.withTransaction {
            if (taken) setMedicationIntakeInsideTransaction(scheduleId, day)
            else dao.deleteIntake(scheduleId, day.toString())
        }
    }

    private suspend fun setMedicationIntakeInsideTransaction(scheduleId: String, day: LocalDate) {
        InputValidation.uuid(scheduleId)
        require(dao.medication(scheduleId) != null) { "Расписание лекарства не найдено" }
        require(!day.isAfter(LocalDate.now())) { "Нельзя отметить приём лекарства за будущий день" }
        if (dao.intake(scheduleId, day.toString()) != null) return
        val time = if (day == LocalDate.now()) System.currentTimeMillis()
        else day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intake = MedicationIntake(scheduleId = scheduleId, timestamp = time, localDate = day.toString())
        InputValidation.intake(intake)
        dao.upsert(intake.toEntity())
    }

    private suspend fun readSnapshot(): AppSnapshot = AppSnapshot(
        profile = dao.profile()?.toDomain() ?: Profile(),
        glucose = dao.glucose().map { it.toDomain() }, meals = dao.meals().map { it.toDomain() },
        weights = dao.weights().map { it.toDomain() }, medications = dao.medications().map { it.toDomain() },
        intakes = dao.intakes().map { it.toDomain() }, loaded = true,
    )

    /** Reads the database directly, so exports never depend on Flow startup timing. */
    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val current = db.withTransaction { readSnapshot() }
        BackupCodec.encode(BackupPayload(BackupValidation.VERSION, System.currentTimeMillis(), current))
    }

    fun previewBackup(json: String): BackupPreview = BackupValidation.validate(BackupCodec.decode(json))

    /** Validates fully before writes; exceptions roll the entire merge back. */
    suspend fun importBackup(json: String, includeProfile: Boolean = false): BackupPreview = withContext(Dispatchers.IO) {
        val payload = BackupCodec.decode(json)
        val preview = BackupValidation.validate(payload)
        db.withTransaction {
            val incoming = payload.snapshot
            // Reject conflicting daily identities instead of removing any existing history.
            incoming.intakes.forEach { intake ->
                val existing = dao.intake(intake.scheduleId, intake.localDate)
                require(existing == null || existing.id == intake.id) {
                    "Конфликт отметок лекарства за ${intake.localDate}. Импорт отменён; данные сохранены."
                }
            }
            incoming.glucose.forEach { dao.upsert(it.toEntity()) }
            incoming.meals.forEach { dao.upsert(it.toEntity()) }
            incoming.weights.forEach { dao.upsert(it.toEntity()) }
            incoming.medications.forEach { dao.upsert(it.toEntity()) }
            incoming.intakes.forEach { dao.upsert(it.toEntity()) }
            if (includeProfile) dao.upsert(incoming.profile.toEntity())
        }
        preview
    }

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        CsvExport.encode(db.withTransaction { readSnapshot() })
    }
}
