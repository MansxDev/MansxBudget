package com.budgeting.me.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.budgeting.me.R
import com.budgeting.me.data.DatabaseHelper
import com.budgeting.me.model.Debt
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.RupiahTextWatcher
import java.util.Calendar

/** Form tambah/edit catatan hutang & piutang. */
class DebtSheet : DialogFragment() {

    interface OnSaved {
        fun onSaved()
    }

    private var callback: OnSaved? = null
    private lateinit var db: DatabaseHelper
    private var editId = -1L
    private var editing: Debt? = null
    private var dateIso: String? = null

    private lateinit var etDebtor: EditText
    private lateinit var rbMine: RadioButton
    private lateinit var rbTheirs: RadioButton
    private lateinit var etTotal: EditText
    private lateinit var etPaid: EditText
    private lateinit var etPurpose: EditText
    private lateinit var btnDate: Button
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button

    fun setOnSaved(cb: OnSaved) {
        callback = cb
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Budgeting_Dialog_Floating)
        db = DatabaseHelper(requireContext())
        editId = arguments?.getLong(ARG_ID, -1) ?: -1
        if (editId > 0) editing = db.getDebt(editId)
    }

    override fun onStart() {
        super.onStart()
        val w = dialog?.window
        if (w != null) {
            val margin = (20 * resources.displayMetrics.density).toInt()
            val width = resources.displayMetrics.widthPixels - margin * 2
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            w.setDimAmount(0.6f)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.sheet_debt, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        etDebtor = v.findViewById(R.id.et_debtor)
        rbMine = v.findViewById(R.id.rb_debt_mine)
        rbTheirs = v.findViewById(R.id.rb_debt_theirs)
        etTotal = v.findViewById(R.id.et_debt_total)
        etPaid = v.findViewById(R.id.et_debt_paid)
        etPurpose = v.findViewById(R.id.et_debt_purpose)
        btnDate = v.findViewById(R.id.btn_debt_date)
        btnSave = v.findViewById(R.id.btn_save_debt)
        btnDelete = v.findViewById(R.id.btn_delete_debt)
        val tvTitle: TextView = v.findViewById(R.id.tv_debt_sheet_title)

        etTotal.addTextChangedListener(RupiahTextWatcher(etTotal))
        etPaid.addTextChangedListener(RupiahTextWatcher(etPaid))

        dateIso = FormatHelper.todayIso()
        refreshDateButton()

        val ed = editing
        if (ed != null) {
            tvTitle.text = getString(R.string.debt_form_title_edit, editId)
            etDebtor.setText(ed.name)
            if (ed.isMine()) rbMine.isChecked = true else rbTheirs.isChecked = true
            etTotal.setText(ed.total.toLong().toString())
            etPaid.setText(ed.paid.toLong().toString())
            etPurpose.setText(ed.purpose)
            dateIso = ed.dateIso ?: FormatHelper.todayIso()
            refreshDateButton()
            btnSave.setText(R.string.btn_update)
            btnDelete.visibility = View.VISIBLE
        } else {
            tvTitle.setText(R.string.debt_form_title_add)
            rbMine.isChecked = true
            btnDelete.visibility = View.GONE
        }

        btnDate.setOnClickListener { pickDate() }
        btnSave.setOnClickListener { save() }
        btnDelete.setOnClickListener {
            if (editId > 0 && db.deleteDebt(editId) > 0) {
                toast(getString(R.string.debt_deleted))
                done()
            } else {
                toast(getString(R.string.debt_save_failed))
            }
        }
    }

    private fun refreshDateButton() {
        btnDate.text = FormatHelper.toDisplay(dateIso)
    }

    private fun pickDate() {
        val c = Calendar.getInstance()
        val cur = dateIso
        if (cur != null && cur.length >= 10) {
            try {
                c.set(cur.substring(0, 4).toInt(),
                    cur.substring(5, 7).toInt() - 1,
                    cur.substring(8, 10).toInt())
            } catch (ignored: Exception) {
            }
        }
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                dateIso = FormatHelper.iso(y, m + 1, d)
                refreshDateButton()
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun save() {
        val name = etDebtor.text.toString().trim()
        if (name.isEmpty()) {
            toast(getString(R.string.debt_err_name))
            return
        }
        val total = RupiahTextWatcher.parseAmount(etTotal.text.toString())
        if (total <= 0) {
            toast(getString(R.string.debt_err_amount))
            return
        }
        val paid = RupiahTextWatcher.parseAmount(etPaid.text.toString())
        val kind = if (rbTheirs.isChecked) Debt.KIND_THEIRS else Debt.KIND_MINE
        val purpose = etPurpose.text.toString().trim()
        val iso = dateIso ?: FormatHelper.todayIso()

        val ed = editing
        val ok: Boolean
        if (ed != null) {
            ed.name = name
            ed.kind = kind
            ed.total = total
            ed.paid = paid
            ed.purpose = purpose
            ed.dateIso = iso
            ed.date = FormatHelper.toDisplay(iso)
            ok = db.updateDebt(ed) > 0
            if (ok) toast(getString(R.string.debt_updated))
        } else {
            val d = Debt(name, kind, total, paid, purpose, FormatHelper.toDisplay(iso))
            d.dateIso = iso
            ok = db.insertDebt(d) > -1
            if (ok) toast(getString(R.string.debt_saved))
        }
        if (ok) done()
        else toast(getString(R.string.debt_save_failed))
    }

    private fun done() {
        callback?.onSaved()
        dismiss()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_ID = "arg_id"

        fun forAdd(): DebtSheet = DebtSheet()

        fun forEdit(id: Long): DebtSheet {
            val s = DebtSheet()
            val b = Bundle()
            b.putLong(ARG_ID, id)
            s.arguments = b
            return s
        }
    }
}
