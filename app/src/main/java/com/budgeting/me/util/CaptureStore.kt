package com.budgeting.me.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keadaan lokal auto-capture:
 *  - pengaturan (nyala/mati, ambang skor)
 *  - antrean review (hasil skor ambigu, tidak hilang)
 *  - daftar fingerprint untuk dedup notif kembar
 *
 * Semua memakai SharedPreferences. Tidak ada tabel DB baru, tidak ada jaringan.
 */
object CaptureStore {

    private const val PREFS = "auto_capture_store"
    private const val KEY_QUEUE = "review_queue"
    private const val KEY_SEEN = "seen_hashes"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_AUTO_THRESHOLD = "auto_threshold"
    private const val KEY_STATS = "stats"

    /** Batas dedup: notif kembar dalam jendela ini dianggap satu transaksi. */
    const val DEDUP_WINDOW_MS = 5 * 60 * 1000L

    private const val SEEN_LIMIT = 500
    private const val QUEUE_LIMIT = 100

    /** Default: auto-capture nyala begitu listener aktif. */
    private const val DEFAULT_ENABLED = true

    /** Satu antrean review. */
    data class Pending(
        val id: String,
        val score: Int,
        val amount: Long,
        val kind: String,
        val category: String,
        val title: String,
        val raw: String,
        val bank: String,
        val reason: String,
        val savedAt: Long
    )

    /** Angka ringkas untuk baris di Pengaturan. */
    data class Stats(
        val autoCount: Long,
        val reviewCount: Int,
        val lastTitle: String?,
        val lastAt: Long
    )

    // ---------------- pengaturan ----------------

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** Ambang skor AUTO, bisa dikunci user (40..95). */
    fun autoThreshold(ctx: Context): Int =
        prefs(ctx).getInt(KEY_AUTO_THRESHOLD, AutoCapture.SCORE_AUTO).coerceIn(40, 95)

    fun setAutoThreshold(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt(KEY_AUTO_THRESHOLD, value.coerceIn(40, 95)).apply()
    }

    // ---------------- dedup ----------------

    /**
     * Fingerprint stabil dari nominal + judul.
     * FNV-1a 64-bit: deterministik di Kotlin maupun Python, tanpa dependency.
     */
    fun fingerprint(amount: Long, title: String): String {
        val norm = buildString {
            append(abs(amount))
            append('|')
            append(title.lowercase().filter { it.isLetterOrDigit() })
        }
        return hex64(fnv1a64(norm))
    }

