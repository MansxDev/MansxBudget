package com.budgeting.me.util

import java.util.ArrayList
import java.util.Calendar
import java.util.Locale

/**
 * Grouping ribuan manual, stdlib murni TANPA ICU/NumberFormat device.
 * Deterministik di semua ROM (penyebab "5000.000" di MIUI sudah dibuang total).
 *   "5000000" -> "5.000.000" ; "0" -> "0"
 */
fun groupThousands(digits: String): String {
    val clean = digits.trimStart('0').ifEmpty { "0" }
    if (clean.length <= 3) return clean
    return clean.reversed().chunked(3).joinToString(".").reversed()
}

/** Input field: angka utuh tanpa desimal ("5.000.000"), dipakai live-watcher. */
fun formatRpInput(value: Long): String {
    val s = value.toString()
    return if (s.startsWith("-")) "-" + groupThousands(s.drop(1)) else groupThousands(s)
}

/** Date + currency helpers. Dates in DB are ISO "yyyy-MM-dd"; display is Indonesian. */
object FormatHelper {

    private val MONTH_ID = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    fun todayIso(): String {
        val c = Calendar.getInstance()
        return iso(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun iso(y: Int, m: Int, d: Int): String {
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    fun toDisplay(isoDate: String?): String {
        if (isoDate == null || isoDate.length < 10) return isoDate ?: ""
        try {
            val y = isoDate.substring(0, 4).toInt()
            val m = isoDate.substring(5, 7).toInt()
            val d = isoDate.substring(8, 10).toInt()
            return "$d ${MONTH_ID[m - 1]} $y"
        } catch (e: Exception) {
            return isoDate
        }
    }

    /** Best-effort parse of legacy display dates ("22 Sep 2026", EN or ID month names). */
    fun parseDisplayToIso(display: String?): String? {
        if (display == null) return null
        val p = display.trim().split("\\s+".toRegex())
        if (p.size != 3) return null
        try {
            val d = p[0].toInt()
            val m = monthFromName(p[1])
            val y = p[2].toInt()
            if (m < 1 || y < 1900) return null
            return iso(y, m, d)
        } catch (e: Exception) {
            return null
        }
    }

    private fun monthFromName(name: String): Int {
        return when (name.lowercase(Locale.US)) {
            "jan", "januari", "january" -> 1
            "feb", "februari", "february" -> 2
            "mar", "maret", "march" -> 3
            "apr", "april" -> 4
            "mei", "may" -> 5
            "jun", "juni", "june" -> 6
            "jul", "juli", "july" -> 7
            "agu", "agustus", "aug", "august" -> 8
            "sep", "sept", "september" -> 9
            "okt", "oktober", "oct", "october" -> 10
            "nov", "november" -> 11
            "des", "desember", "dec", "december" -> 12
            else -> -1
        }
    }

    fun formatRupiah(amount: Double): String {
        val neg = amount < 0
        val abs = if (neg) -amount else amount
        var whole = abs.toLong()
        var cents = Math.round((abs - whole) * 100).toInt()
        if (cents == 100) {
            whole += 1
            cents = 0
        }
        val centsStr = if (cents < 10) "0$cents" else cents.toString()
        return (if (neg) "Rp-" else "Rp") + groupThousands(whole.toString()) + "." + centsStr
    }

    /** Legacy tanpa desimal (dipakai hanya bila dibutuhkan). */
    fun formatRupiahWhole(amount: Double): String {
        return "Rp " + formatRpInput(Math.round(amount))
    }

    /** Compact for charts: 1,5 jt / 250 rb / 2,3 M. */
    fun formatCompact(amount: Double): String {
        val a = Math.abs(amount)
        if (a >= 1_000_000_000) return trim(amount / 1_000_000_000) + " M"
        if (a >= 1_000_000) return trim(amount / 1_000_000) + " jt"
        if (a >= 1_000) return trim(amount / 1_000) + " rb"
        return Math.round(amount).toString()
    }

    private fun trim(v: Double): String {
        if (v == Math.rint(v)) return v.toLong().toString()
        return String.format(Locale.US, "%.1f", v).replace('.', ',')
    }

    fun yearMonthNow(): String = todayIso().substring(0, 7)

    fun shortMonth(yearMonth: String): String {
        try {
            val m = yearMonth.substring(5, 7).toInt()
            return MONTH_ID[m - 1]
        } catch (e: Exception) {
            return yearMonth
        }
    }

    /** Last n months including current, oldest-first, as "yyyy-MM". */
    fun lastMonths(n: Int): List<String> {
        val out = ArrayList<String>()
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, -(n - 1))
        for (i in 0 until n) {
            out.add(iso(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, 1).substring(0, 7))
            c.add(Calendar.MONTH, 1)
        }
        return out
    }

    fun daysLeftInMonth(): Int {
        val c = Calendar.getInstance()
        val max = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        return max - c.get(Calendar.DAY_OF_MONTH) + 1
    }

    /** Days elapsed in current month (today included, 1-based). */
    fun daysElapsedInMonth(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    /** Total days in current month. */
    fun daysInMonth(): Int = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)

    /** [thisStart, thisEnd, prevStart, prevEnd] covering last 7d vs previous 7d. */
    fun weekWindows(): Array<String> {
        val c = Calendar.getInstance()
        val end = isoOf(c)
        c.add(Calendar.DAY_OF_MONTH, -6)
        val start = isoOf(c)
        c.add(Calendar.DAY_OF_MONTH, -1)
        val prevEnd = isoOf(c)
        c.add(Calendar.DAY_OF_MONTH, -6)
        val prevStart = isoOf(c)
        return arrayOf(start, end, prevStart, prevEnd)
    }

    private fun isoOf(c: Calendar): String {
        return iso(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Add days to an ISO date, returns ISO. */
    fun addDays(isoDate: String, days: Int): String {
        try {
            val c = Calendar.getInstance()
            c.set(
                isoDate.substring(0, 4).toInt(),
                isoDate.substring(5, 7).toInt() - 1,
                isoDate.substring(8, 10).toInt()
            )
            c.add(Calendar.DAY_OF_MONTH, days)
            return isoOf(c)
        } catch (e: Exception) {
            return isoDate
        }
    }
}
