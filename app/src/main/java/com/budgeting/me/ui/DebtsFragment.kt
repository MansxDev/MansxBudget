package com.budgeting.me.ui

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
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
import com.budgeting.me.model.Debt
import com.budgeting.me.util.FormatHelper
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Tab Hutang & Piutang: ringkasan + cari + filter + kelola catatan. */
class DebtsFragment : Fragment(), TabRefreshable {

    private var db: DatabaseHelper? = null
    private var adapter: DebtAdapter? = null
    private var tvEmpty: TextView? = null
    private var tvSumMine: TextView? = null
    private var tvSumTheirs: TextView? = null
    private var tvSumActive: TextView? = null

    private var search: String? = null
    private var kind: String? = null // null = semua
    private var doneOnly: Boolean? = null // null = semua

    private var selBar: LinearLayout? = null
    private var tvSelCount: TextView? = null
    private var btnSelDelete: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_debts, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val database = DatabaseHelper(requireContext())
        db = database

        tvSumMine = v.findViewById(R.id.tv_sum_mine)
        tvSumTheirs = v.findViewById(R.id.tv_sum_theirs)
        tvSumActive = v.findViewById(R.id.tv_sum_active)

        val rv: RecyclerView = v.findViewById(R.id.rv_debts)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val listAdapter = DebtAdapter { d -> openDebtEditor(d.id) }
        adapter = listAdapter
        listAdapter.setOnItemLongClick { d -> enterSelectMode(d.id) }
        listAdapter.setOnSelectChange { count -> updateSelBar(count) }
        rv.adapter = listAdapter
        attachSwipe(rv)
        tvEmpty = v.findViewById(R.id.tv_debt_empty)
        val swipeLayout: SwipeRefreshLayout = v.findViewById(R.id.swipe_debt)
        swipeLayout.setOnRefreshListener {
            refresh()
            swipeLayout.isRefreshing = false
        }

        v.findViewById<View>(R.id.btn_add_debt).setOnClickListener {
            val s = DebtSheet.forAdd()
            s.setOnSaved(object : DebtSheet.OnSaved {
                override fun onSaved() {
                    apply()
                    (requireActivity() as MainActivity).refreshAllTabs()
                }
            })
            s.show(parentFragmentManager, "debt_sheet")
        }

        val searchView: SearchView = v.findViewById(R.id.search_debt)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean = false
            override fun onQueryTextChange(q: String?): Boolean {
                search = if (q == null || q.trim().isEmpty()) null else q
                apply()
                return true
            }
        })

        val chipKind: ChipGroup = v.findViewById(R.id.chip_group_kind)
        chipKind.setOnCheckedStateChangeListener { _, ids ->
            kind = if (ids.contains(R.id.chip_kind_mine)) Debt.KIND_MINE
                else if (ids.contains(R.id.chip_kind_theirs)) Debt.KIND_THEIRS
                else null
            apply()
        }

        val chipStatus: ChipGroup = v.findViewById(R.id.chip_group_status)
        chipStatus.setOnCheckedStateChangeListener { _, ids ->
            doneOnly = if (ids.contains(R.id.chip_status_paid)) true
                else if (ids.contains(R.id.chip_status_unpaid)) false
                else null
            apply()
        }

        selBar = v.findViewById(R.id.sel_bar_debt)
        tvSelCount = v.findViewById(R.id.tv_sel_count_debt)
        val selDelete: Button = v.findViewById(R.id.btn_sel_delete_debt)
        btnSelDelete = selDelete
        v.findViewById<View>(R.id.btn_sel_all_debt).setOnClickListener {
            val a = adapter ?: return@setOnClickListener
            if (a.getSelectedCount() >= a.itemCount) a.selectAll(false)
            else a.selectAll(true)
            updateSelBar(a.getSelectedCount())
        }
        v.findViewById<View>(R.id.btn_sel_delete_debt).setOnClickListener { confirmDeleteSelected() }
        v.findViewById<View>(R.id.btn_sel_cancel_debt).setOnClickListener { exitSelectMode() }

        apply()
    }

    private fun openDebtEditor(id: Long) {
        val s = DebtSheet.forEdit(id)
        s.setOnSaved(object : DebtSheet.OnSaved {
            override fun onSaved() {
                apply()
                (requireActivity() as MainActivity).refreshAllTabs()
            }
        })
        s.show(parentFragmentManager, "debt_sheet")
    }

    // ---------- select mode ----------

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
            .setTitle(R.string.debt_sel_confirm_title)
            .setMessage(getString(R.string.debt_sel_confirm_msg, sel.size))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                var n = 0
                for (d in sel) {
                    if (database.deleteDebt(d.id) > 0) n++
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.debt_sel_deleted, n), Toast.LENGTH_SHORT
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
                    val d = a.getItem(pos)
                    if (d == null) {
                        a.notifyItemChanged(pos)
                        return
                    }
                    if (dir == ItemTouchHelper.LEFT) {
                        a.notifyItemChanged(pos)
                        openDebtEditor(d.id)
                    } else {
                        confirmSwipeDelete(d, pos)
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
        if (dX < 0) {
            bg.color = 0xFF0FBFA0.toInt()
            bg.setBounds(item.right + dX.toInt(), item.top, item.right, item.bottom)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_edit)
            if (icon != null) {
                icon.setBounds(
                    item.right - margin - iconSize, top,
                    item.right - margin, top + iconSize
                )
            }
        } else if (dX > 0) {
            bg.color = 0xFFE02424.toInt()
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

    private fun confirmSwipeDelete(d: Debt, pos: Int) {
        val database = db ?: return
        val a = adapter ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.debt_sel_confirm_title)
            .setMessage(d.name)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                database.deleteDebt(d.id)
                Toast.makeText(requireContext(), R.string.debt_deleted, Toast.LENGTH_SHORT).show()
                apply()
                (requireActivity() as MainActivity).refreshAllTabs()
            }
            .setNegativeButton(R.string.sel_cancel) { _, _ -> a.notifyItemChanged(pos) }
            .setOnCancelListener { a.notifyItemChanged(pos) }
            .show()
    }

    private fun apply() {
        val database = db ?: return
        val a = adapter ?: return
        if (!isAdded) return
        exitSelectMode()
        val data = database.queryDebts(search, kind, doneOnly, null, null)
        a.setData(data)
        tvEmpty?.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
        val totalMine = database.getTotalDebtByKind(Debt.KIND_MINE)
        val paidMine = database.getTotalPaidByKind(Debt.KIND_MINE)
        val totalTheirs = database.getTotalDebtByKind(Debt.KIND_THEIRS)
        val paidTheirs = database.getTotalPaidByKind(Debt.KIND_THEIRS)
        tvSumMine?.text = FormatHelper.formatCompact(totalMine - paidMine)
        tvSumTheirs?.text = FormatHelper.formatCompact(totalTheirs - paidTheirs)
        tvSumActive?.text = FormatHelper.formatCompact(
            (totalMine - paidMine) + (totalTheirs - paidTheirs)
        )
    }

    override fun refresh() {
        if (!isAdded) return
        apply()
    }
}
