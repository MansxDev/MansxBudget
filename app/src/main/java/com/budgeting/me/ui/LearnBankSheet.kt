package com.budgeting.me.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.budgeting.me.MainActivity
import com.budgeting.me.R
import com.budgeting.me.util.BankNotifParser
import com.budgeting.me.util.BankWhitelist
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Auto-learn: daftarkan package asing yang berpola bank sebagai sumber baru.
 * "Daftarkan & Catat" -> simpan custom whitelist -> langsung tampilkan
 * sheet konfirmasi untuk notif yang tadi. "Nanti Saja" -> dismiss + catat
 * timestamp (tidak ditawari lagi 7 hari untuk package ini).
 */
class LearnBankSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.sheet_learn_bank, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val args = arguments
        val pkg = args?.getString(ARG_PKG).orEmpty()
        val label = args?.getString(ARG_LABEL).orEmpty()
        val raw = args?.getString(ARG_RAW).orEmpty()
        val amount = args?.getLong(ARG_AMOUNT, 0L) ?: 0L
        val kind = args?.getString(ARG_KIND).orEmpty()

        val tvApp: TextView = v.findViewById(R.id.tv_learn_app)
        val tvPkg: TextView = v.findViewById(R.id.tv_learn_pkg)
        val tvRaw: TextView = v.findViewById(R.id.tv_learn_raw)
        val etName: EditText = v.findViewById(R.id.et_learn_name)

        tvApp.text = label.ifEmpty { pkg }
        tvPkg.text = pkg
        tvRaw.text = raw
        etName.setText(label.ifEmpty { pkg })
        etName.setSelection(etName.text.length)

        v.findViewById<View>(R.id.btn_learn_ok).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.learn_name_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            BankWhitelist.addCustom(requireContext(), pkg, name)
            val host = activity as? MainActivity
            val fm = parentFragmentManager
            dismiss()
            // Langsung parse notif tadi dengan nama yang baru didaftarkan.
            val parsedKind = kind.ifEmpty { BankNotifParser.KIND_OUT }
            BankConfirmSheet.show(fm, amount, parsedKind, name, raw)
            host?.refreshAllTabs()
        }
        v.findViewById<View>(R.id.btn_learn_later).setOnClickListener {
            BankWhitelist.markDismissed(requireContext(), pkg)
            dismiss()
        }
    }

    companion object {
        private const val ARG_PKG = "arg_pkg"
        private const val ARG_LABEL = "arg_label"
        private const val ARG_RAW = "arg_raw"
        private const val ARG_AMOUNT = "arg_amount"
        private const val ARG_KIND = "arg_kind"

        fun show(
            fm: FragmentManager,
            pkg: String,
            label: String,
            raw: String,
            amount: Long,
            kind: String
        ): LearnBankSheet {
            val s = LearnBankSheet()
            s.arguments = Bundle().apply {
                putString(ARG_PKG, pkg)
                putString(ARG_LABEL, label)
                putString(ARG_RAW, raw)
                putLong(ARG_AMOUNT, amount)
                putString(ARG_KIND, kind)
            }
            s.show(fm, "learn_bank")
            return s
        }
    }
}