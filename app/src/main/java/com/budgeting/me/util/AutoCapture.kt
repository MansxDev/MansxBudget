package com.budgeting.me.util

import com.budgeting.me.data.DatabaseHelper

/**
 * Auto-capture: mengubah isi notifikasi bank/e-wallet menjadi keputusan
 * `AUTO` (tulis langsung ke DB), `REVIEW` (antrean, tidak hilang), atau `DROP`.
 *
 * Murni lokal. Tidak ada panggilan jaringan, tidak ada model yang diunduh,
 * tidak ada tabel DB baru. Kolom yang dipakai sudah ada di DB v5:
 * - `is_auto` = 1  -> transaksi hasil auto-capture (bisa disaring)
 * - `title`       -> "{sumber} - {merchant} - {cuplikan notif}"
 *
 * Tahapan (ikut "baca ini.txt" minus bagian server):
 *  1. split notif gabungan jadi baris, tiap baris dinilai sendiri
 *  2. drop keras: OTP / keamanan, snapshot saldo, link
 *  3. drop keras: kata ajakan promo
 *  4. wajib ada pola transaksi + nominal Rp/IDR
 *  5. tentukan arah (IN/OUT) dan merchant, petakan ke kategori existing
 *  6. confidence score -> AUTO / REVIEW / DROP
 */
object AutoCapture {

    /** Skor >= ini: tulis langsung ke DB. */
    const val SCORE_AUTO = 60

    /** Skor >= ini (dan < SCORE_AUTO): masuk antrean review. */
    const val SCORE_REVIEW = 35

    const val DECISION_AUTO = "AUTO"
    const val DECISION_REVIEW = "REVIEW"
    const val DECISION_DROP = "DROP"

    const val KIND_IN = BankNotifParser.KIND_IN
    const val KIND_OUT = BankNotifParser.KIND_OUT

    /** Kategori fallback. WAJIB salah satu dari TransactionSheet.DEFAULT_CATEGORIES. */
    private const val FALLBACK_CATEGORY = "Lainnya"

    /** Satu hasil evaluasi satu baris notifikasi. */
    data class Candidate(
        val score: Int,
        val decision: String,
        val amount: Long,
        val kind: String, // KIND_IN | KIND_OUT | "" kalau arah tidak pasti
        val category: String,
        val merchant: String?, // merchant / tujuan / pengirim
        val title: String, // judul siap simpan
        val raw: String, // baris notif mentah
        val bank: String, // nama sumber
        val reason: String
    )

    // ------------------------------------------------------------------
    // Layer 2+3: penolakan keras
    // ------------------------------------------------------------------

    /** OTP / keamanan. Kalau ada, tidak mungkin transaksi. */
    private val SECURITY = listOf(
        "otp", "kode verifikasi", "kode otp", "verification code", "verifikasi kode",
        "password", "kata sandi", "pin baru", "jwt", "token anda", "access token",
        "jangan bagikan", "jangan dibagikan", "rahasia", "rahasiah",
        "one time password", "one-time password", "verifikasi identitas",
        "login dari", "login berhasil di", "password diubah", "pin kamu"
    )

    /** Snapshot saldo / pengingat / info, bukan transaksi. */
    private val NON_TX = listOf(
        "saldo anda", "saldo akhir", "saldo awal", "sisa saldo", "saldo saat ini",
        "saldo tersedia", "total saldo", "cek saldo", "saldo anda saat ini",
        "mutasi rekening", "mutasi transaksi", "daftar mutasi", "cek mutasi",
        "akan jatuh tempo", "jatuh tempo", "tagihan akan",
        "pengingat", "reminder", "peringatan",
        "sedang berjalan", "transaksi berjalan", "pencairan berjalan",
        "limit kamu", "batas transaksi", "kartu kamu", "kartu anda",
        // Pembatalan: tidak boleh dicatat sebagai transaksi baru.
        "dibatalkan", "pembatalan", "canceled", "cancelled", "refund diproses",
        // Fail / pending: uang belum benar-benar keluar atau masuk.
        "gagal", "failed", "ditolak", "declined", "pending", "menunggu persetujuan"
    )

    /** Kata ajakan promo. >= 2 kata -> bukan transaksi. */
    private val IMPERATIVE = listOf(
        "dapatkan", "nikmati", "gratis", "klik", "buruan", "terbatas",
        "undang", "referral", "promo", "diskon", "cashback", "voucher",
        "hadiah", "menangkan", "berlaku sampai", "syarat", "ketentuan",
        "ayo", "segera", "jangan lewatkan", "kepo", "cek sekarang",
        "yuk", "ayo cek", "khusus", "potongan", "langganan sekarang"
    )

