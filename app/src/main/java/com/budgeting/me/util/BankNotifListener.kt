package com.budgeting.me.util

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Menangkap notifikasi bank/e-wallet lalu memprosesnya penuh di latar belakang.
 *
 * Urutan (v4.8):
 *  1. Gate kelas: PROMO / OTP / INFO langsung dibuang, tidak dicatat.
 *  2. Sumber dikenal -> [CaptureWriter] menulis sendiri ke DB (nol tap).
 *     Skor ambigu -> antrean review, bukan hilang.
 *  3. Kalau auto-capture tidak menulis apa pun -> fallback broadcast
 *     [ACTION_BANK_NOTIF_PARSED] supaya sheet konfirmasi v4.7 tetap muncul.
 *  4. Package asing berpola bank -> [ACTION_BANK_UNKNOWN] (auto-learn sumber).
 *
 * Semua lokal: tidak ada panggilan jaringan. Tidak butuh READ_SMS.
 * Opt-in: user mengaktifkan listener lewat Settings > Akses Notifikasi.
 */
class BankNotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            val s = sbn ?: return
            val notification = s.notification ?: return
            val extras = notification.extras ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

            // --- Gate v4.8: classify dulu, auto-capture hanya untuk TRANSACTION ---
            val classification = NotifScorer.classify(
                merge(title, text), NotifScorer.score(merge(title, text))
            )
            if (classification == NotifScorer.PROMO || classification == NotifScorer.OTP) return
            if (classification == NotifScorer.INFO) return

            // --- Auto-capture (v4.8): tulis sendiri, tanpa buka app ---
            val known = BankWhitelist.findName(this, s.packageName)
                ?: BankWhitelist.findNameByTitle(title)
            if (known != null) {
                val result = CaptureWriter.handle(this, s.packageName, known, title, text)
                if (result != null) {
                    Log.i(
                        TAG,
                        "Auto-capture: ${result.autoSaved.size} tercatat, " +
                            "${result.queued.size} antre, ${result.dropped.size} dibuang"
                    )
                    return
                }
            }

            // --- Fallback v4.7: sheet konfirmasi kalau auto-capture tidak menulis ---
            val parsed = BankNotifParser.parseExtended(this, s.packageName, title, text)
            if (parsed != null) {
                if (!BankNotifParser.isTransactionLike(parsed.classification)) return
                val intent = Intent(ACTION_BANK_NOTIF_PARSED).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_AMOUNT, parsed.amount)
                    putExtra(EXTRA_KIND, parsed.kind)
                    putExtra(EXTRA_BANK, parsed.bank)
                    putExtra(EXTRA_RAW, parsed.raw)
                    putExtra(EXTRA_PKG, parsed.pkg)
                    putExtra(EXTRA_CLASSIFICATION, parsed.classification)
                    putExtra(EXTRA_SCORE, parsed.score)
                    putExtra(EXTRA_CATEGORY, parsed.suggestedCategory)
                    putExtra(EXTRA_MERCHANT, parsed.merchant)
                }
                sendBroadcast(intent)
                return
            }

            val unknown = BankNotifParser.detectUnknown(this, s.packageName, title, text)
                ?: return
            val learn = Intent(ACTION_BANK_UNKNOWN).apply {
                setPackage(packageName)
                putExtra(EXTRA_PKG, unknown.pkg)
                putExtra(EXTRA_LABEL, unknown.label)
                putExtra(EXTRA_RAW, unknown.raw)
                putExtra(EXTRA_AMOUNT, unknown.parsedAmount)
                putExtra(EXTRA_KIND, unknown.parsedKind)
            }
            sendBroadcast(learn)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal proses notifikasi: ${e.message}")
        }
    }

    private fun merge(title: String?, text: String?): String =
        if (title.isNullOrBlank()) text.orEmpty() else "$title $text"

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener notifikasi terhubung")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Listener notifikasi terputus")
    }

    companion object {
        const val ACTION_BANK_NOTIF_PARSED = "com.budgeting.me.BANK_NOTIF_PARSED"
        const val ACTION_BANK_UNKNOWN = "com.budgeting.me.BANK_UNKNOWN"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_KIND = "kind"
        const val EXTRA_BANK = "bank"
        const val EXTRA_RAW = "raw"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_LABEL = "label"
        const val EXTRA_CLASSIFICATION = "classification"
        const val EXTRA_SCORE = "score"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_MERCHANT = "merchant"
        private const val TAG = "BankNotifListener"
    }
}