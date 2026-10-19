package com.budgeting.me.util

/**
 * Scoring + klasifikasi notifikasi (otak Fase 1).
 *
 * Skor (tiap kata dihitung sekali):
 *   +3 : berhasil, transaksi, debit, kredit
 *   +2 : pembayaran, transfer, top up, topup, bayar
 *        ("bayar" TIDAK dihitung kalau "pembayaran" sudah match, supaya
 *         "Pembayaran ... dibayar" tidak dapat +4 dobel)
 *   +2 : nominal >= Rp1.000
 *   +1 : ada merchant yang dikenal (CategoryMatcher)
 *   -3 : promo, diskon, voucher, cashback, info, penawaran,
 *        undian, hadiah, menang
 *   -2 : nominal ada tapi < Rp100
 *   -5 : otp, kode verifikasi, password, pin, jangan bagikan, rahasia
 *
 * Klasifikasi:
 *   skor >= 4 -> TRANSACTION
 *   skor 1..3 -> AMBIGU (tetap diproses sebagai transaksi, tapi
 *                ditandai ragu supaya rilis berikutnya bisa tanya user)
 *   skor <= 0 -> OTP kalau ada kata OTP, PROMO kalau ada kata promo,
 *                selain itu INFO
 *
 * Kata kunci <= 3 huruf ("pin", "otp") WAJIB word-boundary supaya tidak
 * match di dalam kata lain.
 */
object NotifScorer {

    const val TRANSACTION = "TRANSACTION"
    const val PROMO = "PROMO"
    const val OTP = "OTP"
    const val INFO = "INFO"
    const val AMBIGU = "AMBIGU"

    private val PLUS3 = listOf("berhasil", "transaksi", "debit", "kredit")

    private val PLUS2 =
        listOf("pembayaran", "transfer", "top up", "topup", "bayar")

    private val MINUS3 = listOf(
        "promo", "diskon", "voucher", "cashback", "info", "penawaran",
        "undian", "hadiah", "menang"
    )

    private val MINUS5 = listOf(
        "otp", "kode verifikasi", "password", "pin", "jangan bagikan", "rahasia"
    )

    /** Sama dengan MINUS5, dipakai saat kelas <= 0 untuk memastikan kelas OTP. */
    private val OTP_WORDS = MINUS5

    /** Kata promo tegas (tanpa "info") untuk kelas PROMO. */
    private val PROMO_WORDS =
        listOf("promo", "diskon", "voucher", "cashback", "undian", "hadiah")

    private val AMOUNT_REGEX =
        Regex("(?:Rp|IDR)\\s?([\\d.]{3,})", RegexOption.IGNORE_CASE)

    /** Total skor untuk satu teks gabungan (judul + isi notifikasi). */
    fun score(merged: String): Int {
        val lower = merged.lowercase()
        var s = 0

        for (w in PLUS3) {
            if (containsWord(lower, w)) s += 3
        }

        var pembayaranHit = false
        for (w in PLUS2) {
            if (w == "bayar" && pembayaranHit) continue
            if (containsWord(lower, w)) {
                s += 2
                if (w == "pembayaran") pembayaranHit = true
            }
        }

        val amount = firstAmount(merged)
        if (amount != null && amount >= 1000L) s += 2

        if (CategoryMatcher.findMerchant(merged) != null) s += 1

        for (w in MINUS3) {
            if (containsWord(lower, w)) s -= 3
        }

        if (amount != null && amount < 100L) s -= 2

        for (w in MINUS5) {
            if (containsWord(lower, w)) s -= 5
        }

        return s
    }

    /** Klasifikasi dari skor + kata kunci penegas. */
    fun classify(merged: String, score: Int): String {
        if (score >= 4) return TRANSACTION
        if (score >= 1) return AMBIGU
        val lower = merged.lowercase()
        if (OTP_WORDS.any { containsWord(lower, it) }) return OTP
        if (PROMO_WORDS.any { containsWord(lower, it) }) return PROMO
        return INFO
    }

    /** Nominal pertama (angka mentah, titik ribuan dibuang). Null kalau tidak ada. */
    fun firstAmount(merged: String): Long? {
        return try {
            AMOUNT_REGEX.find(merged)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(".", "")
                ?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Keyword pendek (<= 3 huruf, mis. "pin", "otp") wajib word-boundary
     * supaya tidak match di dalam kata lain.
     */
    private fun containsWord(lower: String, keyword: String): Boolean {
        if (keyword.length <= 3) {
            return Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(lower)
        }
        return lower.contains(keyword)
    }
}