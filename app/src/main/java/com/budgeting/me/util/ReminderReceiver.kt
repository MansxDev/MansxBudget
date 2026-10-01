package com.budgeting.me.util

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.budgeting.me.MainActivity
import com.budgeting.me.R
import java.util.Calendar

/** Daily reminder helpers + receiver entry point (kept compatible with old installs). */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Pengingat Catat Transaksi",
                NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Notifikasi harian pengingat mencatat transaksi keuangan"
            nm?.createNotificationChannel(ch)
        }
        val main = Intent(context, MainActivity::class.java)
        main.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pi = PendingIntent.getActivity(context, 0, main,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
        val b: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        b.setSmallIcon(R.drawable.logo_mansxdev)
            .setContentTitle(context.getString(R.string.reminder_notif_title))
            .setContentText(context.getString(R.string.reminder_notif_body))
            .setAutoCancel(true)
            .setContentIntent(pi)
        if (nm != null) {
            try {
                nm.notify(NOTIFICATION_ID, b.build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "budgeting_reminder_channel"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE = 5001

        fun scheduleDaily(context: Context, hour: Int, minute: Int) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
                ?: return
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, hour)
            c.set(Calendar.MINUTE, minute)
            c.set(Calendar.SECOND, 0)
            if (c.before(Calendar.getInstance())) c.add(Calendar.DATE, 1)
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.timeInMillis,
                AlarmManager.INTERVAL_DAY, pending(context))
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
            am?.cancel(pending(context))
        }

        private fun pending(context: Context): PendingIntent {
            val i = Intent(context, ReminderReceiver::class.java)
            return PendingIntent.getBroadcast(context, REQUEST_CODE, i,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
        }

        fun immutableFlag(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        }
    }
}