    /** True kalau fingerprint ini sudah diproses di dalam [DEDUP_WINDOW_MS]. */
    fun isSeen(ctx: Context, fp: String, windowMs: Long = DEDUP_WINDOW_MS): Boolean {
        val now = System.currentTimeMillis()
        val arr = readArray(ctx, KEY_SEEN)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("fp") == fp && now - o.optLong("ts", 0L) <= windowMs) return true
        }
        return false
    }

    /** Tandai fingerprint sudah diproses. Dipanggil setelah tulis/queue berhasil. */
    fun markSeen(ctx: Context, fp: String) {
        val arr = readArray(ctx, KEY_SEEN)
        val obj = JSONObject()
        obj.put("fp", fp)
        obj.put("ts", System.currentTimeMillis())
        arr.put(obj)
        while (arr.length() > SEEN_LIMIT) arr.remove(0)
        writeArray(ctx, KEY_SEEN, arr)
    }

    /** Bersihkan fingerprint lama (dipanggil saat auto-capture dimatikan). */
    fun clearSeen(ctx: Context) {
        writeArray(ctx, KEY_SEEN, JSONArray())
    }

    // ---------------- antrean review ----------------

    fun addPending(ctx: Context, c: AutoCapture.Candidate) {
        val arr = readArray(ctx, KEY_QUEUE)
        val fp = fingerprint(c.amount, c.title)
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.optString("id") == fp) return
        }
        val obj = JSONObject()
        obj.put("id", fp)
        obj.put("score", c.score)
        obj.put("amount", c.amount)
        obj.put("kind", c.kind)
        obj.put("category", c.category)
        obj.put("title", c.title)
        obj.put("raw", c.raw)
        obj.put("bank", c.bank)
        obj.put("reason", c.reason)
        obj.put("saved_at", System.currentTimeMillis())
        arr.put(obj)
        while (arr.length() > QUEUE_LIMIT) arr.remove(0)
        writeArray(ctx, KEY_QUEUE, arr)
    }

    fun getPending(ctx: Context): List<Pending> {
        val arr = readArray(ctx, KEY_QUEUE)
        val out = ArrayList<Pending>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Pending(
                    id = o.optString("id"),
                    score = o.optInt("score"),
                    amount = o.optLong("amount"),
                    kind = o.optString("kind"),
                    category = o.optString("category"),
                    title = o.optString("title"),
                    raw = o.optString("raw"),
                    bank = o.optString("bank"),
                    reason = o.optString("reason"),
                    savedAt = o.optLong("saved_at")
                )
            )
        }
        return out.sortedByDescending { it.savedAt }
    }

    fun pendingCount(ctx: Context): Int = readArray(ctx, KEY_QUEUE).length()

    /** Ambil satu antrean review dan hapus dari antrean (dipakai saat dicatat). */
    fun takePending(ctx: Context, id: String): Pending? {
        val arr = readArray(ctx, KEY_QUEUE)
        var found: Pending? = null
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") != id) continue
            found = Pending(
                id = o.optString("id"),
                score = o.optInt("score"),
                amount = o.optLong("amount"),
                kind = o.optString("kind"),
                category = o.optString("category"),
                title = o.optString("title"),
                raw = o.optString("raw"),
                bank = o.optString("bank"),
                reason = o.optString("reason"),
                savedAt = o.optLong("saved_at")
            )
            arr.remove(i)
        }
        if (found != null) writeArray(ctx, KEY_QUEUE, arr)
        return found
    }

    fun removePending(ctx: Context, id: String) {
        val arr = readArray(ctx, KEY_QUEUE)
        for (i in arr.length() - 1 downTo 0) {
            if (arr.optJSONObject(i)?.optString("id") == id) arr.remove(i)
        }
        writeArray(ctx, KEY_QUEUE, arr)
    }

    fun clearPending(ctx: Context) {
        writeArray(ctx, KEY_QUEUE, JSONArray())
    }

    // ---------------- statistik ----------------

    fun bumpAuto(ctx: Context, title: String) {
        val s = readStats(ctx)
        val obj = JSONObject()
        obj.put("auto", s.autoCount + 1)
        obj.put("last_title", title.take(60))
        obj.put("last_at", System.currentTimeMillis())
        writeArray(ctx, KEY_STATS, JSONArray().put(obj))
    }

    fun readStats(ctx: Context): Stats {
        val arr = readArray(ctx, KEY_STATS)
        if (arr.length() == 0) return Stats(0L, 0, null, 0L)
        val o = arr.optJSONObject(0) ?: return Stats(0L, 0, null, 0L)
        val last = o.optString("last_title").takeIf { it.isNotBlank() }
        return Stats(
            autoCount = o.optLong("auto"),
            reviewCount = pendingCount(ctx),
            lastTitle = last,
            lastAt = o.optLong("last_at")
        )
    }

    // ---------------- json helpers ----------------

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readArray(ctx: Context, key: String): JSONArray {
        val raw = prefs(ctx).getString(key, null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun writeArray(ctx: Context, key: String, arr: JSONArray) {
        prefs(ctx).edit().putString(key, arr.toString()).apply()
    }

    // ---------------- hash ----------------

    private fun fnv1a64(s: String): Long {
        var h = -3750763034362895579L // 0xcbf29ce484222325
        for (ch in s) {
            h = h xor ch.code.toLong()
            h *= 1099511628211L // 0x100000001b3
        }
        return h
    }

    private fun hex64(v: Long): String {
        val digits = "0123456789abcdef"
        val sb = StringBuilder(16)
        for (i in 15 downTo 0) {
            sb.append(digits[((v ushr (i * 4)) and 0xF).toInt()])
        }
        return sb.toString()
    }

    private fun abs(x: Long): Long = if (x < 0) -x else x
}
