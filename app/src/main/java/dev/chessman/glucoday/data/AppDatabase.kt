package dev.chessman.glucoday.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dev.chessman.glucoday.domain.*

@Entity(tableName = "profile")
internal data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val heightCm: Double?,
    val birthYear: Int?,
    val glucoseUnit: String,
    val glucoseTargetMinMmol: Double?,
    val glucoseTargetMaxMmol: Double?,
    val stepGoal: Int,
    val theme: String,
    val extraTopInsetDp: Int,
    val diabetesType: String,
)

@Entity(tableName = "glucose", indices = [Index("timestamp")])
internal data class GlucoseEntity(
    @PrimaryKey val id: String,
    val valueMmol: Double,
    val context: String,
    val note: String,
    val timestamp: Long,
)

@Entity(tableName = "meals", indices = [Index("timestamp")])
internal data class MealEntity(
    @PrimaryKey val id: String,
    val title: String,
    val carbsGrams: Double,
    val caloriesKcal: Double?,
    val timestamp: Long,
)

@Entity(tableName = "weights", indices = [Index("timestamp")])
internal data class WeightEntity(
    @PrimaryKey val id: String,
    val kilograms: Double,
    val timestamp: Long,
)

@Entity(tableName = "medications")
internal data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dosage: String,
    val time: String,
    val enabled: Boolean,
)

@Entity(
    tableName = "intakes",
    foreignKeys = [ForeignKey(
        entity = MedicationEntity::class, parentColumns = ["id"], childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["scheduleId", "localDate"], unique = true), Index("timestamp")],
)
internal data class IntakeEntity(
    @PrimaryKey val id: String,
    val scheduleId: String,
    val timestamp: Long,
    val localDate: String,
)

internal fun Profile.toEntity() = ProfileEntity(1, name, heightCm, birthYear, glucoseUnit.name,
    glucoseTargetMinMmol, glucoseTargetMaxMmol, stepGoal, theme.name, extraTopInsetDp, diabetesType)
internal fun ProfileEntity.toDomain() = Profile(name, heightCm, birthYear, GlucoseUnit.valueOf(glucoseUnit),
    glucoseTargetMinMmol, glucoseTargetMaxMmol, stepGoal, AppTheme.valueOf(theme), extraTopInsetDp, diabetesType)
internal fun GlucoseRecord.toEntity() = GlucoseEntity(id, valueMmol, context.name, note, timestamp)
internal fun GlucoseEntity.toDomain() = GlucoseRecord(valueMmol, GlucoseContext.valueOf(context), note, timestamp, id)
internal fun MealRecord.toEntity() = MealEntity(id, title, carbsGrams, caloriesKcal, timestamp)
internal fun MealEntity.toDomain() = MealRecord(title, carbsGrams, caloriesKcal, timestamp, id)
internal fun WeightRecord.toEntity() = WeightEntity(id, kilograms, timestamp)
internal fun WeightEntity.toDomain() = WeightRecord(kilograms, timestamp, id)
internal fun MedicationSchedule.toEntity() = MedicationEntity(id, name, dosage, time, enabled)
internal fun MedicationEntity.toDomain() = MedicationSchedule(name, dosage, time, enabled, id)
internal fun MedicationIntake.toEntity() = IntakeEntity(id, scheduleId, timestamp, localDate)
internal fun IntakeEntity.toDomain() = MedicationIntake(scheduleId, timestamp, localDate, id)

@Dao
internal interface AppDao {
    @Query("SELECT * FROM profile WHERE id = 1") suspend fun profile(): ProfileEntity?
    @Query("SELECT * FROM glucose ORDER BY timestamp DESC, id ASC") suspend fun glucose(): List<GlucoseEntity>
    @Query("SELECT * FROM meals ORDER BY timestamp DESC, id ASC") suspend fun meals(): List<MealEntity>
    @Query("SELECT * FROM weights ORDER BY timestamp DESC, id ASC") suspend fun weights(): List<WeightEntity>
    @Query("SELECT * FROM medications ORDER BY time ASC, name ASC, id ASC") suspend fun medications(): List<MedicationEntity>
    @Query("SELECT * FROM intakes ORDER BY timestamp DESC, id ASC") suspend fun intakes(): List<IntakeEntity>
    @Query("SELECT * FROM medications WHERE id = :id") suspend fun medication(id: String): MedicationEntity?
    @Query("SELECT * FROM intakes WHERE scheduleId = :scheduleId AND localDate = :localDate")
    suspend fun intake(scheduleId: String, localDate: String): IntakeEntity?

    @Upsert suspend fun upsert(value: ProfileEntity)
    @Upsert suspend fun upsert(value: GlucoseEntity)
    @Upsert suspend fun upsert(value: MealEntity)
    @Upsert suspend fun upsert(value: WeightEntity)
    @Upsert suspend fun upsert(value: MedicationEntity)
    @Upsert suspend fun upsert(value: IntakeEntity)

    @Query("DELETE FROM glucose WHERE id = :id") suspend fun deleteGlucose(id: String)
    @Query("DELETE FROM meals WHERE id = :id") suspend fun deleteMeal(id: String)
    @Query("DELETE FROM weights WHERE id = :id") suspend fun deleteWeight(id: String)
    @Query("DELETE FROM medications WHERE id = :id") suspend fun deleteMedication(id: String)
    @Query("DELETE FROM intakes WHERE scheduleId = :scheduleId AND localDate = :localDate")
    suspend fun deleteIntake(scheduleId: String, localDate: String)
}

@Database(
    entities = [ProfileEntity::class, GlucoseEntity::class, MealEntity::class, WeightEntity::class,
        MedicationEntity::class, IntakeEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "glucoday.db")
                .build().also { instance = it }
        }
    }
}
