package com.budgeting.me.ui

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.budgeting.me.R
import java.time.LocalDate

data class CalendarDay(val date: LocalDate, val inMonth: Boolean)

class CalendarDayAdapter(
    private val onDayClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<CalendarDayAdapter.Holder>() {

    private var days: List<CalendarDay> = emptyList()
    private var selected: LocalDate = LocalDate.now()
    private var today: LocalDate = LocalDate.now()
    private var highlight: Set<LocalDate> = emptySet()

    fun submitDays(
        days: List<CalendarDay>,
        selected: LocalDate,
        today: LocalDate,
        highlight: Set<LocalDate> = emptySet()
    ) {
        this.days = days
        this.selected = selected
        this.today = today
        this.highlight = highlight
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return Holder(v)
    }

    override fun getItemCount(): Int = days.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = days[position]
        val ctx = holder.itemView.context
        holder.tvDay.text = item.date.dayOfMonth.toString()
        holder.tvDay.alpha = if (item.inMonth) 1f else 0.35f
        if (item.date == selected) {
            holder.tvDay.background =
                ContextCompat.getDrawable(ctx, R.drawable.bg_calendar_selected)
            holder.tvDay.setTextColor(0xFFFFFFFF.toInt())
            holder.tvDay.setTypeface(holder.tvDay.typeface, Typeface.BOLD)
        } else {
            holder.tvDay.setBackgroundColor(0x00000000)
            if (item.date == today) {
                holder.tvDay.setTextColor(resolveAttr(ctx, com.google.android.material.R.attr.colorPrimary))
                holder.tvDay.setTypeface(holder.tvDay.typeface, Typeface.BOLD)
            } else {
                holder.tvDay.setTextColor(
                    resolveAttr(ctx, com.google.android.material.R.attr.colorOnSurface)
                )
                holder.tvDay.setTypeface(null, Typeface.NORMAL)
            }
        }
        holder.itemView.setOnClickListener { onDayClick(item.date) }
        // Highlight tanggal target Goal dalam horizon kalender (v4.3).
        if (item.date in highlight && item.date != selected) {
            holder.tvDay.background =
                ContextCompat.getDrawable(ctx, R.drawable.bg_calendar_goal)
            holder.tvDay.setTypeface(holder.tvDay.typeface, Typeface.BOLD)
        }
    }

    private fun resolveAttr(ctx: android.content.Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val tvDay: TextView = v.findViewById(R.id.tv_day)
    }
}
