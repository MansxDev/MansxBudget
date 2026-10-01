package com.budgeting.me.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.budgeting.me.R
import com.budgeting.me.data.DatabaseHelper
import com.budgeting.me.model.Budget
import com.budgeting.me.model.Goal
import com.budgeting.me.util.CalendarSyncUtil
import com.budgeting.me.util.DateCountdownUtil
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.RupiahTextWatcher
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import java.time.LocalDate
import java.util.Calendar

class BudgetFragment : Fragment(), TabRefreshable {

    private var db: DatabaseHelper? = null
    private var adapter: BudgetAdapter? = null
    private var goalAdapter: GoalAdapter? = null
    private var tvEmpty: TextView? = null
    private var tvGoalEmpty: TextView? = null
    private var etCategory: AutoCompleteTextView? = null
    private var etLimit: EditText? = null
    private var etGoalName: EditText? = null
    private var etGoalTarget: EditText? = null

    private var goalSelBar: LinearLayout? = null
    private var tvGoalSelCount: TextView? = null
    private var btnGoalSelDelete: Button? = null

    // Goal form (tanggal target + sync kalender)
    private var goalDateBtn: Button? = null
    private var goalSyncSwitch: SwitchMaterial? = null
    private var dialogSyncSwitch: SwitchMaterial? = null
    private var pendingGoalDate: String? = null
    private val pendingGoalReminders = mutableSetOf<Int>()

