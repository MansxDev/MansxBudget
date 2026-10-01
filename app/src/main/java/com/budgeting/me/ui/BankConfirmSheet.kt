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
import com.budgeting.me.util.FormatHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Konfirmasi hasil parse notifikasi bank: "Catat pengeluaran Rp50.000 dari BCA?".
 * Konfirmasi -> form transaksi pre-filled. Abaikan -> dismiss, tidak ada yang dicatat.
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

        val tvAmount: TextView = v.findViewById(R.id.tv_confirm_amount)
        val tvBadge: TextView = v.findViewById(R.id.tv_confirm_badge)
        val tvBank: TextView = v.findViewById(R.id.tv_confirm_bank)
        val tvRaw: TextView = v.findViewById(R.id.tv_confirm_raw)

        tvAmount.text = FormatHelper.formatRupiah(amount.toDouble())

        val isIncome = kind == BankNotifParser.KIND_IN
        tvBadge.setText(if (isIncome) R.string.bank_badge_in else R.string.bank_badge_out)
        tvBadge.background = ContextCompat.getDrawable(
            requireContext(),
            if (isIncome) R.drawable.bg_badge_income else R.drawable.bg_badge_expense
        )
        tvBank.text = bank
        tvRaw.text = raw

        v.findViewById<View>(R.id.btn_confirm_ok).setOnClickListener {
            val host = activity as? MainActivity
            dismiss()
            host?.openTransactionFromBankNotif(amount, kind, bank, raw)
        }
        v.findViewById<View>(R.id.btn_confirm_ignore).setOnClickListener { dismiss() }
    }

    companion object {
        private const val ARG_AMOUNT = "arg_amount"
        private const val ARG_KIND = "arg_kind"
        private const val ARG_BANK = "arg_bank"
        private const val ARG_RAW = "arg_raw"

        fun show(
            fm: FragmentManager,
            amount: Long,
            kind: String,
            bank: String,
            raw: String
        ): BankConfirmSheet {
            val s = BankConfirmSheet()
            s.arguments = Bundle().apply {
                putLong(ARG_AMOUNT, amount)
                putString(ARG_KIND, kind)
                putString(ARG_BANK, bank)
                putString(ARG_RAW, raw)
            }
            s.show(fm, "bank_confirm")
            return s
        }
    }
}