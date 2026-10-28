package com.lunexa.studio.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lunexa.studio.LunexaApp
import com.lunexa.studio.MainActivity
import com.lunexa.studio.R
import com.lunexa.studio.data.AppSettings
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.PubStatus
import com.lunexa.studio.data.Publication
import com.lunexa.studio.data.TaskItem
import com.lunexa.studio.data.zoneOf
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object Reminders {
    private const val ACTION = "com.lunexa.studio.REMIND"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_KIND = "kind"
    const val KIND_PUB = "pub"
    const val KIND_TASK = "task"
    const val KIND_WEEKLY = "weekly"
    private const val WEEKLY_CODE = 3

    fun pubCode(id: Long): Int = (id * 4 + 1).toInt()
    fun taskCode(id: Long): Int = (id * 4 + 2).toInt()

    private fun intent(context: Context, code: Int) =
        Intent(context, ReminderReceiver::class.java).setAction("$ACTION.$code")

    private fun set(context: Context, code: Int, at: Long, title: String, text: String, kind: String) {
        if (at <= System.currentTimeMillis()) { cancel(context, code); return }
        val i = intent(context, code)
            .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text).putExtra(EXTRA_KIND, kind)
        val pi = PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Неточный, но надёжный будильник: не требует разрешения на точные будильники.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, code: Int) {
        val pi = PendingIntent.getBroadcast(
            context, code, intent(context, code), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    fun schedulePublication(context: Context, p: Publication, s: AppSettings) {
        val code = pubCode(p.id)
        val active = p.status == PubStatus.PLANNED || p.status == PubStatus.READY
        if (!s.notificationsEnabled || !s.notifyPublications || p.remindBeforeMin < 0 || !active) {
            cancel(context, code); return
        }
        val zone = zoneOf(s.timezone)
        val en = s.language == "en"
        val at = p.scheduledAt - p.remindBeforeMin * 60_000L
        val title = if (en) "Upcoming upload" else "Скоро публикация"
        val text = "${p.title} — ${Fmt.dateTime(p.scheduledAt, zone, s.language)}"
        set(context, code, at, title, text, KIND_PUB)
    }

    fun scheduleTask(context: Context, t: TaskItem, s: AppSettings) {
        val code = taskCode(t.id)
        val due = t.dueAt
        if (!s.notificationsEnabled || !s.notifyTasks || !t.remind || t.done || due == null) {
            cancel(context, code); return
        }
        val en = s.language == "en"
        set(context, code, due, if (en) "Task due" else "Срок задачи", t.title, KIND_TASK)
    }

    /** Еженедельное напоминание проверить статистику: понедельник, 10:00. */
    fun scheduleWeekly(context: Context, s: AppSettings) {
        val i = intent(context, WEEKLY_CODE)
        if (!s.notificationsEnabled || !s.notifyWeekly) { cancel(context, WEEKLY_CODE); return }
        val zone = zoneOf(s.timezone)
        val now = ZonedDateTime.now(zone)
        var next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).withHour(10).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val en = s.language == "en"
        i.putExtra(EXTRA_TITLE, if (en) "Weekly check" else "Еженедельная проверка")
            .putExtra(EXTRA_TEXT, if (en) "Update your channel stats and review the content plan." else "Обновите статистику канала и проверьте контент-план.")
            .putExtra(EXTRA_KIND, KIND_WEEKLY)
        val pi = PendingIntent.getBroadcast(context, WEEKLY_CODE, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java)?.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), AlarmManager.INTERVAL_DAY * 7, pi,
        )
    }
}

object Notifier {
    const val CHANNEL_ID = "lunexa_reminders"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Напоминания / Reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "LUNEXA Studio" },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lunexa)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFE11D48.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) { /* разрешение отозвано */ }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LunexaApp
        val pending = goAsync()
        app.repo.scope.launch {
            try {
                val s = app.repo.settingsNow()
                val kind = intent.getStringExtra(Reminders.EXTRA_KIND)
                val allowed = s.notificationsEnabled && when (kind) {
                    Reminders.KIND_PUB -> s.notifyPublications
                    Reminders.KIND_TASK -> s.notifyTasks
                    Reminders.KIND_WEEKLY -> s.notifyWeekly
                    else -> false
                }
                if (allowed) {
                    val id = intent.action?.substringAfterLast('.')?.toIntOrNull() ?: 0
                    Notifier.post(
                        context, id,
                        intent.getStringExtra(Reminders.EXTRA_TITLE) ?: "LUNEXA Studio",
                        intent.getStringExtra(Reminders.EXTRA_TEXT) ?: "",
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LunexaApp
        val pending = goAsync()
        app.repo.scope.launch {
            try { app.repo.rescheduleAll() } finally { pending.finish() }
        }
    }
}
