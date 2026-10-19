package com.budgeting.me.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.budgeting.me.MainActivity
import com.budgeting.me.R
import com.budgeting.me.util.BankNotifParser
import com.budgeting.me.util.CategoryMatcher
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.NotifScorer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Konfirmasi hasil parse notifikasi bank: "Catat pengeluaran Rp50.000 dari BCA?".
 * Konfirmasi -> form transaksi pre-filled (nominal + jenis + kategori + catatan).
 * Abaikan -> dismiss, tidak ada yang dicatat.
 *
 * v4.7: sheet ini hanya muncul untuk kelas TRANSACTION / AMBIGU.
 * PROMO / OTP / INFO tidak pernah sampai ke sini (gate di BankNotifListener).
 */
class BankConfirmSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.sheet_bank_confirm, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val args = arguments
        val amount = args?.getLong(ARG_AMOUNT, 0L) ?: 0L
        val kind = args?.getString(ARG_KIND).orEmpty()
        val bank = args?.getString(ARG_BANK).orEmpty()
        val raw = args?.getString(ARG_RAW).orEmpty()
        val classification = args?.getString(ARG_CLASSIFICATION).orEmpty()
        val category = args?.getString(ARG_CATEGORY).orEmpty()
        val merchant = args?.getString(ARG_MERCHANT).orEmpty()

        val tvAmount: TextView = v.findViewById(R.id.tv_confirm_amount)
        val tvBadge: TextView = v.findViewById(R.id.tv_confirm_badge)
        val tvBank: TextView = v.findViewById(R.id.tv_confirm_bank)
        val tvRaw: TextView = v.findViewById(R.id.tv_confirm_raw)
        val tvRisk: TextView = v.findViewById(R.id.tv_confirm_risk)
        val tvRiskHint: TextView = v.findViewById(R.id.tv_confirm_risk_hint)
        val tvMerchant: TextView = v.findViewById(R.id.tv_confirm_merchant)
        val tvCategory: TextView = v.findViewById(R.id.tv_confirm_category)

        tvAmount.text = FormatHelper.formatRupiah(amount.toDouble())

        val isIncome = kind == BankNotifParser.KIND_IN
        tvBadge.setText(if (isIncome) R.string.bank_badge_in else R.string.bank_badge_out)
        tvBadge.background = ContextCompat.getDrawable(
            requireContext(),
            if (isIncome) R.drawable.bg_badge_income else R.drawable.bg_badge_expense
        )
        tvBank.text = bank
        tvRaw.text = raw

        // Ambigu: tandai biar user cek nominal & kategori sebelum menekan Konfirmasi.
        val isAmbigu = classification == NotifScorer.AMBIGU
        tvRisk.visibility = if (isAmbigu) View.VISIBLE else View.GONE
        tvRiskHint.visibility = if (isAmbigu) View.VISIBLE else View.GONE
        if (isAmbigu) {
            tvRisk.background = ContextCompat.getDrawable(
                requireContext(), R.drawable.bg_badge_warning
            )
        }

        // Merchant yang dipakai untuk auto-kategori. Kalau tidak ada merchant,
        // cek ulang dari raw supaya kategori tetap terisi (mis. judul notif).
        val merchantName = merchant.ifBlank { autoMerchant(raw) }
        if (merchantName.isNotBlank()) {
            tvMerchant.visibility = View.VISIBLE
            tvMerchant.text = getString(R.string.bank_confirm_merchant, merchantName)
        } else {
            tvMerchant.visibility = View.GONE
        }

        val suggested = category.ifBlank {
            CategoryMatcher.suggestCategory(raw).orEmpty()
        }
        if (suggested.isNotBlank()) {
            tvCategory.visibility = View.VISIBLE
            tvCategory.text = getString(R.string.bank_confirm_auto_category, suggested)
        } else {
            tvCategory.visibility = View.GONE
        }

        v.findViewById<View>(R.id.btn_confirm_ok).setOnClickListener {
            val host = activity as? MainActivity
            dismiss()
            host?.openTransactionFromBankNotif(
                amount, kind, bank, raw,
                suggested.ifBlank { null }, merchantName.ifBlank { null }
            )
        }
        v.findViewById<View>(R.id.btn_confirm_ignore).setOnClickListener { dismiss() }
    }

    /** Cadangan: merchant dari teks mentah, kalau argumen tidak membawa merchant. */
    private fun autoMerchant(raw: String): String {
        return try {
            CategoryMatcher.findMerchant(raw).orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val ARG_AMOUNT = "arg_amount"
        private const val ARG_KIND = "arg_kind"
        private const val ARG_BANK = "arg_bank"
        private const val ARG_RAW = "arg_raw"
        private const val ARG_CLASSIFICATION = "arg_classification"
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_MERCHANT = "arg_merchant"

        fun show(
            fm: FragmentManager,
            amount: Long,
            kind: String,
            bank: String,
            raw: String,
            classification: String? = null,
            category: String? = null,
            merchant: String? = null
        ): BankConfirmSheet {
            val s = BankConfirmSheet()
            s.arguments = Bundle().apply {
                putLong(ARG_AMOUNT, amount)
                putString(ARG_KIND, kind)
                putString(ARG_BANK, bank)
                putString(ARG_RAW, raw)
                putString(ARG_CLASSIFICATION, classification)
                putString(ARG_CATEGORY, category)
                putString(ARG_MERCHANT, merchant)
            }
            s.show(fm, "bank_confirm")
            return s
        }
    }
}