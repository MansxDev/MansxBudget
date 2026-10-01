package com.budgeting.me.ui

import android.app.DatePickerDialog
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.budgeting.me.MainActivity
import com.budgeting.me.R
import com.budgeting.me.data.DatabaseHelper
import com.budgeting.me.model.Transaction
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.RupiahTextWatcher
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.ArrayList
import java.util.Calendar

class TransactionsFragment : Fragment(), TabRefreshable {

    private var db: DatabaseHelper? = null
    private var adapter: TransactionAdapter? = null
    private var tvEmpty: TextView? = null
    private var swipe: SwipeRefreshLayout? = null

    private var search: String? = null
    private var type: String? = null // null = semua
    private var category: String? = null
    private var fromIso: String? = null
    private var toIso: String? = null
    private var minAmt: Double? = null
    private var maxAmt: Double? = null

    private var btnFrom: Button? = null
    private var btnTo: Button? = null
    private var spCategory: Spinner? = null
    private var spinnerReady = false

    private var selBar: LinearLayout? = null
    private var tvSelCount: TextView? = null
    private var btnSelDelete: Button? = null
    private var tvCount: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_transactions, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val database = DatabaseHelper(requireContext())
        db = database

        val rv: RecyclerView = v.findViewById(R.id.rv_transactions)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val listAdapter = TransactionAdapter { t ->
            (requireActivity() as MainActivity).openEditor(t.id)
        }
        adapter = listAdapter
        listAdapter.setOnItemLongClick { t -> enterSelectMode(t.id) }
        listAdapter.setOnSelectChange { count -> updateSelBar(count) }
        rv.adapter = listAdapter
        attachSwipe(rv)
        tvEmpty = v.findViewById(R.id.tv_empty)
        tvCount = v.findViewById(R.id.tv_trans_count)
        val swipeLayout: SwipeRefreshLayout = v.findViewById(R.id.swipe)
        swipe = swipeLayout
        swipeLayout.setOnRefreshListener {
            refresh()
            swipeLayout.isRefreshing = false
        }

