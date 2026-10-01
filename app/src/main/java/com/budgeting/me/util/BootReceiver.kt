package com.budgeting.me.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arm alarms after reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            RecurringReceiver.schedule(context)
            if (ThemeHelper.isReminderOn(context)) {
                ReminderReceiver.scheduleDaily(context,
                    ThemeHelper.getReminderHour(context),
                    ThemeHelper.getReminderMinute(context))
            }
        }
    }
}