    companion object {
        private const val REQ_CAL_PERM = 4201
        // day -> chip resource id (form & dialog pakai id sama tapi beda prefix)
        private val REM_CHIPS = mapOf(30 to R.id.chip_goal_rem_30, 7 to R.id.chip_goal_rem_7, 1 to R.id.chip_goal_rem_1)
        private val REM_CHIPS_EDIT = mapOf(30 to R.id.chip_goal_rem_edit_30, 7 to R.id.chip_goal_rem_edit_7, 1 to R.id.chip_goal_rem_edit_1)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_budget, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val database = DatabaseHelper(requireContext())
        db = database

        val categoryEt: AutoCompleteTextView = v.findViewById(R.id.et_new_category)
        val limitEt: EditText = v.findViewById(R.id.et_new_limit)
        etCategory = categoryEt
        etLimit = limitEt
        limitEt.addTextChangedListener(RupiahTextWatcher(limitEt))
        val btnAdd: Button = v.findViewById(R.id.btn_add_budget)
        tvEmpty = v.findViewById(R.id.tv_budget_empty)

        val rv: RecyclerView = v.findViewById(R.id.rv_budgets)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.isNestedScrollingEnabled = false
        val listAdapter = BudgetAdapter(
            database, FormatHelper.yearMonthNow(),
            { b, _ -> showLimitDialog(b) }
        )
        adapter = listAdapter
        rv.adapter = listAdapter

        btnAdd.setOnClickListener { addBudget() }
        limitEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addBudget()
                true
            } else {
                false
            }
        }
        val cardBudget: View = v.findViewById(R.id.card_budget_form)
        val sv: ScrollView = v.findViewById(R.id.sv_budget)
        val scrollToCard = View.OnFocusChangeListener { fv, hasFocus ->
            if (hasFocus) {
                fv.postDelayed({ sv.smoothScrollTo(0, cardBudget.bottom) }, 250)
            }
        }
        categoryEt.onFocusChangeListener = scrollToCard
        limitEt.onFocusChangeListener = scrollToCard

        setupGoals(v)

        refresh()
    }

    private fun setupGoals(v: View) {
        val nameEt: EditText = v.findViewById(R.id.et_goal_name)
        val targetEt: EditText = v.findViewById(R.id.et_goal_target)
        etGoalName = nameEt
        etGoalTarget = targetEt
        targetEt.addTextChangedListener(RupiahTextWatcher(targetEt))
        tvGoalEmpty = v.findViewById(R.id.tv_goal_empty)

        val rv: RecyclerView = v.findViewById(R.id.rv_goals)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.isNestedScrollingEnabled = false
        val listAdapter = GoalAdapter(this::showGoalDialog)
        goalAdapter = listAdapter
        listAdapter.setOnItemLongClick { g -> enterGoalSelectMode(g.id) }
        listAdapter.setOnSelectChange { count -> updateGoalSelBar(count) }
        rv.adapter = listAdapter

        v.findViewById<View>(R.id.btn_goal_sel_toggle).setOnClickListener {
            val ga = goalAdapter ?: return@setOnClickListener
            if (ga.isSelectMode()) exitGoalSelectMode()
            else enterGoalSelectMode(null)
        }
        goalSelBar = v.findViewById(R.id.goal_sel_bar)
        tvGoalSelCount = v.findViewById(R.id.tv_goal_sel_count)
        val goalSelDelete: Button = v.findViewById(R.id.btn_goal_sel_delete)
        btnGoalSelDelete = goalSelDelete
        v.findViewById<View>(R.id.btn_goal_sel_all).setOnClickListener {
            val ga = goalAdapter ?: return@setOnClickListener
            if (ga.getSelectedCount() >= ga.itemCount) {
                ga.selectAll(false)
            } else {
                ga.selectAll(true)
            }
            updateGoalSelBar(ga.getSelectedCount())
        }
        v.findViewById<View>(R.id.btn_goal_sel_delete).setOnClickListener {
            confirmDeleteSelectedGoals()
        }
        v.findViewById<View>(R.id.btn_goal_sel_cancel).setOnClickListener {
            exitGoalSelectMode()
        }

        v.findViewById<View>(R.id.btn_add_goal).setOnClickListener { addGoal() }
        targetEt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addGoal()
                true
            } else {
                false
            }
        }
        setupGoalForm(v)
        val cardGoal: View = v.findViewById(R.id.card_goal_form)
        val svGoal: ScrollView = v.findViewById(R.id.sv_budget)
        val scrollToGoal = View.OnFocusChangeListener { fv, hasFocus ->
            if (hasFocus) {
                fv.postDelayed({ svGoal.smoothScrollTo(0, cardGoal.bottom) }, 250)
            }
        }
        nameEt.onFocusChangeListener = scrollToGoal
        targetEt.onFocusChangeListener = scrollToGoal
    }

    private fun addBudget() {
        val database = db ?: return
        val cat = etCategory?.text?.toString()?.trim().orEmpty()
        val limit = RupiahTextWatcher.parseAmount(etLimit?.text?.toString().orEmpty())
        if (cat.isEmpty()) {
            toast("Isi nama kategori dulu")
            return
        }
        if (limit <= 0) {
            toast("Limit harus lebih besar dari 0")
            return
        }
        database.upsertBudget(cat, limit)
        etCategory?.setText("")
        etLimit?.setText("")
        etLimit?.let { hideKeyboard(it) }
        refresh()
        toast("Anggaran $cat disimpan")
    }

    private fun addGoal() {
        val database = db ?: return
        val name = etGoalName?.text?.toString()?.trim().orEmpty()
        val target = RupiahTextWatcher.parseAmount(etGoalTarget?.text?.toString().orEmpty())
        if (name.isEmpty()) {
            toast("Isi nama rencana dulu")
            return
        }
        if (target <= 0) {
            toast("Target harus lebih besar dari 0")
            return
        }
        val date = pendingGoalDate
        val syncOn = goalSyncSwitch?.isChecked == true
        var eventId: Long? = null
        if (syncOn) {
            if (date == null) {
                toast(getString(R.string.goal_cal_date_required))
                return
            }
            if (!calendarPermGranted()) {
                goalSyncSwitch?.isChecked = false
                toast(getString(R.string.goal_cal_perm_denied))
                return
            }
            eventId = CalendarSyncUtil.insertEvent(
                requireContext(), "Rencana Dana: $name", date, pendingGoalReminders.toList())
        }
        val g = Goal(name, target, 0.0)
        g.targetDate = date
        g.calendarEventId = eventId
        g.reminderDays = pendingGoalReminders.sorted().joinToString(",")
        database.insertGoal(g)
        etGoalName?.setText("")
        etGoalTarget?.setText("")
        etGoalTarget?.let { hideKeyboard(it) }
        pendingGoalDate = null
        pendingGoalReminders.clear()
        goalDateBtn?.setText(R.string.goal_date_pick)
        goalSyncSwitch?.isChecked = false
        refresh()
        toast("Rencana $name dibuat")
    }

    private fun setupGoalForm(v: View) {
        goalDateBtn = v.findViewById(R.id.btn_goal_date)
        goalDateBtn?.setOnClickListener {
            showDatePicker(pendingGoalDate) { iso ->
                pendingGoalDate = iso
                goalDateBtn?.text = getString(
                    R.string.goal_date_summary, DateCountdownUtil.formatDate(iso))
            }
        }
        goalSyncSwitch = v.findViewById(R.id.sw_goal_sync)
        goalSyncSwitch?.setOnCheckedChangeListener { _, checked ->
            if (checked && !calendarPermGranted()) {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                    REQ_CAL_PERM)
            }
        }
        val group: ChipGroup = v.findViewById(R.id.chip_goal_rem)
        bindReminderChips(group, pendingGoalReminders, REM_CHIPS)
    }

    private fun showDatePicker(baseIso: String?, onPick: (String) -> Unit) {
        val cal = Calendar.getInstance()
        if (!baseIso.isNullOrBlank()) {
            try {
                val ld = LocalDate.parse(baseIso)
                cal.set(ld.year, ld.monthValue - 1, ld.dayOfMonth)
            } catch (_: Exception) {
            }
        }
        DatePickerDialog(
            requireContext(),
            { _, y, m, d -> onPick("%04d-%02d-%02d".format(y, m + 1, d)) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun bindReminderChips(group: ChipGroup, selected: MutableSet<Int>, map: Map<Int, Int>) {
        for ((day, resId) in map) {
            val chip: Chip = group.findViewById(resId) ?: continue
            chip.isChecked = selected.contains(day)
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(day) else selected.remove(day)
            }
        }
    }

    private fun calendarPermGranted(): Boolean {
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_CAL_PERM) {
            if (grantResults.isEmpty() ||
                grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                goalSyncSwitch?.isChecked = false
                dialogSyncSwitch?.isChecked = false
                view?.let {
                    Snackbar.make(it, R.string.goal_cal_perm_denied, Snackbar.LENGTH_LONG).show()
                }
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun hideKeyboard(v: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(v.windowToken, 0)
        v.clearFocus()
    }

    // ---------- goal select mode (checklist delete) ----------

    private fun enterGoalSelectMode(firstId: Long?) {
        if (!isAdded) return
        val ga = goalAdapter ?: return
        if (ga.itemCount == 0) {
            toast(getString(R.string.goal_empty))
            return
        }
        ga.setSelectMode(true)
        if (firstId != null) ga.toggleSelected(firstId)
        val bar = goalSelBar ?: return
        bar.visibility = View.VISIBLE
        updateGoalSelBar(ga.getSelectedCount())
    }

    private fun exitGoalSelectMode() {
        val ga = goalAdapter
        if (ga != null && ga.isSelectMode()) {
            ga.setSelectMode(false)
        }
        goalSelBar?.visibility = View.GONE
    }

    private fun updateGoalSelBar(count: Int) {
        if (!isAdded) return
        if (count <= 0) {
            tvGoalSelCount?.text = getString(R.string.sel_count, 0)
            btnGoalSelDelete?.text = getString(R.string.sel_delete, 0)
            return
        }
        tvGoalSelCount?.text = getString(R.string.sel_count, count)
        btnGoalSelDelete?.text = getString(R.string.sel_delete, count)
    }

    private fun confirmDeleteSelectedGoals() {
        val database = db ?: return
        val ga = goalAdapter ?: return
        val sel = ga.getSelected()
        if (sel.isEmpty()) {
            exitGoalSelectMode()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.goal_sel_confirm_title)
            .setMessage(getString(R.string.goal_sel_confirm_msg, sel.size))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                var n = 0
                for (g in sel) {
                    CalendarSyncUtil.deleteEvent(requireContext(), g.calendarEventId)
                    if (database.deleteGoal(g.id) > 0) n++
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.goal_sel_deleted, n), Toast.LENGTH_SHORT
                ).show()
                exitGoalSelectMode()
                refresh()
            }
            .setNegativeButton(R.string.sel_cancel, null)
            .show()
    }

    private fun showGoalDialog(g: Goal) {
        val database = db ?: return
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_goal, null)
        val info: TextView = view.findViewById(R.id.tv_goal_info)
        val et: EditText = view.findViewById(R.id.et_goal_amount)
        et.addTextChangedListener(RupiahTextWatcher(et))

        val renderInfo = {
            info.text = getString(
                R.string.goal_saved_of,
                FormatHelper.formatRupiah(g.saved),
                FormatHelper.formatRupiah(g.target)
            )
        }
        renderInfo()

        // Tanggal target + sync kalender
        val dateBtn: Button = view.findViewById(R.id.btn_goal_date_edit)
        var keptDate = g.targetDate
        val renderDate = {
            val d = DateCountdownUtil.formatDate(keptDate)
            dateBtn.text = if (d == null) getString(R.string.goal_date_pick)
            else getString(R.string.goal_date_summary, d)
        }
        renderDate()
        dateBtn.setOnClickListener {
            showDatePicker(keptDate) { iso ->
                keptDate = iso
                renderDate()
            }
        }
        val swEdit: SwitchMaterial = view.findViewById(R.id.sw_goal_sync_edit)
        swEdit.isChecked = g.calendarEventId != null
        dialogSyncSwitch = swEdit
        swEdit.setOnCheckedChangeListener { _, checked ->
            if (checked && !calendarPermGranted()) {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                    REQ_CAL_PERM)
            }
        }
        val remEdit = g.reminderList().toMutableSet()
        bindReminderChips(view.findViewById(R.id.chip_goal_rem_edit), remEdit, REM_CHIPS_EDIT)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(g.name)
            .setView(view)
            .setNegativeButton(R.string.btn_close, null)
            .create()

        view.findViewById<View>(R.id.btn_goal_add).setOnClickListener {
            val amt = RupiahTextWatcher.parseAmount(et.text.toString())
            if (amt <= 0) {
                toast("Isi jumlah dulu")
                return@setOnClickListener
            }
            g.saved = g.saved + amt
            database.updateGoal(g)
            et.setText("")
            renderInfo()
            refresh()
        }
        view.findViewById<View>(R.id.btn_goal_withdraw).setOnClickListener {
            val amt = RupiahTextWatcher.parseAmount(et.text.toString())
            if (amt <= 0) {
                toast("Isi jumlah dulu")
                return@setOnClickListener
            }
            g.saved = Math.max(g.saved - amt, 0.0)
            database.updateGoal(g)
            et.setText("")
            renderInfo()
            refresh()
        }
        view.findViewById<View>(R.id.btn_goal_target).setOnClickListener {
            val amt = RupiahTextWatcher.parseAmount(et.text.toString())
            if (amt <= 0) {
                toast("Isi target baru dulu")
                return@setOnClickListener
            }
            g.target = amt
            database.updateGoal(g)
            et.setText("")
            renderInfo()
            refresh()
            toast("Target diperbarui")
        }
        view.findViewById<View>(R.id.btn_goal_save).setOnClickListener {
            val syncOn = swEdit.isChecked
            var eventId = g.calendarEventId
            if (syncOn) {
                if (keptDate == null) {
                    toast(getString(R.string.goal_cal_date_required))
                    return@setOnClickListener
                }
                if (!calendarPermGranted()) {
                    swEdit.isChecked = false
                    toast(getString(R.string.goal_cal_perm_denied))
                    return@setOnClickListener
                }
                val title = "Rencana Dana: ${g.name}"
                if (eventId != null) {
                    CalendarSyncUtil.updateEvent(requireContext(), eventId, title, keptDate, remEdit.toList())
                } else {
                    eventId = CalendarSyncUtil.insertEvent(
                        requireContext(), title, keptDate, remEdit.toList())
                }
            } else if (eventId != null) {
                CalendarSyncUtil.deleteEvent(requireContext(), eventId)
                eventId = null
            }
            g.targetDate = keptDate
            g.calendarEventId = eventId
            g.reminderDays = remEdit.sorted().joinToString(",")
            database.updateGoal(g)
            dialog.dismiss()
            refresh()
            toast("Tanggal & kalender disimpan")
        }
        view.findViewById<View>(R.id.btn_goal_delete).setOnClickListener {
            CalendarSyncUtil.deleteEvent(requireContext(), g.calendarEventId)
            database.deleteGoal(g.id)
            dialog.dismiss()
            refresh()
            toast("Rencana " + g.name + " dihapus")
        }

        dialog.show()
    }

    private fun showLimitDialog(b: Budget) {
        val database = db ?: return
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_budget_limit, null)
        val et: EditText = view.findViewById(R.id.et_limit)
        et.addTextChangedListener(RupiahTextWatcher(et))
        et.setText(b.limit.toLong().toString())
        et.setSelection(et.text.length)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.budget_limit_title_dialog, b.category))
            .setView(view)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val limit = RupiahTextWatcher.parseAmount(et.text.toString())
                if (limit <= 0) {
                    toast("Limit harus lebih besar dari 0")
                    return@setPositiveButton
                }
                database.upsertBudget(b.category, limit)
                refresh()
            }
            .setNeutralButton(R.string.btn_delete) { _, _ ->
                database.deleteBudget(b.category)
                refresh()
                toast("Anggaran " + b.category + " dihapus")
            }
            .setNegativeButton(R.string.btn_close, null)
            .show()
    }

    override fun refresh() {
        val database = db ?: return
        if (!isAdded) return
        exitGoalSelectMode()
        val listAdapter = adapter
        if (listAdapter != null) {
            listAdapter.setData(database.getAllBudgets())
            tvEmpty?.visibility =
                if (listAdapter.itemCount == 0) View.VISIBLE else View.GONE
        }
        val ga = goalAdapter
        if (ga != null) {
            ga.setData(database.getAllGoals())
            tvGoalEmpty?.visibility =
                if (ga.itemCount == 0) View.VISIBLE else View.GONE
        }
        etCategory?.let {
            val a = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                TransactionSheet.allCategories(database)
            )
            it.setAdapter(a)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
