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
import com.budgeting.me.data.DatabaseHelper
import java.util.Calendar

/** Runs once a day: inserts due recurring templates, then reschedules itself. */
class RecurringReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val db = DatabaseHelper(context)
        var inserted = 0
        try {
            inserted = db.processDueRecurring(FormatHelper.todayIso())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (inserted > 0) notifyInserted(context, inserted)
        schedule(context)
    }

    private fun notifyInserted(context: Context, count: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID,
                "Transaksi Berulang Otomatis", NotificationManager.IMPORTANCE_DEFAULT)
            nm?.createNotificationChannel(ch)
        }
        val main = Intent(context, MainActivity::class.java)
        main.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pi = PendingIntent.getActivity(context, 0, main,
            PendingIntent.FLAG_UPDATE_CURRENT or ReminderReceiver.immutableFlag())
        val b: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        b.setSmallIcon(R.drawable.logo_mansxdev)
            .setContentTitle("Transaksi berulang ditambahkan")
            .setContentText(context.getString(R.string.recurring_added_day, count))
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
        const val CHANNEL_ID = "budgeting_recurring_channel"
        const val NOTIFICATION_ID = 1002
        private const val REQUEST_CODE = 5002

        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
                ?: return
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, 7)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            if (c.before(Calendar.getInstance())) c.add(Calendar.DATE, 1)
            val i = Intent(context, RecurringReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, i,
                PendingIntent.FLAG_UPDATE_CURRENT or ReminderReceiver.immutableFlag())
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.timeInMillis,
                AlarmManager.INTERVAL_DAY, pi)
        }
    }
}
