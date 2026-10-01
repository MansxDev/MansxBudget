package com.budgeting.me.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.budgeting.me.R
import com.budgeting.me.model.Transaction
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.ThemeHelper

class TransactionAdapter(
    private val listener: ((Transaction) -> Unit)?
) : RecyclerView.Adapter<TransactionAdapter.Holder>() {

    private val items = mutableListOf<Transaction>()
    private val selected = mutableSetOf<Long>()
    private var longListener: ((Transaction) -> Unit)? = null
    private var selectListener: ((Int) -> Unit)? = null
    private var inSelectMode = false
    private var lastAnimated = -1

    fun setOnItemLongClick(l: ((Transaction) -> Unit)?) {
        longListener = l
    }

    fun setOnSelectChange(l: ((Int) -> Unit)?) {
        selectListener = l
    }

    fun setData(data: List<Transaction>?) {
        items.clear()
        if (data != null) items.addAll(data)
        selected.clear()
        inSelectMode = false
        lastAnimated = -1
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
            for (t in items) selected.add(t.id)
        }
        notifyDataSetChanged()
    }

    fun getSelected(): List<Transaction> {
        val out = ArrayList<Transaction>()
        for (t in items) {
            if (selected.contains(t.id)) out.add(t)
        }
        return out
    }

    fun getSelectedCount(): Int = selected.size

    fun isSelected(id: Long): Boolean = selected.contains(id)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val t = items[position]
        val dark = ThemeHelper.isDark(h.itemView.context)
        h.tvTitle.text = t.title

        val sub = StringBuilder()
        sub.append(t.category ?: "").append(" • ").append(t.date ?: "")
        if (t.isRecurringTemplate()) sub.append(" • ").append(freqLabel(t.recurring))
        if (t.auto == 1) sub.append(" • otomatis")
        h.tvSub.text = sub.toString()

        if (t.isIncome()) {
            h.tvBadge.text = "IN"
            h.tvBadge.setBackgroundResource(
                if (dark) R.drawable.bg_badge_income_dark else R.drawable.bg_badge_income
            )
            h.tvBadge.setTextColor(
                if (dark) 0xFF0FBFA0.toInt()
                else h.itemView.resources.getColor(R.color.income_green)
            )
            h.tvAmount.text = "+" + FormatHelper.formatRupiah(t.amount)
            h.tvAmount.setTextColor(
                if (dark) 0xFF0FBFA0.toInt()
                else h.itemView.resources.getColor(R.color.income_green)
            )
        } else {
            h.tvBadge.text = "OUT"
            h.tvBadge.setBackgroundResource(
                if (dark) R.drawable.bg_badge_expense_dark else R.drawable.bg_badge_expense
            )
            h.tvBadge.setTextColor(
                if (dark) 0xFFF87171.toInt()
                else h.itemView.resources.getColor(R.color.expense_red)
            )
            h.tvAmount.text = "-" + FormatHelper.formatRupiah(t.amount)
            h.tvAmount.setTextColor(
                if (dark) 0xFFF87171.toInt()
                else h.itemView.resources.getColor(R.color.expense_red)
            )
        }

        h.itemView.setOnClickListener { v ->
            v.startAnimation(AnimationUtils.loadAnimation(v.context, R.anim.scale_in))
            if (inSelectMode) {
                toggleSelected(t.id)
                selectListener?.invoke(getSelectedCount())
            } else {
                listener?.invoke(t)
            }
        }

        h.itemView.setOnLongClickListener {
            val l = longListener
            if (l != null) {
                l(t)
                true
            } else {
                false
            }
        }

        h.cbSelect.visibility = if (inSelectMode) View.VISIBLE else View.GONE
        h.cbSelect.isChecked = selected.contains(t.id)

        if (position > lastAnimated) {
            lastAnimated = position
            h.itemView.startAnimation(
                AnimationUtils.loadAnimation(h.itemView.context, R.anim.item_slide_in)
            )
        }
    }

    override fun getItemCount(): Int = items.size

    fun getItem(position: Int): Transaction? {
        if (position < 0 || position >= items.size) return null
        return items[position]
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val cbSelect: CheckBox = v.findViewById(R.id.cb_select)
        val tvBadge: TextView = v.findViewById(R.id.tv_badge)
        val tvTitle: TextView = v.findViewById(R.id.tv_title)
        val tvSub: TextView = v.findViewById(R.id.tv_sub)
        val tvAmount: TextView = v.findViewById(R.id.tv_amount)
    }

    companion object {
        @JvmStatic
        fun freqLabel(freq: String?): String {
            if (Transaction.FREQ_DAILY.equals(freq, ignoreCase = true)) return "Harian"
            if (Transaction.FREQ_WEEKLY.equals(freq, ignoreCase = true)) return "Mingguan"
            if (Transaction.FREQ_MONTHLY.equals(freq, ignoreCase = true)) return "Bulanan"
            return "Sekali"
        }
    }
}