    private val URL_HINT = listOf("http://", "https://", "bit.ly", "bitly", "wa.me", "t.co")

    private const val TAG = "AutoCapture"

    /** Kata penguat bahwa angka di notif memang nominal uang. */
    private val MONEY_HINT = listOf("rp", "idr", "rupiah", "nominal", "sebesar", "senilai")

    // ------------------------------------------------------------------
    // Layer 1: pola transaksi (wajib match)
    // ------------------------------------------------------------------

    /**
     * Frasa transaksi berpasti tense. Promo hampir selalu memakai kata ajakan,
     * jadi frasa di sini tidak akan kena notif promosi.
     */
    private val TX_STRONG = listOf(
        "transfer masuk", "transfer keluar", "transfer dari", "transfer ke",
        "transaksi debit", "transaksi kredit", "debit transaksi", "kredit transaksi",
        "pembayaran", "pembelian", "top up", "topup", "isi ulang", "isi saldo",
        "tarik tunai", "pencairan", "cairkan", "mencairkan", "setor",
        "dibayarkan", "diterima", "diteruskan", "dibayarkan ke", "pembayaran ke",
        "menerima", "terima", "kamu menerima", "pesanan",
        "withdrawal", "cash out", "cashout", "purchase", "payment"
    )

    /** Kata transaksi umum (skor lebih kecil, sendirian tidak cukup). */
    private val TX_WEAK = listOf(
        "transaksi", "trx", "debit", "kredit", "mutasi", "bayar", "biaya",
        "berhasil", "sukses", "selesai", "terproses", "berhasil dilakukan"
    )

    // ------------------------------------------------------------------
    // Arah transaksi
    // ------------------------------------------------------------------

    /**
     * Penanda arah yang eksplisit. "dari" SENGAJA tidak ada di sini: pada
     * notif bank "dari" muncul di transfer masuk maupun keluar, jadi tidak
     * boleh dipakai sebagai sinyal arah.
     */
    private val OUT_STRONG = listOf(
        "debit", "debet", "transfer keluar", "transfer ke", "dari rekening ke",
        "pembayaran", "pembelian", "belanja", "bayar", "biaya", "tarif",
        "tarik tunai", "pencairan", "cairkan", "mencairkan", "setor",
        "top up", "topup", "isi ulang", "isi saldo", "withdrawal", "withdraw",
        "cash out", "cashout", "purchase", "payment", "debit sebesar",
        "pesanan", "checkout", "berlansur", "cicilan", "kartu kredit"
    )

    private val IN_STRONG = listOf(
        "kredit", "credit", "transfer masuk", "transfer dari",
        "diterima", "menerima", "terima", "masuk", "incoming",
        "refund", "pengembalian", "reembolso", "rebate", "cashback",
        "saldo bertambah", "dana masuk"
    )

    // ------------------------------------------------------------------
    // Regex
    // ------------------------------------------------------------------

    private val AMOUNT_REGEX = Regex("(?:Rp|IDR)\\s?([\\d.]{3,})", RegexOption.IGNORE_CASE)

    private val TIME_REGEX =
        Regex("\\b([01]?\\d|2[0-3])[:.][0-5]\\d\\b|\\b\\d{1,2}[/-]\\d{1,2}\\b")

