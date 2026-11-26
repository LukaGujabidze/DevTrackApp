package com.devtracker.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object Reminders {
    private const val DAY = 86_400_000L
    fun schedule(c: Context, e: Event) {
        val at = e.notifyAt ?: return
        val now = System.currentTimeMillis()
        val data = workDataOf("title" to e.title, "id" to e.id)
        val wm = WorkManager.getInstance(c)
        if (e.repeatDaily) {
            var t = at; while (t < now) t += DAY
            wm.enqueueUniquePeriodicWork("ev-${e.id}", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).setInitialDelay(t - now, TimeUnit.MILLISECONDS).setInputData(data).build())
        } else if (at > now) {
            wm.enqueueUniqueWork("ev-${e.id}", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(at - now, TimeUnit.MILLISECONDS).setInputData(data).build())
        }
    }
    fun cancel(c: Context, id: Long) { WorkManager.getInstance(c).cancelUniqueWork("ev-$id") }
}

class ReminderWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        val ctx = applicationContext
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val b = Notification.Builder(ctx, "reminders").setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("DevTracker").setContentText(inputData.getString("title") ?: "Reminder").setAutoCancel(true)
        ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let { b.setContentIntent(PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE)) }
        nm.notify(inputData.getLong("id", 0L).toInt(), b.build())
        return Result.success()
    }
}
