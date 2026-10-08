package dev.chessman.glucoday.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.Settings
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class StepsState { AVAILABLE, PERMISSION_REQUIRED, UPDATE_REQUIRED, UNAVAILABLE, ERROR }

data class StepsStatus(
    val state: StepsState,
    val message: String,
    /** Capability only: this does not assert that tracking is enabled or a source has supplied data. */
    val onDeviceTrackingSupported: Boolean,
)

data class DailySteps(val date: LocalDate, val count: Long?)

data class StepsSnapshot(
    val days: List<DailySteps>,
    val status: StepsStatus,
    /** Last successful read, not the last time an upstream device or source synced. */
    val lastSyncedAt: Instant?,
    val error: String? = null,
) {
    val today: DailySteps? get() = days.lastOrNull()
}

/**
 * Foreground-only, read-only Health Connect integration. No network and no fabricated zeroes.
 * Unfiltered COUNT_TOTAL aggregation lets Health Connect apply source priority/deduplication.
 * https://developer.android.com/health-and-fitness/health-connect/read-data
 */
class HealthStepsRepository(context: Context, private val clock: Clock = Clock.systemUTC()) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("steps_sync", Context.MODE_PRIVATE)
    private val readMutex = Mutex()

    suspend fun status(): StepsStatus = withContext(Dispatchers.IO) {
        try {
            withTimeout(15_000) { readStatus() }
        } catch (_: TimeoutCancellationException) {
            failureStatus("Health Connect не ответил. Попробуйте ещё раз.")
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            permissionStatus()
        } catch (_: Exception) {
            failureStatus("Не удалось проверить Health Connect. Откройте его настройки и повторите попытку.")
        }
    }

    suspend fun readLastSevenDays(): StepsSnapshot = readMutex.withLock {
        withContext(Dispatchers.IO) {
            val windows = lastSevenDayWindows(clock.instant(), ZoneId.systemDefault())
            val emptyDays = windows.map { DailySteps(it.date, null) }
            try {
                withTimeout(25_000) {
                    val connectionStatus = readStatus()
                    if (connectionStatus.state != StepsState.AVAILABLE) {
                        return@withTimeout StepsSnapshot(emptyDays, connectionStatus, lastSync())
                    }
                    val client = HealthConnectClient.getOrCreate(appContext)
                    val days = windows.map { window ->
                        val count = if (window.end > window.start) {
                            client.aggregate(
                                AggregateRequest(
                                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                                    timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
                                    // Intentionally do not filter data origins or sum raw source records.
                                ),
                            )[StepsRecord.COUNT_TOTAL]
                        } else null
                        DailySteps(window.date, count)
                    }
                    val syncedAt = clock.instant()
                    preferences.edit().putLong("last_successful_read", syncedAt.toEpochMilli()).apply()
                    val message = when {
                        days.all { it.count == null } ->
                            "За 7 дней нет записей шагов. Проверьте источник и его синхронизацию в Health Connect. Установка Health Connect сама по себе не гарантирует подсчёт."
                        days.last().count == null ->
                            "За сегодня пока нет записей шагов. Данные появятся после синхронизации источника с Health Connect."
                        else -> "Шаги прочитаны из Health Connect. Обновление источника может происходить с задержкой."
                    }
                    StepsSnapshot(days, connectionStatus.copy(message = message), syncedAt)
                }
            } catch (_: TimeoutCancellationException) {
                failedSnapshot(emptyDays, "Чтение шагов заняло слишком много времени. Попробуйте ещё раз.")
            } catch (error: CancellationException) {
                throw error
            } catch (_: SecurityException) {
                StepsSnapshot(emptyDays, permissionStatus(), lastSync(), "Разрешение на чтение шагов отозвано или недоступно.")
            } catch (_: Exception) {
                failedSnapshot(emptyDays, "Не удалось прочитать шаги. Проверьте Health Connect и повторите попытку.")
            }
        }
    }

    fun manageAccessIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    fun providerInstallIntent(): Intent = if (Build.VERSION.SDK_INT >= 34) {
        // Android 14+ uses a system module; installing the older provider APK is not a repair.
        Intent(Settings.ACTION_SETTINGS)
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER_PACKAGE"))
    }

    private suspend fun readStatus(): StepsStatus {
        val trackingSupported = supportsOnDeviceTracking()
        return when (HealthConnectClient.getSdkStatus(appContext)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                val granted = HealthConnectClient.getOrCreate(appContext).permissionController.getGrantedPermissions()
                if (granted.containsAll(REQUIRED_PERMISSIONS)) {
                    StepsStatus(
                        StepsState.AVAILABLE,
                        if (trackingSupported) {
                            "Доступ к шагам разрешён. Система поддерживает подсчёт на телефоне; наличие записей проверяется при обновлении."
                        } else {
                            "Доступ к шагам разрешён. Нужен источник, записывающий шаги в Health Connect: приложение телефона или часов."
                        },
                        trackingSupported,
                    )
                } else permissionStatus()
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StepsStatus(
                StepsState.UPDATE_REQUIRED,
                if (Build.VERSION.SDK_INT >= 34) {
                    "Обновите Android и системный модуль Health Connect в настройках телефона."
                } else "Установите или обновите Health Connect, затем разрешите чтение шагов.",
                trackingSupported,
            )
            else -> StepsStatus(
                StepsState.UNAVAILABLE,
                if (Build.VERSION.SDK_INT < 28) {
                    "Health Connect требует Android 9 или новее. На этом устройстве шаги недоступны."
                } else {
                    "Health Connect недоступен в этой системе или профиле. Проверьте настройки телефона и используйте основной профиль."
                },
                trackingSupported,
            )
        }
    }

    private fun permissionStatus() = StepsStatus(
        StepsState.PERMISSION_REQUIRED,
        "Разрешите GlucoDay читать только шаги в Health Connect. Геолокация не нужна.",
        supportsOnDeviceTracking(),
    )

    private fun failureStatus(message: String) = StepsStatus(StepsState.ERROR, message, supportsOnDeviceTracking())

    private fun failedSnapshot(days: List<DailySteps>, message: String) =
        StepsSnapshot(days, failureStatus(message), lastSync(), message)

    private fun lastSync(): Instant? = preferences.getLong("last_successful_read", 0L)
        .takeIf { it > 0L }?.let(Instant::ofEpochMilli)

    private fun supportsOnDeviceTracking(): Boolean = Build.VERSION.SDK_INT >= 34 &&
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 20

    companion object {
        private const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        val REQUIRED_PERMISSIONS: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))
        fun permissionContract() = PermissionController.createRequestPermissionResultContract()
    }
}
