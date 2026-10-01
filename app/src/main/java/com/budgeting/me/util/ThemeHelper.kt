package com.budgeting.me.util

import android.content.Context
import android.content.SharedPreferences
import com.budgeting.me.R

object ThemeHelper {

    private const val PREF_NAME = "budgeting_prefs"

    const val KEY_THEME = "key_theme"
    const val KEY_REMINDER_ON = "key_reminder_on"
    const val KEY_REMINDER_HOUR = "key_reminder_hour"
    const val KEY_REMINDER_MIN = "key_reminder_minute"

    const val THEME_WHITE = "WHITE"
    const val THEME_DARK = "DARK"
    const val THEME_AMOLED = "AMOLED"

    fun saveTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, theme).apply()
    }

    fun getTheme(context: Context): String {
        return prefs(context).getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    }

    /** Call before super.onCreate() in activities. */
    fun resolveTheme(context: Context): Int {
        val theme = getTheme(context)
        if (THEME_WHITE.equals(theme, ignoreCase = true)) return R.style.Theme_Budgeting
        if (THEME_AMOLED.equals(theme, ignoreCase = true)) return R.style.Theme_Budgeting_Amoled
        return R.style.Theme_Budgeting_Dark
    }

    fun isDark(context: Context): Boolean {
        return !THEME_WHITE.equals(getTheme(context), ignoreCase = true)
    }

    fun setReminder(context: Context, on: Boolean, hour: Int, minute: Int) {
        prefs(context).edit()
            .putBoolean(KEY_REMINDER_ON, on)
            .putInt(KEY_REMINDER_HOUR, hour)
            .putInt(KEY_REMINDER_MIN, minute)
            .apply()
    }

    fun isReminderOn(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REMINDER_ON, false)
    }

    fun getReminderHour(context: Context): Int {
        return prefs(context).getInt(KEY_REMINDER_HOUR, 20)
    }

    fun getReminderMinute(context: Context): Int {
        return prefs(context).getInt(KEY_REMINDER_MIN, 0)
    }

    /** Fixed semantic colors that read well on both light and dark surfaces. */
    fun progressColor(context: Context, ratio: Float): Int {
        val r = context.resources
        if (ratio >= 1f) return r.getColor(R.color.danger)
        if (ratio >= 0.8f) return warningColor(context)
        return r.getColor(R.color.success)
    }

    /** Amber warning: neon on dark, deep amber on light for contrast. */
    fun warningColor(context: Context): Int {
        return if (isDark(context)) 0xFFFBBF24.toInt() else 0xFFB45309.toInt()
    }

    fun chartPalette(context: Context): IntArray {
        val r = context.resources
        return intArrayOf(
            r.getColor(R.color.chart_1),
            r.getColor(R.color.chart_2),
            r.getColor(R.color.chart_3),
            r.getColor(R.color.chart_4),
            r.getColor(R.color.chart_5),
            r.getColor(R.color.chart_6),
            r.getColor(R.color.chart_7),
            r.getColor(R.color.chart_8)
        )
    }

    fun chartTextColor(context: Context): Int {
        return if (isDark(context)) 0xFFD4DCFF.toInt() else 0xFF590925.toInt()
    }

    fun chartGridColor(context: Context): Int {
        return if (isDark(context)) 0xFF3A0718.toInt() else 0xFFE3E8FF.toInt()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}
