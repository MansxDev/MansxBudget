package com.budgeting.me.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

/** Sinkronisasi event "Rencana Dana" ke Google Calendar (primary calendar). */
object CalendarSyncUtil {

    private const val TRIGGER_HOUR = 9L

    /** Insert event + reminder (days*24*60 menit). Return event id, atau null gagal. */
    fun insertEvent(
        context: Context,
        title: String,
        targetIso: String,
        reminderDays: List<Int>
    ): Long? {
        val calId = primaryCalendarId(context) ?: return null
        val startMillis = atNineMillis(targetIso) ?: return null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + 60 * 60 * 1000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            put(CalendarContract.Events.DESCRIPTION, "MansxBudget - Rencana Dana")
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return null
        val id = ContentUris.parseId(uri)
        writeReminders(context, id, reminderDays)
        return id
    }

    /** Update event & ganti total reminder-nya (hapus lama, isi baru). */
    fun updateEvent(
        context: Context,
        eventId: Long,
        title: String,
        targetIso: String,
        reminderDays: List<Int>
    ) {
        val startMillis = atNineMillis(targetIso) ?: return
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + 60 * 60 * 1000L)
        }
        context.contentResolver.update(uri, values, null, null)
        context.contentResolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID}=?",
            arrayOf(eventId.toString())
        )
        writeReminders(context, eventId, reminderDays)
    }

    /** Hapus event + reminder-nya. eventId null -> no-op. */
    fun deleteEvent(context: Context, eventId: Long?) {
        if (eventId == null) return
        context.contentResolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID}=?",
            arrayOf(eventId.toString())
        )
        context.contentResolver.delete(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            null, null
        )
    }

    private fun writeReminders(context: Context, eventId: Long, days: List<Int>) {
        for (d in days) {
            val rv = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, d * 24 * 60)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, rv)
        }
    }

    private fun primaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(1) == 1) return c.getLong(0)
            }
        }
        return null
    }

    private fun atNineMillis(iso: String): Long? {
        return try {
            LocalDate.parse(iso)
                .atStartOfDay(ZoneId.systemDefault())
                .plusHours(TRIGGER_HOUR)
                .toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}