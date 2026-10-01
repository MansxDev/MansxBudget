package com.budgeting.me.util

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Menangkap notifikasi bank/e-wallet lalu menyiarkan hasilnya ke MainActivity
 * lewat broadcast internal (setPackage = app sendiri).
 *
 * - Package dikenal -> [ACTION_BANK_NOTIF_PARSED] (sheet konfirmasi).
 * - Package asing tapi berpola bank -> [ACTION_BANK_UNKNOWN] (sheet auto-learn).
 * - Selain itu -> skip diam-diam.
 *
 * Tidak butuh READ_SMS. Opt-in: user mengaktifkan listener lewat
 * Settings > Akses Notifikasi (ACTION_NOTIFICATION_LISTENER_SETTINGS).
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

            val parsed = BankNotifParser.parse(this, s.packageName, title, text)
            if (parsed != null) {
                val intent = Intent(ACTION_BANK_NOTIF_PARSED).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_AMOUNT, parsed.amount)
                    putExtra(EXTRA_KIND, parsed.kind)
                    putExtra(EXTRA_BANK, parsed.bank)
                    putExtra(EXTRA_RAW, parsed.raw)
                    putExtra(EXTRA_PKG, parsed.pkg)
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
        private const val TAG = "BankNotifListener"
    }
}