        val searchView: SearchView = v.findViewById(R.id.search_view)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean = false
            override fun onQueryTextChange(q: String?): Boolean {
                search = if (q == null || q.trim().isEmpty()) null else q
                apply()
                return true
            }
        })

        val chipType: ChipGroup = v.findViewById(R.id.chip_group_type)
        chipType.setOnCheckedStateChangeListener { _, ids ->
            type = if (ids.contains(R.id.chip_in)) DatabaseHelper.TYPE_INCOME
                else if (ids.contains(R.id.chip_out)) DatabaseHelper.TYPE_EXPENSE
                else null
            apply()
        }

        val spinner: Spinner = v.findViewById(R.id.sp_category)
        spCategory = spinner
        rebuildCategorySpinner()
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (!spinnerReady) {
                    spinnerReady = true
                    return
                }
                category = if (pos == 0) null else p?.getItemAtPosition(pos) as? String
                apply()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val fromBtn: Button = v.findViewById(R.id.btn_from)
        val toBtn: Button = v.findViewById(R.id.btn_to)
        btnFrom = fromBtn
        btnTo = toBtn
        fromBtn.setOnClickListener { pickDate(true) }
        toBtn.setOnClickListener { pickDate(false) }

        val etMin: EditText = v.findViewById(R.id.et_min)
        val etMax: EditText = v.findViewById(R.id.et_max)
        etMin.addTextChangedListener(RupiahTextWatcher(etMin))
        etMax.addTextChangedListener(RupiahTextWatcher(etMax))
        val moneyWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                minAmt = parseDouble(etMin.text.toString())
                maxAmt = parseDouble(etMax.text.toString())
                apply()
            }
        }
        etMin.addTextChangedListener(moneyWatcher)
        etMax.addTextChangedListener(moneyWatcher)

        val btnReset: Button = v.findViewById(R.id.btn_reset_filter)
        btnReset.setOnClickListener {
            search = null
            searchView.setQuery("", false)
            type = null
            chipType.check(R.id.chip_all)
            category = null
            spinner.setSelection(0)
            spinnerReady = false
            fromIso = null
            toIso = null
            fromBtn.setText(R.string.filter_from)
            toBtn.setText(R.string.filter_to)
            etMin.setText("")
            etMax.setText("")
            minAmt = null
            maxAmt = null
            exitSelectMode()
            apply()
        }

        selBar = v.findViewById(R.id.sel_bar)
        tvSelCount = v.findViewById(R.id.tv_sel_count)
        val selDelete: Button = v.findViewById(R.id.btn_sel_delete)
        btnSelDelete = selDelete
        v.findViewById<View>(R.id.btn_sel_all).setOnClickListener {
            val a = adapter ?: return@setOnClickListener
            if (a.getSelectedCount() >= a.itemCount) {
                a.selectAll(false)
            } else {
                a.selectAll(true)
            }
            updateSelBar(a.getSelectedCount())
        }
        v.findViewById<View>(R.id.btn_sel_delete).setOnClickListener { confirmDeleteSelected() }
        v.findViewById<View>(R.id.btn_sel_cancel).setOnClickListener { exitSelectMode() }

        v.findViewById<View>(R.id.btn_sel_toggle).setOnClickListener {
            val a = adapter ?: return@setOnClickListener
            if (a.isSelectMode()) exitSelectMode()
            else enterSelectMode(null)
        }

        apply()
    }

    // ---------- select mode (checklist delete) ----------

    private fun enterSelectMode(firstId: Long?) {
        if (!isAdded) return
        val a = adapter ?: return
        a.setSelectMode(true)
        if (firstId != null) a.toggleSelected(firstId)
        val bar = selBar ?: return
        bar.visibility = View.VISIBLE
        bar.startAnimation(
            AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
        )
        val count = a.getSelectedCount()
        tvSelCount?.text = getString(R.string.sel_count, count)
        btnSelDelete?.text = getString(R.string.sel_delete, count)
    }

    private fun exitSelectMode() {
        val a = adapter
        if (a != null && a.isSelectMode()) {
            a.setSelectMode(false)
        }
        selBar?.visibility = View.GONE
    }

    private fun updateSelBar(count: Int) {
        if (!isAdded) return
        if (count <= 0) {
            exitSelectMode()
            return
        }
        tvSelCount?.text = getString(R.string.sel_count, count)
        btnSelDelete?.text = getString(R.string.sel_delete, count)
    }

    private fun confirmDeleteSelected() {
        val database = db ?: return
        val a = adapter ?: return
        val sel = a.getSelected()
        if (sel.isEmpty()) {
            exitSelectMode()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sel_confirm_title)
            .setMessage(getString(R.string.sel_confirm_msg, sel.size))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                var n = 0
                for (t in sel) {
                    if (database.deleteTransaction(t.id) > 0) n++
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sel_deleted, n), Toast.LENGTH_SHORT
                ).show()
                exitSelectMode()
                apply()
                (requireActivity() as MainActivity).refreshAllTabs()
            }
            .setNegativeButton(R.string.sel_cancel, null)
            .show()
    }

    // ---------- swipe: left = edit, right = delete ----------

    private fun attachSwipe(rv: RecyclerView) {
        val helper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            ) {
                override fun onMove(
                    r: RecyclerView,
                    vh: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                override fun getSwipeDirs(r: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                    if (adapter?.isSelectMode() == true) return 0
                    return super.getSwipeDirs(r, vh)
                }

                override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                    val a = adapter ?: return
                    val pos = vh.bindingAdapterPosition
                    val t = a.getItem(pos)
                    if (t == null) {
                        a.notifyItemChanged(pos)
                        return
                    }
                    if (dir == ItemTouchHelper.LEFT) {
                        a.notifyItemChanged(pos)
                        (requireActivity() as MainActivity).openEditor(t.id)
                    } else {
                        confirmSwipeDelete(t, pos)
                    }
                }

                override fun onChildDraw(
                    c: Canvas, r: RecyclerView,
                    vh: RecyclerView.ViewHolder,
                    dX: Float, dY: Float, actionState: Int,
                    isActive: Boolean
                ) {
                    if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                        drawSwipeBg(c, vh.itemView, dX)
                    }
                    super.onChildDraw(c, r, vh, dX, dY, actionState, isActive)
                }
            }
        )
        helper.attachToRecyclerView(rv)
    }

    private fun drawSwipeBg(c: Canvas, item: View, dX: Float) {
        val bg = ColorDrawable()
        val icon: Drawable?
        val density = item.resources.displayMetrics.density
        val margin = (20 * density).toInt()
        val iconSize = (24 * density).toInt()
        val top = item.top + (item.height - iconSize) / 2
        if (dX < 0) { // left: edit (green)
            bg.setColor(0xFF0FBFA0.toInt())
            bg.setBounds(item.right + dX.toInt(), item.top, item.right, item.bottom)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_edit)
            if (icon != null) {
                icon.setBounds(
                    item.right - margin - iconSize, top,
                    item.right - margin, top + iconSize
                )
            }
        } else if (dX > 0) { // right: delete (red)
            bg.setColor(0xFFE02424.toInt())
            bg.setBounds(item.left, item.top, item.left + dX.toInt(), item.bottom)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
            if (icon != null) {
                icon.setBounds(
                    item.left + margin, top,
                    item.left + margin + iconSize, top + iconSize
                )
            }
        } else {
            return
        }
        bg.draw(c)
        icon?.draw(c)
    }

    private fun confirmSwipeDelete(t: Transaction, pos: Int) {
        val database = db ?: return
        val a = adapter ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sel_confirm_title)
            .setMessage(t.title)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                database.deleteTransaction(t.id)
                Toast.makeText(requireContext(), R.string.swipe_deleted, Toast.LENGTH_SHORT).show()
                apply()
                (requireActivity() as MainActivity).refreshAllTabs()
            }
            .setNegativeButton(R.string.sel_cancel) { _, _ -> a.notifyItemChanged(pos) }
            .setOnCancelListener { a.notifyItemChanged(pos) }
            .show()
    }

    private fun pickDate(isFrom: Boolean) {
        val c = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                val iso = FormatHelper.iso(y, m + 1, d)
                if (isFrom) {
                    fromIso = iso
                    btnFrom?.text = FormatHelper.toDisplay(iso)
                } else {
                    toIso = iso
                    btnTo?.text = FormatHelper.toDisplay(iso)
                }
                apply()
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun parseDouble(s: String): Double? {
        return try {
            val d = RupiahTextWatcher.digitsOnly(s)
            if (d.isEmpty()) null else d.toDouble()
        } catch (e: Exception) {
            null
        }
    }

    private fun rebuildCategorySpinner() {
        val database = db ?: return
        val spinner = spCategory ?: return
        val cats = ArrayList<String>()
        cats.add(getString(R.string.filter_category_all))
        cats.addAll(TransactionSheet.allCategories(database))
        val a = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item, cats
        )
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = a
    }

    private fun apply() {
        val database = db ?: return
        val a = adapter ?: return
        if (!isAdded) return
        exitSelectMode()
        val data = database.queryTransactions(search, type, category, fromIso, toIso, minAmt, maxAmt)
        a.setData(data)
        tvEmpty?.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
        tvCount?.text = getString(R.string.trans_count, data.size)
    }

    override fun refresh() {
        if (!isAdded) return
        val spinner = spCategory
        val pos = spinner?.selectedItemPosition ?: 0
        if (spinner != null) {
            rebuildCategorySpinner()
            if (pos < spinner.adapter.count) spinner.setSelection(pos)
        }
        apply()
    }
}
