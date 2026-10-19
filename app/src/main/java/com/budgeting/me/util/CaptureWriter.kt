package com.budgeting.me.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.budgeting.me.MainActivity
import com.budgeting.me.R
import com.budgeting.me.data.DatabaseHelper
import com.budgeting.me.model.Transaction

/**
 * Eksekutor auto-capture: menerima satu notifikasi, menilai tiap baris,
 * lalu menulis / mengantrekan / membuang hasilnya.
 *
 * Semua lokal. Tidak ada jaringan, tidak ada foreground service, tidak butuh
 * app sedang terbuka: dipanggil langsung dari [BankNotifListener].
 *
 * Alur tiap kandidat:
 *  1. cek dedup (fingerprint notif + transaksi otomatis nominal sama hari ini)
 *  2. AUTO   -> tulis DB (is_auto = 1) + notif ringan dengan aksi "Hapus"
 *  3. REVIEW -> masuk antrean [CaptureStore] + notif "perlu dicek"
 *  4. DROP   -> diabaikan
 */
object CaptureWriter {

    const val CHANNEL_ID = "budgeting_capture_channel"
    const val NOTIF_BASE_ID = 7300

    /** Ringkasan hasil pemrosesan satu notifikasi (untuk log & tes). */
    data class Result(
        val autoSaved: List<Saved>,
        val queued: List<AutoCapture.Candidate>,
        val dropped: List<AutoCapture.Candidate>,
        val duplicates: Int
    )

    /** Satu transaksi yang benar-benar tersimpan, plus id untuk undo. */
    data class Saved(val id: Long, val candidate: AutoCapture.Candidate)

    /**
     * Proses satu notifikasi dari [pkg].
     *
     * @param bankLabel nama sumber yang sudah dipakai untuk judul; kalau null
     *                  tidak diproses (belum masuk whitelist bank).
     * @return null kalau tidak ada yang dicatat dan tidak ada yang masuk antrean.
     */
    fun handle(
        ctx: Context,
        pkg: String?,
        bankLabel: String?,
        title: String?,
        text: String?
    ): Result? {
        if (!CaptureStore.isEnabled(ctx)) return null
        if (bankLabel.isNullOrBlank()) return null

        val trusted = BankWhitelist.isKnown(ctx, pkg)
        val candidates = AutoCapture.evaluate(title, text, bankLabel.trim(), trusted)
        if (candidates.isEmpty()) return null

        val autoSaved = ArrayList<Saved>()
        val queued = ArrayList<AutoCapture.Candidate>()
        val dropped = ArrayList<AutoCapture.Candidate>()
        var duplicates = 0

        val db = DatabaseHelper(ctx.applicationContext)
        val today = FormatHelper.todayIso()

        for (c in candidates) {
            if (c.decision == AutoCapture.DECISION_DROP) {
                dropped.add(c)
                continue
            }

            // --- dedup lapis 1: fingerprint notif ---
            val fp = CaptureStore.fingerprint(c.amount, c.title)
            if (CaptureStore.isSeen(ctx, fp)) {
                duplicates++
                continue
            }

            // --- dedup lapis 2: transaksi otomatis nominal sama, hari sama ---
            val dup = db.findAutoByAmount(c.amount.toDouble(), today)
            if (dup != null) {
                duplicates++
                CaptureStore.markSeen(ctx, fp)
                continue
            }

            CaptureStore.markSeen(ctx, fp)

            if (c.decision == AutoCapture.DECISION_REVIEW) {
                CaptureStore.addPending(ctx, c)
                queued.add(c)
                continue
            }

            // --- AUTO: tulis DB ---
            val id = writeTransaction(db, c)
            if (id > 0) {
                autoSaved.add(Saved(id, c))
                CaptureStore.bumpAuto(ctx, c.title)
            }
        }

        if (autoSaved.isEmpty() && queued.isEmpty()) {
            Log.d(TAG, "Tidak ada yang dicatat (drop=${dropped.size} dupe=$duplicates)")
            return null
        }

        notifyUser(ctx, autoSaved, queued)
        Log.i(
            TAG,
            "Tercatat ${autoSaved.size}, antre ${queued.size}, drop ${dropped.size}, dupe $duplicates"
        )
        return Result(autoSaved, queued, dropped, duplicates)
    }

