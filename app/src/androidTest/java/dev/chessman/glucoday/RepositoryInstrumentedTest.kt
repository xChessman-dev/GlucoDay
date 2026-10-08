package dev.chessman.glucoday

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.chessman.glucoday.data.AppDatabase
import dev.chessman.glucoday.data.AppRepository
import dev.chessman.glucoday.data.BackupCodec
import dev.chessman.glucoday.domain.AppSnapshot
import dev.chessman.glucoday.domain.AppTheme
import dev.chessman.glucoday.domain.BackupPayload
import dev.chessman.glucoday.domain.BackupValidation
import dev.chessman.glucoday.domain.GlucoseContext
import dev.chessman.glucoday.domain.GlucoseConversions
import dev.chessman.glucoday.domain.GlucoseRecord
import dev.chessman.glucoday.domain.GlucoseUnit
import dev.chessman.glucoday.domain.MealRecord
import dev.chessman.glucoday.domain.MedicationSchedule
import dev.chessman.glucoday.domain.Profile
import dev.chessman.glucoday.domain.WeightRecord
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Each test owns a randomly named database; the real glucoday.db is never opened or cleared. */
@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: AppDatabase
    private lateinit var repositoryJob: Job
    private lateinit var repository: AppRepository

    @Before fun openIsolatedDatabase() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "repository-test-${UUID.randomUUID()}.db"
        openRepository()
    }

    @After fun closeIsolatedDatabase() {
        closeRepository()
        // Only this test's exact, unique database name is eligible for cleanup.
        context.deleteDatabase(databaseName)
    }

    @Test fun crudPersistsAcrossDatabaseReopenAndScheduleEditsPreserveIntakes() = runBlocking {
        val fixture = saveCompleteFixture()
        val initial = currentSnapshot()
        val intakeBeforeEdit = initial.intakes.single()

        val editedGlucose = fixture.glucose.copy(valueMmol = 6.123456789, note = "Изменённая заметка\nВторая строка")
        val editedMeal = fixture.meal.copy(title = "Изменённая запись", carbsGrams = 24.125)
        val editedWeight = fixture.weight.copy(kilograms = 71.375)
        val editedMedication = fixture.medication.copy(time = "21:45", enabled = false)
        repository.upsertGlucose(editedGlucose)
        repository.upsertMeal(editedMeal)
        repository.upsertWeight(editedWeight)
        repository.upsertMedication(editedMedication)
        val beforeReopen = currentSnapshot()
        assertEquals(editedGlucose, beforeReopen.glucose.single())
        assertEquals(editedMeal, beforeReopen.meals.single())
        assertEquals(editedWeight, beforeReopen.weights.single())
        assertEquals(editedMedication, beforeReopen.medications.single())
        assertEquals(intakeBeforeEdit, beforeReopen.intakes.single())

        closeRepository()
        openRepository()
        assertEquals(beforeReopen, currentSnapshot())
        assertEquals(fixture.profile, repository.loadProfile())

        repository.deleteGlucose(editedGlucose.id)
        repository.deleteMeal(editedMeal.id)
        repository.deleteWeight(editedWeight.id)
        repository.deleteMedication(editedMedication.id)
        val afterDelete = currentSnapshot()
        assertTrue(afterDelete.glucose.isEmpty())
        assertTrue(afterDelete.meals.isEmpty())
        assertTrue(afterDelete.weights.isEmpty())
        assertTrue(afterDelete.medications.isEmpty())
        assertTrue("Deleting a schedule must cascade to only its intake history", afterDelete.intakes.isEmpty())
        assertEquals(fixture.profile, afterDelete.profile)
    }

    @Test fun repeatedAndConcurrentCheckedStateDoesNotCreateDuplicateDailyIntakes() = runBlocking {
        val medication = MedicationSchedule(name = "Тестовое расписание", time = "08:15")
        repository.upsertMedication(medication)
        coroutineScope {
            (1..12).map {
                async(Dispatchers.IO) { repository.setMedicationIntake(medication.id, true, TEST_DAY) }
            }.awaitAll()
        }
        val firstIntake = currentSnapshot().intakes.single()
        repository.setMedicationIntake(medication.id, true, TEST_DAY)
        assertEquals(firstIntake, currentSnapshot().intakes.single())

        repository.setMedicationIntake(medication.id, true, TEST_DAY.minusDays(1))
        val otherMedication = MedicationSchedule(name = "Другое тестовое расписание", time = "08:15")
        repository.upsertMedication(otherMedication)
        repository.setMedicationIntake(otherMedication.id, true, TEST_DAY)
        assertEquals(3, currentSnapshot().intakes.size)

        repository.setMedicationIntake(medication.id, false, TEST_DAY)
        repository.setMedicationIntake(medication.id, false, TEST_DAY)
        val remaining = currentSnapshot().intakes
        assertEquals(2, remaining.size)
        assertFalse(remaining.any { it.scheduleId == medication.id && it.localDate == TEST_DAY.toString() })
        assertTrue(remaining.any { it.scheduleId == otherMedication.id })
    }

    @Test fun backupRestoresCanonicalValuesAndMergesWithoutDuplicatingOrRemovingOtherRecords() = runBlocking {
        val fixture = saveCompleteFixture()
        val original = currentSnapshot()
        val backup = repository.exportBackup()
        assertEquals(original, BackupCodec.decode(backup).snapshot.chronological())
        assertEquals(5, repository.previewBackup(backup).totalRecords)

        repository.deleteGlucose(fixture.glucose.id)
        repository.deleteMeal(fixture.meal.id)
        repository.deleteWeight(fixture.weight.id)
        repository.deleteMedication(fixture.medication.id)
        val localProfile = Profile(name = "Текущий тестовый профиль", theme = AppTheme.LIGHT)
        repository.updateProfile(localProfile)
        val unrelated = GlucoseRecord(valueMmol = 5.543210987, timestamp = TEST_TIME - 10_000)
        repository.upsertGlucose(unrelated)

        repository.importBackup(backup)
        val expectedWithoutProfile = original.copy(
            profile = localProfile,
            glucose = original.glucose + unrelated,
        ).chronological()
        assertEquals(expectedWithoutProfile, currentSnapshot())

        repository.importBackup(backup, includeProfile = true)
        repository.importBackup(backup, includeProfile = true)
        val expectedWithProfile = expectedWithoutProfile.copy(profile = original.profile)
        assertEquals(expectedWithProfile, currentSnapshot())
        assertEquals(
            fixture.glucose.valueMmol.toBits(),
            currentSnapshot().glucose.first { it.id == fixture.glucose.id }.valueMmol.toBits(),
        )
        assertEquals(original.intakes.single().id, currentSnapshot().intakes.single().id)
    }

    @Test fun conflictingDailyIntakeRejectsWholeMergeAndPreservesExistingData() = runBlocking {
        saveCompleteFixture()
        val before = currentSnapshot()
        val incoming = before.copy(
            profile = before.profile.copy(name = "Не должен примениться"),
            glucose = before.glucose + GlucoseRecord(valueMmol = 8.25, timestamp = TEST_TIME - 1000),
            intakes = listOf(before.intakes.single().copy(id = UUID.randomUUID().toString())),
        )
        val error = runCatching { repository.importBackup(backupOf(incoming), includeProfile = true) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(before, currentSnapshot())
    }

    @Test fun invalidBackupIsRejectedBeforeAnyWrites() = runBlocking {
        saveCompleteFixture()
        val before = currentSnapshot()
        val incoming = before.copy(
            glucose = before.glucose + GlucoseRecord(valueMmol = 7.75, timestamp = TEST_TIME - 1000),
        )
        val damaged = JSONObject(backupOf(incoming)).apply {
            getJSONArray("weights").getJSONObject(0).put("kilograms", -1)
        }.toString()
        val error = runCatching { repository.importBackup(damaged, includeProfile = true) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(before, currentSnapshot())
    }

    @Test fun databaseFailureMidImportRollsBackEarlierWrites() = runBlocking {
        saveCompleteFixture()
        val before = currentSnapshot()
        val incoming = AppSnapshot(
            profile = Profile(name = "Не должен примениться"),
            glucose = listOf(GlucoseRecord(valueMmol = 9.125, timestamp = TEST_TIME - 2000)),
            weights = listOf(WeightRecord(kilograms = 74.125, timestamp = TEST_TIME - 2000)),
            loaded = true,
        )
        // The JSON is valid. This private test-only SQLite trigger fails after the glucose
        // insert has occurred, proving Room's transaction rolls back actual partial writes.
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_test_weight BEFORE INSERT ON weights BEGIN SELECT RAISE(ABORT, 'test import failure'); END",
        )
        try {
            val error = runCatching { repository.importBackup(backupOf(incoming), includeProfile = true) }.exceptionOrNull()
            assertNotNull("The injected database failure must reach the caller", error)
            assertEquals(before, currentSnapshot())
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_test_weight")
        }
        repository.importBackup(backupOf(incoming), includeProfile = true)
        assertEquals(2, currentSnapshot().glucose.size)
        assertEquals(2, currentSnapshot().weights.size)
    }

    private fun openRepository() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        repositoryJob = SupervisorJob()
        repository = AppRepository(database, CoroutineScope(repositoryJob + Dispatchers.IO))
    }

    private fun closeRepository() {
        if (::repositoryJob.isInitialized) runBlocking { repositoryJob.cancelAndJoin() }
        if (::database.isInitialized) database.close()
    }

    private suspend fun currentSnapshot() = BackupCodec.decode(repository.exportBackup()).snapshot.chronological()

    private fun backupOf(snapshot: AppSnapshot) = BackupCodec.encode(
        BackupPayload(BackupValidation.VERSION, TEST_TIME, snapshot),
    )

    private suspend fun saveCompleteFixture(): Fixture {
        val fixture = Fixture(
            profile = Profile(
                name = "Тестовый профиль", heightCm = 171.25, birthYear = 1990,
                glucoseUnit = GlucoseUnit.MG_DL,
                glucoseTargetMinMmol = 4.25, glucoseTargetMaxMmol = 8.75,
                stepGoal = 4321, theme = AppTheme.DARK, extraTopInsetDp = 13,
                diabetesType = "Тестовое поле",
            ),
            glucose = GlucoseRecord(
                valueMmol = GlucoseConversions.toMmol(101.0, GlucoseUnit.MG_DL),
                context = GlucoseContext.AFTER_MEAL, note = "Тест: «строка»\nЮникод", timestamp = TEST_TIME,
            ),
            meal = MealRecord(title = "Тестовая запись", carbsGrams = 22.123456, caloriesKcal = null, timestamp = TEST_TIME),
            weight = WeightRecord(kilograms = 72.123456, timestamp = TEST_TIME),
            medication = MedicationSchedule(name = "Тестовое расписание", dosage = "", time = "08:15"),
        )
        repository.updateProfile(fixture.profile)
        repository.upsertGlucose(fixture.glucose)
        repository.upsertMeal(fixture.meal)
        repository.upsertWeight(fixture.weight)
        repository.upsertMedication(fixture.medication)
        repository.setMedicationIntake(fixture.medication.id, true, TEST_DAY)
        return fixture
    }

    private data class Fixture(
        val profile: Profile,
        val glucose: GlucoseRecord,
        val meal: MealRecord,
        val weight: WeightRecord,
        val medication: MedicationSchedule,
    )

    companion object {
        private val TEST_TIME = Instant.parse("2020-01-02T12:34:56.789Z").toEpochMilli()
        private val TEST_DAY = LocalDate.of(2020, 1, 2)
    }
}
