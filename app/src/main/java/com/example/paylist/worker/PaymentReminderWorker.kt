package com.example.paylist.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.paylist.R
import com.example.paylist.data.PayListDatabase
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class PaymentReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return ListenableWorker.Result.success()
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Напоминания PayList", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val today = LocalDate.now()
        val end = today.plusDays(3)
        val due = PayListDatabase.getInstance(context).paymentDao().getDueBetween(today.toString(), end.toString())
        due.forEach { item ->
            val title = "Скоро платёж: ${item.title}"
            val body = "Срок ${item.dueDate}. Осталось ${"%.2f".format(java.util.Locale.US, (item.amountMinor - item.paidMinor) / 100.0)} ${item.currency}"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(item.id.toInt(), notification)
        }
        return ListenableWorker.Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "paylist_due_payments"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PaymentReminderWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("paylist_daily_reminders", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
