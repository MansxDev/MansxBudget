package com.budgeting.me.util

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/** Hasil parse satu notifikasi bank/e-wallet. */
data class ParsedBankNotif(
    val amount: Long,
    val kind: String, // "IN" | "OUT"
    val bank: String,
    val raw: String,
    val pkg: String
)

/** Kandidat sumber bank baru (auto-learn) dari package yang belum dikenal. */
data class UnknownBankCandidate(
    val pkg: String,
    val label: String, // label aplikasi dari PackageManager
    val raw: String,
    val parsedAmount: Long,
    val parsedKind: String
)

/**
 * Parse notifikasi bank/e-wallet jadi nominal + jenis transaksi.
 *
 * Semua parsing defensif: input aneh / gagal -> null (skip, tanpa crash).
 */
object BankNotifParser {

    const val KIND_IN = "IN"
    const val KIND_OUT = "OUT"

    private val AMOUNT_REGEX =
        Regex("(?:Rp|IDR)\\s?([\\d.]{3,})", RegexOption.IGNORE_CASE)

    // Kredit dicek lebih dulu: pola "masuk/diterima" lebih spesifik dari "transaksi".
    // "terima" mencakup "menerima" maupun "diterima".
    private val CREDIT_KEYWORDS =
        listOf("kredit", "masuk", "diterima", "menerima", "terima", "top up", "topup", "transfer masuk")
    private val DEBIT_KEYWORDS =
        listOf("debit", "keluar", "pembayaran", "transaksi", "purchase", "berhasil")

    /** Kata kunci pola bank untuk heuristic auto-learn (butuh >= 2). */
    private val LEARN_KEYWORDS =
        listOf("transaksi", "debit", "kredit", "saldo", "transfer", "berhasil", "pembayaran")

    /**
     * @return hasil parse, atau null kalau bukan sumber dikenal / tidak ada nominal.
     */
    fun parse(ctx: Context, pkgName: String?, title: String?, text: String?): ParsedBankNotif? {
        if (pkgName.isNullOrBlank() || text.isNullOrBlank()) return null
        val bank = BankWhitelist.findName(ctx, pkgName)
            ?: BankWhitelist.findNameByTitle(title)
            ?: return null
        val merged = if (title.isNullOrBlank()) text else "$title $text"
        return try {
            val amount = pickAmount(merged) ?: return null
            ParsedBankNotif(
                amount = amount,
                kind = detectKind(merged),
                bank = bank,
                raw = merged,
                pkg = pkgName
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Heuristic auto-learn: notif berpola bank dari package yang belum dikenal.
     * Trigger kalau SEMUA terpenuhi: listener enabled, package asing, ada nominal Rp,
     * >= 2 kata kunci bank, user belum dismiss package ini dalam 7 hari.
     */
    fun detectUnknown(
        ctx: Context, pkgName: String?, title: String?, text: String?
    ): UnknownBankCandidate? {
        if (pkgName.isNullOrBlank() || text.isNullOrBlank()) return null
        if (!isListenerEnabled(ctx)) return null
        if (BankWhitelist.isKnown(ctx, pkgName)) return null
        if (BankWhitelist.findNameByTitle(title) != null) return null
        val merged = if (title.isNullOrBlank()) text else "$title $text"
        return try {
            val amount = pickAmount(merged) ?: return null
            val lower = merged.lowercase()
            val hits = LEARN_KEYWORDS.count { lower.contains(it) }
            if (hits < 2) return null
            if (!BankWhitelist.shouldOfferLearn(ctx, pkgName)) return null
            UnknownBankCandidate(
                pkg = pkgName,
                label = appLabel(ctx, pkgName),
                raw = merged,
                parsedAmount = amount,
                parsedKind = detectKind(merged)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isListenerEnabled(ctx: Context): Boolean {
        return try {
            NotificationManagerCompat.getEnabledListenerPackages(ctx)
                .contains(ctx.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ambil nominal yang PALING DEKAT dengan kata kunci debit/kredit.
     * Contoh "saldo Rp500.000, transaksi Rp50.000" -> 50.000 (dekat "transaksi").
     * Satu match / tanpa keyword -> match pertama.
     */
    private fun pickAmount(merged: String): Long? {
        val matches = AMOUNT_REGEX.findAll(merged).toList()
        if (matches.isEmpty()) return null
        if (matches.size == 1) return toLong(matches[0])
        val lower = merged.lowercase()
        val kwPositions = (CREDIT_KEYWORDS + DEBIT_KEYWORDS).flatMap { kw ->
            val out = mutableListOf<Int>()
            var from = 0
            while (true) {
                val i = lower.indexOf(kw, from)
                if (i < 0) break
                out.add(i)
                from = i + kw.length
            }
            out
        }
        if (kwPositions.isEmpty()) return toLong(matches[0])
        return matches.minByOrNull { m ->
            val start = m.range.first
            kwPositions.minOf { kotlin.math.abs(start - it) }
        }?.let { toLong(it) }
    }

    private fun toLong(m: MatchResult): Long? {
        return m.groupValues.getOrNull(1)?.replace(".", "")?.toLongOrNull()
    }

    private fun detectKind(merged: String): String {
        val lower = merged.lowercase()
        return when {
            CREDIT_KEYWORDS.any { lower.contains(it) } -> KIND_IN
            DEBIT_KEYWORDS.any { lower.contains(it) } -> KIND_OUT
            else -> KIND_OUT
        }
    }

    private fun appLabel(ctx: Context, pkg: String): String {
        return try {
            val pm = ctx.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
        } catch (e: Exception) {
            pkg
        }
    }
}