    /** Kata kunci untuk memilih nominal terdekat kalau notif punya >1 nominal. */
    private val AMOUNT_ANCHORS = (TX_STRONG + TX_WEAK + OUT_STRONG + IN_STRONG).distinct()

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    /**
     * Pecah notif jadi baris yang dinilai independen.
     * Judul ikut jadi baris sendiri karena sering memuat sumber/label bank.
     * Pemisah: newline, bullet, pipe, titik koma, dan " - ".
     */
    fun splitLines(title: String?, text: String?): List<String> {
        val raw = StringBuilder()
        if (!title.isNullOrBlank()) raw.append(title).append('\n')
        if (!text.isNullOrBlank()) raw.append(text)
        return raw.toString()
            .split('\n', '\r', '•', '·', '|', ';')
            .flatMap { chunk -> chunk.split(" - ", " – ", " — ") }
            .map { it.trim().trim('-', '–', '—', '*') }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Nilai semua baris satu notifikasi.
     * Hasil memuat kandidat DROP juga (untuk keperluan log/diagram), jadi
     * pemanggil WAJIB menyaring `decision != DECISION_DROP`.
     */
    fun evaluate(title: String?, text: String?, bank: String, trustedSource: Boolean): List<Candidate> {
        val out = ArrayList<Candidate>()
        for (line in splitLines(title, text)) {
            out.add(evaluateLine(line, bank, trustedSource))
        }
        android.util.Log.d(
            TAG,
            "evaluate bank=$bank trusted=$trustedSource title=[${title}] text=[${text}] " +
                "-> " + out.joinToString(" ; ") {
                    "[${it.decision}|${it.reason}|${it.amount}|${it.raw}]"
                }
        )
        return out
    }

    /** Nilai satu baris. Tidak pernah null; kasus "bukan transaksi" jadi DROP. */
    fun evaluateLine(line: String, bank: String, trustedSource: Boolean): Candidate {
        val lower = line.lowercase()

        // --- Layer 3a: OTP / keamanan ---
        if (SECURITY.any { hasWord(lower, it) }) return drop(line, bank, "OTP / keamanan")

        // --- Layer 3b: snapshot saldo / info ---
        if (NON_TX.any { lower.contains(it) }) return drop(line, bank, "info non-transaksi")

        // --- Layer 2: promo ---
        val imperativeHits = IMPERATIVE.count { hasWord(lower, it) }
        if (imperativeHits >= 2) return drop(line, bank, "kata ajakan promo")

        // --- Layer 3c: link ---
        if (URL_HINT.any { lower.contains(it) }) return drop(line, bank, "mengandung link")

        // --- Layer 1: wajib pola transaksi ---
        val strongHits = TX_STRONG.count { hasWord(lower, it) }
        val weakHits = TX_WEAK.count { hasWord(lower, it) }
        if (strongHits == 0 && weakHits == 0) return drop(line, bank, "bukan pola transaksi")

        // --- nominal ---
        val amount = pickAmount(line)
        if (amount == null || amount <= 0L) return drop(line, bank, "tidak ada nominal")

        // --- arah ---
        val outHits = OUT_STRONG.count { hasWord(lower, it) }
        val inHits = IN_STRONG.count { hasWord(lower, it) }
        val kind = detectKind(outHits, inHits)

        // Nominal absurd (kurang dari Rp1.000) tanpa arah & tanpa merchant -> jangan dicatat.
        val merchant = extractMerchant(line)
        if (amount < 1000L && kind.isEmpty() && merchant == null) {
            return drop(line, bank, "nominal tidak masuk akal")
        }

        // --- confidence score ---
        var score = 0
        if (strongHits > 0) score += 35
        if (weakHits > 0) score += 12
        if (AMOUNT_REGEX.containsMatchIn(line)) score += 25
        if (kind.isNotEmpty()) score += 15
        if (TIME_REGEX.containsMatchIn(line)) score += 10
        if (merchant != null) score += 10
        if (trustedSource) score += 10
        if (MONEY_HINT.any { lower.contains(it) }) score += 5
        score -= imperativeHits * 25
        score = score.coerceIn(0, 100)

        // Arah tidak pasti tidak boleh masuk jalur AUTO.
        val effective = if (kind.isEmpty()) minOf(score, SCORE_AUTO - 1) else score

        val decision = when {
            effective >= SCORE_AUTO -> DECISION_AUTO
            effective >= SCORE_REVIEW -> DECISION_REVIEW
            else -> DECISION_DROP
        }
        if (decision == DECISION_DROP) return drop(line, bank, "skor terlalu rendah ($effective)")

        val category = CategoryMatcher.matchCategory(merchant) ?: FALLBACK_CATEGORY
        val reason = buildString {
            append("skor ").append(effective)
            if (kind.isEmpty()) append(", arah belum pasti")
            if (merchant == null) append(", merchant tidak dikenal")
        }

        return Candidate(
            score = effective,
            decision = decision,
            amount = amount,
            kind = kind,
            category = category,
            merchant = merchant,
            title = buildTitle(bank, merchant, line),
            raw = line,
            bank = bank,
            reason = reason
        )
    }

    /**
     * Arah transaksi dari hitungan penanda.
     * OUT menang kalau penanda OUT lebih banyak DAN penanda IN nol, supaya
     * "transfer masuk" tidak salah jadi OUT karena mengandung kata "transfer".
     */
    fun detectKind(outHits: Int, inHits: Int): String {
        return when {
            inHits > 0 && inHits >= outHits -> KIND_IN
            outHits > 0 -> KIND_OUT
            else -> ""
        }
    }

    /**
     * Ambil nominal paling dekat kata kunci transaksi.
     * "Saldo Rp1.500.000 transaksi Rp50.000" -> 50.000.
     */
    fun pickAmount(line: String): Long? {
        val matches = AMOUNT_REGEX.findAll(line).toList()
        if (matches.isEmpty()) return null
        if (matches.size == 1) return toLong(matches[0])
        val lower = line.lowercase()
        val anchors = indicesOfAny(lower, AMOUNT_ANCHORS)
        if (anchors.isEmpty()) return toLong(matches[0])
        return matches.minByOrNull { m ->
            val start = m.range.first
            anchors.minOf { kotlin.math.abs(start - it) }
        }?.let { toLong(it) }
    }

    /**
     * Nama merchant/pengirim/penerima.
     * Jalur 1: merchant yang dikenal (CategoryMatcher, paling akurat).
     * Jalur 2: pola "dari/terima X", "ke/kepada X", "an X".
     * Jalur 3: prep "di/ke/at/pada" (pola yang sudah dipakai BankNotifParser).
     */
    fun extractMerchant(line: String): String? {
        CategoryMatcher.findMerchant(line)?.let { return it }
        val patterns = listOf(
            Regex("(?:terima|dari)\\s+([A-Z][\\w\\s.'/-]{1,30})"),
            Regex("(?:kepada|ke)\\s+([A-Z][\\w\\s.'/-]{1,30})"),
            Regex("(?:pengirim|penerima)\\s*:?\\s*([A-Z][\\w\\s.'/-]{1,30})"),
            Regex("\\ban\\s+([A-Z][\\w\\s.'/-]{1,30})")
        )
        for (p in patterns) {
            val m = p.find(line) ?: continue
            val cand = m.groupValues.getOrNull(1) ?: continue
            val clean = cleanParty(cand) ?: continue
            return clean
        }
        return null
    }

    /** Judul transaksi: "{sumber} - {merchant} - {cuplikan}". */
    fun buildTitle(bank: String, merchant: String?, line: String): String {
        val b = bank.trim()
        val m = merchant?.trim().orEmpty()
        val snippet = line.trim().take(70)
        return listOf(b, m, snippet)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
    }

    /** Ubah jenis internal ke nilai `type` yang disimpan di DB. */
    fun toDbType(kind: String): String =
        if (kind == KIND_IN) DatabaseHelper.TYPE_INCOME else DatabaseHelper.TYPE_EXPENSE

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    private fun drop(line: String, bank: String, reason: String): Candidate = Candidate(
        score = 0,
        decision = DECISION_DROP,
        amount = 0L,
        kind = "",
        category = "",
        merchant = null,
        title = line,
        raw = line,
        bank = bank,
        reason = reason
    )

    /** Kata penutup yang bukan bagian nama merchant. */
    private val PARTY_STOP = setOf(
        "berhasil", "selesai", "sukses", "transaksi", "pembayaran", "debit",
        "kredit", "saldo", "tanggal", "waktu", "lagi", "saja", "kembali",
        "rp", "idr", "via", "dan", "untuk", "dengan", "sebesar", "sejumlah",
        "anda", "nominal", "pada", "tanggal", "jam", "rupiah", "berupa"
    )

    private fun cleanParty(raw: String): String? {
        val words = raw.split(' ', '\t')
            .map { it.trim().trim('.', ',', '!', '-', '&', '\'', '"', '(') }
            .filter { it.isNotBlank() }
        val picked = ArrayList<String>()
        for (w in words) {
            val lw = w.lowercase()
            if (w.any { it.isDigit() }) break
            if (lw.startsWith("rp") || lw.startsWith("idr")) break
            if (lw in PARTY_STOP) break
            picked.add(w)
            if (picked.size == 3) break
        }
        while (picked.isNotEmpty()) {
            val last = picked.last()
            val lastLow = last.lowercase()
            val junk = lastLow in PARTY_STOP || last.any { it.isDigit() }
                || lastLow.startsWith("rp") || lastLow.startsWith("idr")
            if (!junk) break
            picked.removeAt(picked.lastIndex)
        }
        val name = picked.joinToString(" ").trim()
        if (name.length < 2 || name.length > 30) return null
        return name
    }

    private fun indicesOfAny(lower: String, needles: List<String>): List<Int> {
        val out = ArrayList<Int>()
        for (n in needles) {
            var from = 0
            while (true) {
                val i = lower.indexOf(n, from)
                if (i < 0) break
                out.add(i)
                from = i + n.length
            }
        }
        return out
    }

    private fun toLong(m: MatchResult): Long? =
        m.groupValues.getOrNull(1)?.replace(".", "")?.toLongOrNull()

    /** Pencocokan berbasis batas kata untuk keyword pendek (<= 3 huruf). */
    private fun hasWord(lower: String, keyword: String): Boolean {
        if (keyword.length <= 3) {
            return Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(lower)
        }
        return lower.contains(keyword)
    }
}
