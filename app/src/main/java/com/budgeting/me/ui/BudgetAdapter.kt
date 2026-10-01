package com.budgeting.me.ui

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.budgeting.me.R
import com.budgeting.me.data.DatabaseHelper
import com.budgeting.me.model.Budget
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.ThemeHelper

class BudgetAdapter(
    private val db: DatabaseHelper,
    private val yearMonth: String,
    private val listener: ((Budget, Double) -> Unit)?
) : RecyclerView.Adapter<BudgetAdapter.Holder>() {

    private val items = mutableListOf<Budget>()

    fun setData(data: List<Budget>?) {
        items.clear()
        if (data != null) items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budget, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val b = items[position]
        val spent = db.getCategoryExpenseMonth(b.category, yearMonth)
        val limit = b.limit
        val ratio = if (limit > 0) (spent / limit).toFloat() else 0f

        h.tvCat.text = b.category
        h.tvDetail.text = h.itemView.context.getString(
            R.string.budget_spent_of,
            FormatHelper.formatCompact(spent), FormatHelper.formatCompact(limit)
        )
        if (spent > limit) {
            h.tvStatus.text = h.itemView.context.getString(
                R.string.budget_over_by,
                FormatHelper.formatCompact(spent - limit)
            )
        } else {
            h.tvStatus.text = h.itemView.context.getString(
                R.string.budget_remaining,
                FormatHelper.formatCompact(limit - spent)
            )
        }
        val color = ThemeHelper.progressColor(h.itemView.context, ratio)
        h.tvStatus.setTextColor(color)
        h.bar.setIndicatorColor(color)
        val anim = ObjectAnimator.ofInt(
            h.bar, "progress", h.bar.progress,
            Math.round(Math.min(ratio, 1f) * 100)
        )
        anim.duration = 600
        anim.start()

        h.itemView.setOnClickListener {
            listener?.invoke(b, spent)
        }
    }

    override fun getItemCount(): Int = items.size

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val tvCat: TextView = v.findViewById(R.id.tv_bcat)
        val tvDetail: TextView = v.findViewById(R.id.tv_bdetail)
        val tvStatus: TextView = v.findViewById(R.id.tv_bstatus)
        val bar: com.google.android.material.progressindicator.LinearProgressIndicator =
            v.findViewById(R.id.budget_bar)
    }
}
