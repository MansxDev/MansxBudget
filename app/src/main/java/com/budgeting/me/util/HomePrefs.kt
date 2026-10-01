package com.budgeting.me.util

import android.content.Context

/** Preferensi tampilan Beranda: widget yang aktif + horizon kalender mini. */
object HomePrefs {

    const val W_SALDO = "saldo"
    const val W_QUICK = "quick"
    const val W_PLAN = "plan"
    const val W_CALENDAR = "calendar"
    const val W_CHART = "chart"
    const val W_BUDGET = "budget"
    const val W_LAST_TRX = "last_trx"

    /** Urutan default tampilan di Beranda. */
    val ALL: List<String> = listOf(W_SALDO, W_QUICK, W_PLAN, W_CALENDAR, W_CHART, W_BUDGET, W_LAST_TRX)

    private const val PREFS = "home_prefs"
    private const val KEY_WIDGETS = "widgets_enabled"
    private const val KEY_HORIZON = "cal_horizon"

    /** Kunci kosong/NOL -> treat semua widget aktif. */
    fun enabled(ctx: Context): Set<String> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_WIDGETS)) return ALL.toSet()
        return p.getStringSet(KEY_WIDGETS, emptySet()) ?: emptySet()
    }

    fun setEnabled(ctx: Context, set: Set<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_WIDGETS, LinkedHashSet(set)).apply()
    }

    fun calendarHorizonDays(ctx: Context): Int {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getInt(KEY_HORIZON, 30).coerceIn(7, 90)
    }

    fun setCalendarHorizon(ctx: Context, d: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_HORIZON, d.coerceIn(7, 90)).apply()
    }

    fun resetToDefault(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_WIDGETS).remove(KEY_HORIZON).apply()
    }
}