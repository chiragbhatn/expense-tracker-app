package io.github.chiragbhatn.expensetracker.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.chiragbhatn.expensetracker.ExpenseTrackerApp
import io.github.chiragbhatn.expensetracker.MainActivity
import io.github.chiragbhatn.expensetracker.R
import io.github.chiragbhatn.expensetracker.data.AppClock
import io.github.chiragbhatn.expensetracker.data.AppData
import io.github.chiragbhatn.expensetracker.data.SettingsRepository
import io.github.chiragbhatn.expensetracker.domain.CardMath
import io.github.chiragbhatn.expensetracker.domain.DueReminder
import io.github.chiragbhatn.expensetracker.domain.ReminderPlanner
import java.util.concurrent.TimeUnit

/** Works out which reminders are due and shows each one once as a notification. */
class ReminderNotifier(
    private val context: Context,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    fun due(data: AppData, reminderSettings: io.github.chiragbhatn.expensetracker.domain.ReminderSettings): List<DueReminder> {
        val today = clock.today()
        return ReminderPlanner.due(
            today = today,
            settings = reminderSettings,
            cardPayments = CardMath.upcomingPayments(data.cardSummaries(today), today),
            recurring = data.recurring,
            people = data.balances,
            custom = data.reminders,
        )
    }

    suspend fun notifyDue(data: AppData) {
        val reminders = settings.current().reminders
        if (!reminders.enabled || !canPost(context)) return
        val shown = settings.notifiedReminders()
        val fresh = due(data, reminders).filter { it.key !in shown }
        if (fresh.isEmpty()) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fresh.forEach { reminder ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(reminder.title)
                .setContentText(reminder.text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try {
                manager.notify(reminder.key.hashCode(), notification)
            } catch (_: SecurityException) {
                return
            }
        }
        settings.markNotified(fresh.map { it.key })
    }

    companion object {
        const val CHANNEL_ID = "reminders"

        fun canPost(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Card bills, recurring expenses and money owed to you"
                },
            )
        }
    }
}

/** Once a day: records due recurring expenses, then shows due reminders. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ExpenseTrackerApp).container
        container.recurring.recordDue()
        container.notifier.notifyDue(container.loadData())
        return Result.success()
    }

    companion object {
        private const val NAME = "daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
