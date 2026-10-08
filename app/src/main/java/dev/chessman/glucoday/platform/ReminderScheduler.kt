package dev.chessman.glucoday.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

data class ReminderSpec(
    val id: String,
    val title: String,
    val timeHour: Int,
    val timeMinute: Int,
    val enabled: Boolean,
)

data class NotificationStatus(
    val permissionGranted: Boolean,
    val channelEnabled: Boolean,
    val message: String,
) {
    val canNotify: Boolean get() = permissionGranted && channelEnabled
}

data class ReminderScheduleResult(val scheduledCount: Int, val error: String? = null)

/**
 * Daily local, deliberately inexact alarms. Android/POCO battery restrictions can delay delivery;
 * no exact-alarm access, background service, GPS, or network is requested.
 * https://developer.android.com/develop/background-work/services/alarms
 *
 * The caller must pass the complete current list after saving medication changes. This store
 * holds only id/time/enabled and delivery metadata, never medication names, dosages or history.
 */
class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    fun replaceAll(specs: List<ReminderSpec>): ReminderScheduleResult = synchronized(LOCK) {
        require(specs.map { it.id }.distinct().size == specs.size) { "Reminder ids must be unique" }
        require(specs.all { it.id.isNotBlank() && it.timeHour in 0..23 && it.timeMinute in 0..59 }) {
            "Each reminder needs an id and a valid local time"
        }
        val oldSpecs = readSpecs()
        val revision = nextRevision()
        val serialized = JSONArray().apply {
            specs.forEach { spec ->
                put(JSONObject().apply {
                    put("id", spec.id)
                    put("hour", spec.timeHour)
                    put("minute", spec.timeMinute)
                    put("enabled", spec.enabled)
                })
            }
        }
        val editor = preferences.edit().putString(KEY_SPECS, serialized.toString()).putLong(KEY_REVISION, revision)
        val currentIds = specs.mapTo(mutableSetOf()) { it.id }
        oldSpecs.filter { it.id !in currentIds }.forEach { editor.remove(deliveryKey(it.id)) }
        if (!editor.commit()) {
            return@synchronized ReminderScheduleResult(0, "Не удалось сохранить расписание напоминаний.")
        }
        oldSpecs.forEach { cancelAlarm(it.id) }
        oldSpecs.filter { old -> specs.none { it.id == old.id && it.enabled } }
            .forEach { notificationManager.cancel(it.id, NOTIFICATION_ID) }
        scheduleSpecs(specs, revision)
    }

    /** Called on app start/resume, boot, package replacement, clock change and time-zone change. */
    fun rescheduleAll(): ReminderScheduleResult = synchronized(LOCK) {
        val specs = readSpecs()
        val revision = nextRevision()
        if (!preferences.edit().putLong(KEY_REVISION, revision).commit()) {
            return@synchronized ReminderScheduleResult(0, "Не удалось восстановить расписание напоминаний.")
        }
        specs.forEach { cancelAlarm(it.id) }
        scheduleSpecs(specs, revision)
    }

    fun notificationStatus(): NotificationStatus {
        ensureChannel()
        val permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val channelEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled() &&
            notificationManager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        val message = when {
            !permissionGranted -> "Разрешите уведомления, чтобы видеть напоминания."
            !channelEnabled -> "Уведомления GlucoDay отключены в настройках телефона."
            else -> "Напоминания включены. Android может доставить их позже указанного времени, особенно при экономии батареи."
        }
        return NotificationStatus(permissionGranted, channelEnabled, message)
    }

    fun notificationSettingsIntent(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)

    /** Handles only our private explicit alarm intent and rechecks saved state before notifying. */
    @SuppressLint("MissingPermission") // notificationStatus checks POST_NOTIFICATIONS; posting also handles revocation.
    internal fun deliver(intent: Intent) = synchronized(LOCK) {
        if (intent.action != ACTION_REMINDER) return@synchronized
        val id = intent.data?.takeIf { it.scheme == "glucoday" && it.authority == "reminder" }
            ?.lastPathSegment ?: return@synchronized
        val revision = preferences.getLong(KEY_REVISION, 0L)
        if (intent.getLongExtra(EXTRA_REVISION, -1L) != revision) return@synchronized
        val spec = readSpecs().firstOrNull { it.id == id && it.enabled } ?: return@synchronized
        val scheduledAt = intent.getLongExtra(EXTRA_TRIGGER_AT, -1L)
        if (scheduledAt < 0) return@synchronized
        val now = Instant.now()
        if (now.toEpochMilli() < scheduledAt) return@synchronized
        val zone = ZoneId.systemDefault()
        val occurrenceDate = Instant.ofEpochMilli(scheduledAt).atZone(zone).toLocalDate().toString()
        val alreadyDelivered = preferences.getString(deliveryKey(id), null) == occurrenceDate
        // Persist the following alarm independently of notification permission, so granting
        // permission later still leaves the next day's reminder scheduled.
        runCatching { schedule(spec, revision, now, zone) }
        if (alreadyDelivered || !notificationStatus().canNotify) return@synchronized

        val openApp = PendingIntent.getActivity(
            appContext,
            0,
            PlatformActions.mainActivityIntent(appContext, PlatformActions.OPEN_MEDICATIONS, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("GlucoDay")
            .setContentText("Запланированное напоминание")
            .build()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("GlucoDay")
            .setContentText("У вас запланировано напоминание. Откройте расписание.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            notificationManager.notify(id, NOTIFICATION_ID, notification)
            preferences.edit().putString(deliveryKey(id), occurrenceDate).apply()
        } catch (_: SecurityException) {
            // Permission may be revoked between checking it and posting. No crash or retry loop.
        }
    }

    private fun scheduleSpecs(specs: List<ReminderSpec>, revision: Long): ReminderScheduleResult {
        ensureChannel()
        var count = 0
        var error: String? = null
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        specs.filter { it.enabled }.forEach { spec ->
            try {
                schedule(spec, revision, now, zone)
                count++
            } catch (_: RuntimeException) {
                error = "Не все напоминания удалось запланировать. Откройте GlucoDay снова и проверьте настройки батареи."
            }
        }
        return ReminderScheduleResult(count, error)
    }

    private fun schedule(spec: ReminderSpec, revision: Long, now: Instant, zone: ZoneId) {
        val next = nextReminderTime(now, zone, spec.timeHour, spec.timeMinute)
        val intent = alarmIntent(spec.id)
            .putExtra(EXTRA_REVISION, revision)
            .putExtra(EXTRA_TRIGGER_AT, next.toEpochMilli())
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pendingIntent)
    }

    private fun alarmIntent(id: String): Intent = Intent(appContext, ReminderReceiver::class.java).apply {
        action = ACTION_REMINDER
        // PendingIntent identity includes the full encoded id, avoiding String.hashCode collisions.
        data = Uri.Builder().scheme("glucoday").authority("reminder").appendPath(id).build()
    }

    private fun cancelAlarm(id: String) {
        PendingIntent.getBroadcast(
            appContext, 0, alarmIntent(id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun readSpecs(): List<ReminderSpec> = try {
        val array = JSONArray(preferences.getString(KEY_SPECS, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val hour = item.optInt("hour", -1)
            val minute = item.optInt("minute", -1)
            if (id.isBlank() || hour !in 0..23 || minute !in 0..59) null
            else ReminderSpec(id, "", hour, minute, item.optBoolean("enabled", false))
        }.distinctBy { it.id }
    } catch (_: Exception) {
        emptyList()
    }

    private fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Локальные напоминания по вашему расписанию"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun nextRevision(): Long = preferences.getLong(KEY_REVISION, 0L).let {
        if (it == Long.MAX_VALUE) 1L else it + 1L
    }

    private fun deliveryKey(id: String) = "delivered:$id"

    companion object {
        const val CHANNEL_ID = "daily_reminders"
        const val TIMING_NOTICE = "Время приблизительное: Android и энергосбережение POCO могут задержать уведомление. После принудительной остановки снова откройте GlucoDay."
        private const val PREFERENCES = "local_reminder_schedule"
        private const val KEY_SPECS = "specs"
        private const val KEY_REVISION = "revision"
        private const val ACTION_REMINDER = "dev.chessman.glucoday.action.REMINDER"
        private const val EXTRA_REVISION = "schedule_revision"
        private const val EXTRA_TRIGGER_AT = "trigger_at"
        private const val NOTIFICATION_ID = 1
        private val LOCK = Any()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler(context).deliver(intent)
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )
        ) {
            ReminderScheduler(context).rescheduleAll()
        }
    }
}
