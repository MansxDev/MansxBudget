package com.budgeting.me.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.budgeting.me.R
import com.budgeting.me.data.DatabaseHelper
import com.budgeting.me.model.Transaction
import com.budgeting.me.util.CalculatorDialog
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.RupiahTextWatcher
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class TransactionSheet : DialogFragment() {

    interface OnSaved {
        fun onSaved()
    }

    private var callback: OnSaved? = null
    private lateinit var db: DatabaseHelper
    private var editId = -1L
    private var editing: Transaction? = null

    private lateinit var etTitle: EditText
    private lateinit var etAmount: EditText
    private lateinit var rbIncome: RadioButton
    private lateinit var rbExpense: RadioButton
    private lateinit var chipCategory: ChipGroup
    private lateinit var chipFreq: ChipGroup
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
        if (editId > 0) editing = db.getTransaction(editId)
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
        return inflater.inflate(R.layout.sheet_transaction, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        etTitle = v.findViewById(R.id.et_title)
        etAmount = v.findViewById(R.id.et_amount)
        etAmount.addTextChangedListener(RupiahTextWatcher(etAmount))
        val tvSheetTitle: TextView = v.findViewById(R.id.tv_sheet_title)
        val btnCalc: ImageButton = v.findViewById(R.id.btn_calc)
        rbIncome = v.findViewById(R.id.rb_income)
        rbExpense = v.findViewById(R.id.rb_expense)
        chipCategory = v.findViewById(R.id.chip_category)
        chipFreq = v.findViewById(R.id.chip_freq)
        btnSave = v.findViewById(R.id.btn_save_sheet)
        btnDelete = v.findViewById(R.id.btn_delete_sheet)

        buildCategoryChips()
        buildFreqChips()

        val ed = editing
        if (ed != null) {
            tvSheetTitle.text = getString(R.string.form_title_edit, editId)
            etTitle.setText(ed.title)
            etAmount.setText(ed.amount.toLong().toString())
            if (ed.isIncome()) rbIncome.isChecked = true
            else rbExpense.isChecked = true
            checkChipByTag(chipCategory, ed.category)
            checkChipByTag(chipFreq, ed.recurring)
            btnSave.setText(R.string.btn_update)
            btnDelete.visibility = View.VISIBLE
        } else {
            tvSheetTitle.setText(R.string.form_title_add)
            rbExpense.isChecked = true
            checkChipByTag(chipCategory, DEFAULT_CATEGORIES[0])
            checkChipByTag(chipFreq, Transaction.FREQ_NONE)
            btnDelete.visibility = View.GONE
            val args = arguments
            if (args != null && args.containsKey(ARG_AMOUNT)) {
                val preset = args.getDouble(ARG_AMOUNT, 0.0)
                // Angka mentah -> RupiahTextWatcher yang memformatnya jadi "50.000".
                if (preset > 0) etAmount.setText(preset.toLong().toString())
            }
            if (args != null && args.containsKey(ARG_TYPE)) {
                val presetType = args.getString(ARG_TYPE, DatabaseHelper.TYPE_EXPENSE)
                if (DatabaseHelper.TYPE_INCOME == presetType) rbIncome.isChecked = true
                else rbExpense.isChecked = true
            }
            if (args != null && args.containsKey(ARG_TITLE)) {
                val note = args.getString(ARG_TITLE).orEmpty()
                if (note.isNotEmpty()) etTitle.setText(note)
            }
        }

        btnCalc.setOnClickListener {
            val d: Dialog = CalculatorDialog(requireContext()) { result ->
                if (result == result.toLong().toDouble()) etAmount.setText(result.toLong().toString())
                else etAmount.setText(result.toString())
            }
            d.show()
        }

        btnSave.setOnClickListener { save() }
        btnDelete.setOnClickListener {
            if (editId > 0 && db.deleteTransaction(editId) > 0) {
                toast("Transaksi berhasil dihapus!")
                done()
            } else {
                toast("Gagal menghapus transaksi")
            }
        }
    }

    private fun buildCategoryChips() {
        val cats = LinkedHashSet<String>()
        DEFAULT_CATEGORIES.forEach { cats.add(it) }
        try {
            cats.addAll(db.getDistinctCategories())
        } catch (ignored: Exception) {
        }
        for (c in cats) {
            val chip = Chip(requireContext())
            chip.text = c
            chip.tag = c
            chip.isCheckable = true
            chip.isClickable = true
            chipCategory.addView(chip)
        }
        chipCategory.isSingleSelection = true
        chipCategory.isSelectionRequired = true
    }

    private fun buildFreqChips() {
        addFreqChip(getString(R.string.freq_once), Transaction.FREQ_NONE)
        addFreqChip(getString(R.string.freq_daily), Transaction.FREQ_DAILY)
        addFreqChip(getString(R.string.freq_weekly), Transaction.FREQ_WEEKLY)
        addFreqChip(getString(R.string.freq_monthly), Transaction.FREQ_MONTHLY)
        chipFreq.isSingleSelection = true
        chipFreq.isSelectionRequired = true
    }

    private fun addFreqChip(label: String, tag: String) {
        val chip = Chip(requireContext())
        chip.text = label
        chip.tag = tag
        chip.isCheckable = true
        chip.isClickable = true
        chipFreq.addView(chip)
    }

    private fun checkChipByTag(group: ChipGroup, tag: String?) {
        if (tag == null) return
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (tag.equals(child.tag.toString(), ignoreCase = true)) {
                group.check(if (child.id == View.NO_ID) assignId(child) else child.id)
                return
            }
        }
        // Unknown category (e.g. custom): add it on the fly.
        if (group == chipCategory) {
            val chip = Chip(requireContext())
            chip.text = tag
            chip.tag = tag
            chip.isCheckable = true
            chip.isClickable = true
            chip.id = View.generateViewId()
            group.addView(chip)
            group.check(chip.id)
        }
    }

    private fun assignId(child: View): Int {
        val id = View.generateViewId()
        child.id = id
        return id
    }

    private fun save() {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            toast("Judul transaksi tidak boleh kosong")
            return
        }
        val amount = RupiahTextWatcher.parseAmount(etAmount.text.toString())
        if (amount <= 0) {
            toast("Jumlah nominal harus lebih besar dari 0")
            return
        }
        val type = if (rbIncome.isChecked) DatabaseHelper.TYPE_INCOME else DatabaseHelper.TYPE_EXPENSE
        val category = chipTag(chipCategory, DEFAULT_CATEGORIES[0])
        val freq = chipTag(chipFreq, Transaction.FREQ_NONE)
        val todayIso = FormatHelper.todayIso()

        val ed = editing
        val ok: Boolean
        if (ed != null) {
            ed.title = title
            ed.amount = amount
            ed.type = type
            ed.category = category
            ed.recurring = freq
            if (!ed.isRecurringTemplate()) ed.lastRun = null
            else if (ed.lastRun == null) ed.lastRun = todayIso
            ok = db.updateTransaction(ed) > 0
            if (ok) toast("Transaksi berhasil diperbarui!")
        } else {
            val t = Transaction(title, amount, type, category,
                FormatHelper.toDisplay(todayIso))
            t.dateIso = todayIso
            t.recurring = freq
            if (t.isRecurringTemplate()) t.lastRun = todayIso
            ok = db.insertTransaction(t) > -1
            if (ok) toast("Transaksi berhasil disimpan!")
        }
        if (ok) done()
        else toast("Gagal menyimpan transaksi")
    }

    private fun chipTag(group: ChipGroup, fallback: String): String {
        val id = group.checkedChipId
        if (id == View.NO_ID) return fallback
        val child = group.findViewById<View>(id) ?: return fallback
        val tag = child.tag ?: return fallback
        return tag.toString()
    }

    private fun done() {
        callback?.onSaved()
        dismiss()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        val DEFAULT_CATEGORIES = arrayOf(
            "Makanan", "Transportasi", "Gaji", "Belanja", "Tagihan", "Lainnya"
        )

        private const val ARG_ID = "arg_id"
        private const val ARG_AMOUNT = "arg_amount"
        private const val ARG_TYPE = "arg_type"
        private const val ARG_TITLE = "arg_title"

        fun forAdd(): TransactionSheet = TransactionSheet()

        fun forAddWithAmount(amount: Double): TransactionSheet {
            val s = TransactionSheet()
            val b = Bundle()
            b.putDouble(ARG_AMOUNT, amount)
            s.arguments = b
            return s
        }

        /** Pre-fill dari notifikasi bank: nominal, jenis, dan catatan "{bank} - cuplikan". */
        fun forBankNotif(amount: Long, type: String, note: String): TransactionSheet {
            val s = TransactionSheet()
            val b = Bundle()
            b.putDouble(ARG_AMOUNT, amount.toDouble())
            b.putString(ARG_TYPE, type)
            b.putString(ARG_TITLE, note)
            s.arguments = b
            return s
        }

        fun forEdit(id: Long): TransactionSheet {
            val s = TransactionSheet()
            val b = Bundle()
            b.putLong(ARG_ID, id)
            s.arguments = b
            return s
        }

        fun allCategories(db: DatabaseHelper): List<String> {
            val cats = LinkedHashSet<String>()
            DEFAULT_CATEGORIES.forEach { cats.add(it) }
            try {
                cats.addAll(db.getDistinctCategories())
            } catch (ignored: Exception) {
            }
            return ArrayList(cats)
        }
    }
}
