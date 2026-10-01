package com.budgeting.me.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Countdown & tanggal target Goal. Format ISO: "yyyy-MM-dd". */
object DateCountdownUtil {

    private val idFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID"))

    /** "2026-12-09" -> "09 Des 2026". Null/blank -> null. */
    fun formatDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return try {
            LocalDate.parse(iso).format(idFormat)
        } catch (e: Exception) {
            null
        }
    }

    /** ISO target -> label countdown Indonesia. Null/blank -> null. */
    fun countdownLabel(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val target = try {
            LocalDate.parse(iso)
        } catch (e: Exception) {
            return null
        }
        val days = ChronoUnit.DAYS.between(LocalDate.now(), target)
        return when {
            days == 0L -> "Hari ini!"
            days < 0 -> "Sudah lewat ${-days} hari"
            days < 7 -> "$days hari lagi"
            else -> {
                val weeks = days / 7
                "${weeks} minggu (${days % 7} hari) lagi"
            }
        }
    }
}