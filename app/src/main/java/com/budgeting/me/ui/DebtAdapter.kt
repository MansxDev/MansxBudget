package com.budgeting.me.ui

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.budgeting.me.R
import com.budgeting.me.model.Debt
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.ThemeHelper
import com.google.android.material.progressindicator.LinearProgressIndicator

/** List hutang/piutang: progress dibayar/total + status Lunas/Sisa + badge jenis. */
class DebtAdapter(
    private val listener: ((Debt) -> Unit)?
) : RecyclerView.Adapter<DebtAdapter.Holder>() {

    private val items = mutableListOf<Debt>()
    private val selected = mutableSetOf<Long>()
    private var longListener: ((Debt) -> Unit)? = null
    private var selectListener: ((Int) -> Unit)? = null
    private var inSelectMode = false

    fun setOnItemLongClick(l: ((Debt) -> Unit)?) {
        longListener = l
    }

    fun setOnSelectChange(l: ((Int) -> Unit)?) {
        selectListener = l
    }

    fun setData(data: List<Debt>?) {
        items.clear()
        if (data != null) items.addAll(data)
        if (!inSelectMode) selected.clear()
        notifyDataSetChanged()
    }

    fun setSelectMode(on: Boolean) {
        inSelectMode = on
        if (!on) selected.clear()
        notifyDataSetChanged()
    }

    fun isSelectMode(): Boolean = inSelectMode

    fun toggleSelected(id: Long) {
        if (selected.contains(id)) selected.remove(id)
        else selected.add(id)
        notifyDataSetChanged()
    }

    fun selectAll(all: Boolean) {
        selected.clear()
        if (all) {
            for (d in items) selected.add(d.id)
        }
        notifyDataSetChanged()
    }

    fun getSelected(): List<Debt> {
        val out = ArrayList<Debt>()
        for (d in items) {
            if (selected.contains(d.id)) out.add(d)
        }
        return out
    }

    fun getSelectedCount(): Int = selected.size

    fun getItem(pos: Int): Debt? = items.getOrNull(pos)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_debt, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val d = items[position]
        val ctx = h.itemView.context
        val dark = ThemeHelper.isDark(ctx)

        h.tvKind.text = ctx.getString(
            if (d.isMine()) R.string.debt_filter_mine else R.string.debt_filter_theirs
        )
        h.tvKind.setBackgroundResource(
            if (d.isMine()) {
                if (dark) R.drawable.bg_badge_expense_dark else R.drawable.bg_badge_expense
            } else {
                if (dark) R.drawable.bg_badge_income_dark else R.drawable.bg_badge_income
            }
        )
        val badgeText = if (dark) {
            if (d.isMine()) 0xFFF87171.toInt() else 0xFF0FBFA0.toInt()
        } else {
            ctx.resources.getColor(
                if (d.isMine()) R.color.expense_red else R.color.income_green
            )
        }
        h.tvKind.setTextColor(badgeText)

        h.tvName.text = d.name
        val sub = StringBuilder()
        if (d.purpose.isNotBlank()) sub.append(d.purpose)
        val date = d.date
        if (!date.isNullOrBlank()) {
            if (sub.isNotEmpty()) sub.append(" • ")
            sub.append(date)
        }
        h.tvSub.text = sub.toString()
        h.tvSub.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE

        h.tvDetail.text = ctx.getString(
            R.string.debt_paid_of,
            FormatHelper.formatCompact(d.paid),
            FormatHelper.formatCompact(d.total)
        )
        val green = if (dark) 0xFF0FBFA0.toInt()
            else ctx.resources.getColor(R.color.income_green)
        if (d.isDone()) {
            h.tvStatus.setText(R.string.debt_done)
            h.tvStatus.setTextColor(green)
        } else {
            h.tvStatus.text = ctx.getString(
                R.string.debt_remaining,
                FormatHelper.formatCompact(d.remaining())
            )
            h.tvStatus.setTextColor(ThemeHelper.warningColor(h.itemView.context))
        }
        h.bar.setIndicatorColor(green)
        val anim = ObjectAnimator.ofInt(
            h.bar, "progress", h.bar.progress,
            Math.round(d.getRatio() * 100)
        )
        anim.duration = 600
        anim.start()

        h.itemView.setOnClickListener {
            if (inSelectMode) {
                toggleSelected(d.id)
                selectListener?.invoke(getSelectedCount())
            } else {
                listener?.invoke(d)
            }
        }

        h.itemView.setOnLongClickListener {
            val l = longListener
            if (l != null) {
                l(d)
                true
            } else {
                false
            }
        }

        h.cbSelect.visibility = if (inSelectMode) View.VISIBLE else View.GONE
        h.cbSelect.isChecked = selected.contains(d.id)
    }

    override fun getItemCount(): Int = items.size

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val cbSelect: CheckBox = v.findViewById(R.id.cb_debt_select)
        val tvKind: TextView = v.findViewById(R.id.tv_debt_kind)
        val tvName: TextView = v.findViewById(R.id.tv_debt_name)
        val tvStatus: TextView = v.findViewById(R.id.tv_debt_status)
        val tvSub: TextView = v.findViewById(R.id.tv_debt_sub)
        val tvDetail: TextView = v.findViewById(R.id.tv_debt_detail)
        val bar: LinearProgressIndicator = v.findViewById(R.id.debt_bar)
    }
}