    /** Ubah kandidat jadi transaksi dan simpan. Return id baris, -1 kalau gagal. */
    private fun writeTransaction(db: DatabaseHelper, c: AutoCapture.Candidate): Long {
        return try {
            val t = Transaction(
                title = c.title,
                amount = c.amount.toDouble(),
                type = AutoCapture.toDbType(c.kind),
                category = c.category,
                date = null,
                dateIso = FormatHelper.todayIso()
            )
            t.auto = 1
            db.insertTransaction(t)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal simpan transaksi otomatis: ${e.message}")
            -1L
        }
    }

    /** Notif ringan: 1 ketuk = hapus (undo). Tanpa ini user tidak bisa canceling cepat. */
    private fun notifyUser(
        ctx: Context,
        saved: List<Saved>,
        queued: List<AutoCapture.Candidate>
    ) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(ctx, nm)

        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        if (saved.isNotEmpty()) {
            val total = saved.sumOf { it.candidate.amount }
            val first = saved.first().candidate
            val text = if (saved.size == 1) {
                val arrow = if (first.kind == AutoCapture.KIND_IN) "masuk" else "keluar"
                "${FormatHelper.formatRupiah(total.toDouble())} $arrow" +
                    (first.merchant?.let { " di $it" } ?: "")
            } else {
                "${saved.size} transaksi totaling ${FormatHelper.formatRupiah(total.toDouble())}"
            }
            val b = baseBuilder(ctx, CHANNEL_ID)
                .setContentTitle(ctx.getString(R.string.capture_notif_saved_title))
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
            b.addAction(
                0,
                ctx.getString(R.string.capture_notif_undo),
                undoIntent(ctx, saved.map { it.id })
            )
            post(nm, NOTIF_BASE_ID + 1, b.build())
        }

        if (queued.isNotEmpty()) {
            val c = queued.first()
            val body = if (queued.size == 1) {
                FormatHelper.formatRupiah(c.amount.toDouble()) + (c.merchant?.let { " - $it" } ?: "")
            } else {
                "${queued.size} transaksi perlu dicek"
            }
            val b = baseBuilder(ctx, CHANNEL_ID)
                .setContentTitle(ctx.getString(R.string.capture_notif_review_title, queued.size))
                .setContentText(body)
                .setContentIntent(open)
                .setAutoCancel(true)
            post(nm, NOTIF_BASE_ID + 2, b.build())
        }
    }

    private fun ensureChannel(ctx: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.capture_channel_label),
            NotificationManager.IMPORTANCE_LOW
        )
        ch.description = ctx.getString(R.string.capture_channel_desc)
        ch.setShowBadge(true)
        try {
            nm.createNotificationChannel(ch)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal buat channel: ${e.message}")
        }
    }

    private fun post(nm: NotificationManager, id: Int, n: Notification) {
        try {
            nm.notify(id, n)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal kirim notif: ${e.message}")
        }
    }

    private fun baseBuilder(ctx: Context, channel: String): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, channel)
        } else {
            Notification.Builder(ctx)
        }.setSmallIcon(R.drawable.logo_mansxdev).setPriority(Notification.PRIORITY_LOW)
    }

    private fun undoIntent(ctx: Context, ids: List<Long>): PendingIntent {
        val intent = Intent(ctx, CaptureUndoReceiver::class.java).apply {
            action = CaptureUndoReceiver.ACTION_UNDO
            putExtra(CaptureUndoReceiver.EXTRA_IDS, LongArray(ids.size) { ids[it] })
        }
        return PendingIntent.getBroadcast(
            ctx,
            (ids.firstOrNull() ?: 0L).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private const val TAG = "CaptureWriter"
}

/**
 * Receiver untuk aksi "Hapus" pada notifikasi auto-capture.
 * Menghapus transaksi yang baru tercatat dari notifikasi tersebut.
 */
class CaptureUndoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UNDO) return
        val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: return
        if (ids.isEmpty()) return
        val db = DatabaseHelper(context.applicationContext)
        var removed = 0
        for (id in ids) {
            try {
                if (db.deleteTransaction(id) > 0) removed++
            } catch (e: Exception) {
                Log.w("CaptureUndo", "Gagal hapus $id: ${e.message}")
            }
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            nm?.cancel(CaptureWriter.NOTIF_BASE_ID + 1)
            nm?.cancel(CaptureWriter.NOTIF_BASE_ID + 2)
        } catch (e: Exception) {
            // diam saja
        }
        Log.i("CaptureUndo", "Undo $removed transaksi")
    }

    companion object {
        const val ACTION_UNDO = "com.budgeting.me.CAPTURE_UNDO"
        const val EXTRA_IDS = "ids"
    }
}
