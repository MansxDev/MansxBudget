package com.budgeting.me.ui

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.budgeting.me.R
import com.budgeting.me.model.Goal
import com.budgeting.me.util.DateCountdownUtil
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.ThemeHelper
import com.google.android.material.progressindicator.LinearProgressIndicator

class GoalAdapter(
    private val listener: ((Goal) -> Unit)?
) : RecyclerView.Adapter<GoalAdapter.Holder>() {

    private val items = mutableListOf<Goal>()
    private val selected = mutableSetOf<Long>()
    private var longListener: ((Goal) -> Unit)? = null
    private var selectListener: ((Int) -> Unit)? = null
    private var inSelectMode = false

    fun setOnItemLongClick(l: ((Goal) -> Unit)?) {
        longListener = l
    }

    fun setOnSelectChange(l: ((Int) -> Unit)?) {
        selectListener = l
    }

    fun setData(data: List<Goal>?) {
        items.clear()
        if (data != null) items.addAll(data)
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
            for (g in items) selected.add(g.id)
        }
        notifyDataSetChanged()
    }

    fun getSelected(): List<Goal> {
        val out = ArrayList<Goal>()
        for (g in items) {
            if (selected.contains(g.id)) out.add(g)
        }
        return out
    }

    fun getSelectedCount(): Int = selected.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_goal, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val g = items[position]
        val dark = ThemeHelper.isDark(h.itemView.context)

        h.tvCat.text = g.name
        h.tvDetail.text = h.itemView.context.getString(
            R.string.goal_saved_of,
            FormatHelper.formatCompact(g.saved),
            FormatHelper.formatCompact(g.target)
        )
        val green = if (dark) 0xFF0FBFA0.toInt()
            else h.itemView.resources.getColor(R.color.income_green)
        if (g.isDone()) {
            h.tvStatus.setText(R.string.goal_done)
            h.tvStatus.setTextColor(green)
        } else {
            h.tvStatus.text = h.itemView.context.getString(
                R.string.goal_remaining,
                FormatHelper.formatCompact(g.target - g.saved)
            )
            h.tvStatus.setTextColor(ThemeHelper.warningColor(h.itemView.context))
        }
        h.bar.setIndicatorColor(green)
        val anim = ObjectAnimator.ofInt(
            h.bar, "progress", h.bar.progress,
            Math.round(g.getRatio() * 100)
        )
        anim.duration = 600
        anim.start()

        val cdDate = DateCountdownUtil.formatDate(g.targetDate)
        val cdLabel = DateCountdownUtil.countdownLabel(g.targetDate)
        if (cdLabel == null) {
            h.tvCountdown.visibility = View.GONE
        } else {
            h.tvCountdown.visibility = View.VISIBLE
            h.tvCountdown.text = if (cdDate == null) cdLabel else "$cdDate · $cdLabel"
            h.tvCountdown.setTextColor(
                if (cdLabel.startsWith("Sudah lewat")) {
                    ThemeHelper.warningColor(h.itemView.context)
                } else {
                    green
                }
            )
        }

        h.itemView.setOnClickListener {
            if (inSelectMode) {
                toggleSelected(g.id)
                selectListener?.invoke(getSelectedCount())
            } else {
                listener?.invoke(g)
            }
        }

        h.itemView.setOnLongClickListener {
            val l = longListener
            if (l != null) {
                l(g)
                true
            } else {
                false
            }
        }

        h.cbSelect.visibility = if (inSelectMode) View.VISIBLE else View.GONE
        h.cbSelect.isChecked = selected.contains(g.id)
    }

    override fun getItemCount(): Int = items.size

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val cbSelect: CheckBox = v.findViewById(R.id.cb_goal_select)
        val tvCat: TextView = v.findViewById(R.id.tv_gname)
        val tvDetail: TextView = v.findViewById(R.id.tv_gdetail)
        val tvStatus: TextView = v.findViewById(R.id.tv_gstatus)
        val bar: LinearProgressIndicator = v.findViewById(R.id.goal_bar)
        val tvCountdown: TextView = v.findViewById(R.id.tv_gcountdown)
    }
}
