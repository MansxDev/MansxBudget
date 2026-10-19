package com.budgeting.me.util

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Hasil parse satu notifikasi bank/e-wallet.
 *
 * Field baru (v4.7) punya nilai default supaya pemanggil lama yang hanya
 * mengisi 5 field pertama tetap bisa compile.
 *
 * - [classification]: TRANSACTION | PROMO | OTP | INFO | AMBIGU
 * - [score]: skor numerik dari NotifScorer
 * - [suggestedCategory]: kategori EXISTING hasil auto-kategorisasi merchant
 * - [merchant]: nama merchant yang terdeteksi, null kalau tidak ada
 */
data class ParsedBankNotif(
    val amount: Long,
    val kind: String, // "IN" | "OUT"
    val bank: String,
    val raw: String,
    val pkg: String,
    val classification: String = NotifScorer.TRANSACTION,
    val score: Int = 0,
    val suggestedCategory: String? = null,
    val merchant: String? = null
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
 * Parse notifikasi bank/e-wallet jadi nominal + jenis transaksi + klasifikasi.
 *
 * Semua parsing defensif: input aneh / gagal -> null (skip, tanpa crash).
 *
 * Sumber notifikasi ada 3 jalur:
 * 1. Package ada di whitelist  -> bank = nama whitelist.
 * 2. Judul persis nama bank    -> bank = judul (simulasi/umum bank).
 * 3. Fallback merchant         -> sumber asing, tapi merchant dikenal dan
 *    skornya kuat, tetap diproses dengan "bank" = judul notif.
 *
 * Klasifikasi menentukan apakah notifikasi diteruskan ke sheet konfirmasi
 * ([isTransactionLike]) atau di-skip total (PROMO / OTP / INFO).
 */
object BankNotifParser {

    const val KIND_IN = "IN"
    const val KIND_OUT = "OUT"

    /** Skor minimal supaya sumber asing + merchant dikenal tetap dicatat. */
    private const val FALLBACK_MIN_SCORE = 4

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
     * Ekstraksi merchant fallback (bukan merchant yang dikenal).
     * "Transaksi debit Rp50.000 di Alfamart" -> "Alfamart"
     * "Pembayaran ke Gojek Rp25.000"          -> "Gojek"
     * "Bayar di Kopi Kenangan Rp30.000"       -> "Kopi Kenangan"
     */
    private val PREP_REGEX = Regex(
        "\\b(?:di|ke|at|pada|dari)\\s+([A-Za-z0-9][A-Za-z0-9&'./\\- ]{1,28})",
        RegexOption.IGNORE_CASE
    )

    /** Kata bribes yang bukan bagian nama merchant ("... di Alfamart berhasil"). */
    private val MERCHANT_STOPWORDS = setOf(
        "berhasil", "selesai", "sukses", "transaksi", "pembayaran", "debit",
        "kredit", "saldo", "tanggal", "waktu", "lagi", "saja", "kembali",
        "anda", "anda.", "rp", "idr", "via", "dan", "untuk", "dengan"
    )

    /**
     * @return hasil parse lengkap (klasifikasi + merchant + kategori),
     *         atau null kalau bukan sumber dikenal / tidak ada nominal.
     */
    fun parse(ctx: Context, pkgName: String?, title: String?, text: String?): ParsedBankNotif? {
        if (pkgName.isNullOrBlank() || text.isNullOrBlank()) return null
        val merged = if (title.isNullOrBlank()) text else "$title $text"
        return try {
            val amount = pickAmount(merged) ?: return null
            val knownBank = BankWhitelist.findName(ctx, pkgName)
                ?: BankWhitelist.findNameByTitle(title)

            val score = NotifScorer.score(merged)
            val classification = NotifScorer.classify(merged, score)
            val merchant = extractMerchant(merged)
            val suggested = CategoryMatcher.matchCategory(merchant)
            val kind = detectKind(merged)

            if (knownBank != null) {
                ParsedBankNotif(
                    amount = amount,
                    kind = kind,
                    bank = knownBank,
                    raw = merged,
                    pkg = pkgName,
                    classification = classification,
                    score = score,
                    suggestedCategory = suggested,
                    merchant = merchant
                )
            } else {
                // Sumber asing: tetap diproses kalau merchant dikenal + skor kuat.
                if (!isTransactionLike(classification)) return null
                if (merchant == null || score < FALLBACK_MIN_SCORE) return null
                val label = title?.trim().orEmpty().ifBlank { appLabel(ctx, pkgName) }
                ParsedBankNotif(
                    amount = amount,
                    kind = kind,
                    bank = label,
                    raw = merged,
                    pkg = pkgName,
                    classification = classification,
                    score = score,
                    suggestedCategory = suggested,
                    merchant = merchant
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Alias eksplisit untuk pemanggil baru: sama dengan [parse], tapi
     * kontrasnya jelas bahwa hasilnya sudah enriched (klasifikasi + merchant).
     */
    fun parseExtended(
        ctx: Context, pkgName: String?, title: String?, text: String?
    ): ParsedBankNotif? = parse(ctx, pkgName, title, text)

    /**
     * Heuristic auto-learn: notif berpola bank dari package yang belum dikenal.
     * Trigger kalau SEMUA terpenuhi: listener enabled, package asing, ada nominal Rp,
     * >= 2 kata kunci bank, kelas bukan PROMO/OTP, user belum dismiss 7 hari.
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
            // Jangan ajari sumber baru dari notifikasi promosi / OTP.
            val classification = NotifScorer.classify(merged, NotifScorer.score(merged))
            if (classification == NotifScorer.PROMO || classification == NotifScorer.OTP) return null
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

    /** Hanya kelas ini yang boleh lanjut ke sheet konfirmasi. */
    fun isTransactionLike(classification: String?): Boolean {
        return classification == NotifScorer.TRANSACTION
            || classification == NotifScorer.AMBIGU
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

    /**
     * Nama merchant dari teks notif.
     * Jalur 1: merchant yang dikenal (paling akurat, dari CategoryMatcher).
     * Jalur 2: pola "di/ke/at/pada + 1..3 kata", dibersihkan dari kata
     *        penutup dan nominal ("di Alfamart berhasil" -> "Alfamart").
     */
    private fun extractMerchant(merged: String): String? {
        CategoryMatcher.findMerchant(merged)?.let { return it }
        val m = PREP_REGEX.find(merged) ?: return null
        val chunk = m.groupValues.getOrNull(1) ?: return null
        val words = chunk.trim()
            .split(' ', '\t')
            .map { it.trim().trim('.', ',', '!', '-', '&', '\'', '"') }
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        val picked = ArrayList<String>()
        for (w in words) {
            val low = w.lowercase()
            if (low in MERCHANT_STOPWORDS) break
            // Token berisi angka atau prefix nominal = bukan nama merchant.
            if (w.any { it.isDigit() }) break
            if (low.startsWith("rp") || low.startsWith("idr")) break
            picked.add(w)
            if (picked.size == 3) break
        }
        while (picked.isNotEmpty()) {
            val last = picked.last()
            val lastLow = last.lowercase()
            val junk = lastLow in MERCHANT_STOPWORDS
                || last.any { it.isDigit() }
                || lastLow.startsWith("rp")
                || lastLow.startsWith("idr")
            if (!junk) break
            picked.removeAt(picked.lastIndex)
        }
        val name = picked.joinToString(" ").trim()
        if (name.length < 2 || name.length > 30) return null
        return name
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