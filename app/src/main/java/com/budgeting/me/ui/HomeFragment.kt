package com.budgeting.me.ui

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.budgeting.me.MainActivity
import com.budgeting.me.R
import com.budgeting.me.data.DatabaseHelper
import com.budgeting.me.util.DateCountdownUtil
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.HomePrefs
import com.budgeting.me.util.ThemeHelper
import com.google.android.material.card.MaterialCardView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeFragment : Fragment(), TabRefreshable {

    private var db: DatabaseHelper? = null
    private var adapter: TransactionAdapter? = null

    private var tvBalance: TextView? = null
    private var tvIncome: TextView? = null
    private var tvExpense: TextView? = null
    private var cardEnvelope: MaterialCardView? = null
    private var cardInsight: MaterialCardView? = null
    private var cardAlert: MaterialCardView? = null
    private var tvEnvelopeAmount: TextView? = null
    private var tvEnvelopeSub: TextView? = null
    private var tvInsight: TextView? = null
    private var tvAlertMsg: TextView? = null
    private var tvProjection: TextView? = null
    private var btnSeeAll: Button? = null
    private var cardGoalNext: MaterialCardView? = null
    private var tvGoalNextName: TextView? = null
    private var tvGoalNextCount: TextView? = null
    // Widget toggle "Atur Beranda" (v4.3)
    private var cardSaldo: View? = null
    private var cardQuickAction: MaterialCardView? = null
    private var cardChart: MaterialCardView? = null
    private var cardPlan: MaterialCardView? = null
    private var cardCalendar: MaterialCardView? = null
    private var cardLastTrx: LinearLayout? = null
    private var rvRecent: RecyclerView? = null
    private var rvCalendar: RecyclerView? = null
    private var tvCalendarTitle: TextView? = null
    private var tvSelectedDate: TextView? = null
    private var calendarAdapter: CalendarDayAdapter? = null
    private var displayedMonth: YearMonth = YearMonth.now()
    private var lastBalance = 0.0

    /** Tanggal aktif untuk filter data budgeting. Default: hari ini. */
    private var activeDate: LocalDate = LocalDate.now()
    private val dateFormatter =
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID"))

    /** Tanggal aktif saat ini, siap dipakai untuk memfilter data. */
    fun getActiveDate(): LocalDate = activeDate

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val database = DatabaseHelper(requireContext())
        db = database
        tvBalance = v.findViewById(R.id.tv_balance)
        tvIncome = v.findViewById(R.id.tv_income)
        tvExpense = v.findViewById(R.id.tv_expense)
        cardEnvelope = v.findViewById(R.id.cardBudget)
        tvEnvelopeAmount = v.findViewById(R.id.tv_envelope_amount)
        tvEnvelopeSub = v.findViewById(R.id.tv_envelope_sub)
        cardInsight = v.findViewById(R.id.card_insight)
        tvInsight = v.findViewById(R.id.tv_insight)
        cardAlert = v.findViewById(R.id.card_alert)
        tvAlertMsg = v.findViewById(R.id.tv_alert_msg)
        tvProjection = v.findViewById(R.id.tv_projection)
        btnSeeAll = v.findViewById(R.id.btn_see_all)
        cardGoalNext = v.findViewById(R.id.cardPlan)
        tvGoalNextName = v.findViewById(R.id.tv_goal_next_name)
        tvGoalNextCount = v.findViewById(R.id.tv_goal_next_count)
        cardSaldo = v.findViewById(R.id.cardSaldo)
        cardQuickAction = v.findViewById(R.id.cardQuickAction)
        cardChart = v.findViewById(R.id.cardChart)
        cardPlan = v.findViewById(R.id.cardPlan)
        cardCalendar = v.findViewById(R.id.cardCalendar)
        cardLastTrx = v.findViewById(R.id.cardLastTrx)

        val rv: RecyclerView = v.findViewById(R.id.rv_recent)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.isNestedScrollingEnabled = false
        val listAdapter = TransactionAdapter { t ->
            (requireActivity() as MainActivity).openEditor(t.id)
        }
        adapter = listAdapter
        rv.adapter = listAdapter
        rvRecent = rv

        btnSeeAll?.setOnClickListener {
            (requireActivity() as MainActivity).goToTransactions()
        }
        rvCalendar = v.findViewById(R.id.rv_calendar)
        tvCalendarTitle = v.findViewById(R.id.tv_calendar_title)
        tvSelectedDate = v.findViewById(R.id.tv_selected_date)
        val btnPrev: Button = v.findViewById(R.id.btn_prev_month)
        val btnToday: Button = v.findViewById(R.id.btn_today)
        val btnNext: Button = v.findViewById(R.id.btn_next_month)
        rvCalendar?.layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar?.isNestedScrollingEnabled = false
        val calAdapter = CalendarDayAdapter { date ->
            activeDate = date
            displayedMonth = YearMonth.from(date)
            renderCalendar()
        }
        calendarAdapter = calAdapter
        rvCalendar?.adapter = calAdapter
        btnPrev.setOnClickListener {
            displayedMonth = displayedMonth.minusMonths(1)
            renderCalendar()
        }
        btnNext.setOnClickListener {
            displayedMonth = displayedMonth.plusMonths(1)
            renderCalendar()
        }
        btnToday.setOnClickListener {
            activeDate = LocalDate.now()
            displayedMonth = YearMonth.now()
            renderCalendar()
        }
        renderCalendar()
        cardEnvelope?.setOnClickListener {
            if (database.getTotalBudgetLimit() <= 0) {
                (requireActivity() as MainActivity).goToBudget()
            }
        }
        refresh()
    }

    override fun refresh() {
        val database = db ?: return
        if (!isAdded) return
        val income = database.getTotalIncome()
        val expense = database.getTotalExpense()
        val balance = income - expense

        animateBalance(lastBalance, balance)
        lastBalance = balance
        tvIncome?.text = FormatHelper.formatRupiah(income)
        tvExpense?.text = FormatHelper.formatRupiah(expense)

        renderEnvelope()
        renderInsight()
        renderProjection(balance)
        renderAlert()
        renderGoalNext()

        val all = database.getAllTransactions()
        adapter?.setData(if (all.size > 5) all.subList(0, 5) else all)
        applyWidgetVisibility()
    }

    override fun onResume() {
        super.onResume()
        applyWidgetVisibility()
    }

    /** Terapkan preferensi "Atur Beranda": GONE widget yang di-disable. */
    private fun applyWidgetVisibility() {
        val ctx = context ?: return
        val enabled = HomePrefs.enabled(ctx)
        val map = mapOf(
            HomePrefs.W_SALDO to listOf(cardSaldo),
            HomePrefs.W_QUICK to listOf(cardQuickAction),
            HomePrefs.W_PLAN to listOf(cardPlan),
            HomePrefs.W_CALENDAR to listOf(cardCalendar),
            HomePrefs.W_CHART to listOf(cardChart),
            HomePrefs.W_BUDGET to listOf(cardEnvelope),
            HomePrefs.W_LAST_TRX to listOf(cardLastTrx, rvRecent)
        )
        for ((key, views) in map) {
            val on = key in enabled
            for (v in views) {
                if (v == null) continue
                // cardPlan: biarkan renderGoalNext yang memutuskan (adakah goal?),
                // hanya ikut disembunyikan kalau widget-nya nonaktif.
                if (!on || key != HomePrefs.W_PLAN) {
                    v.visibility = if (on) View.VISIBLE else View.GONE
                }
            }
        }
    }

    /** Render kalender full-month 42 sel mulai Senin. */
    private fun renderCalendar() {
        if (!isAdded) return
        val raw = displayedMonth.format(
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID"))
        )
        tvCalendarTitle?.text = raw.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale("id", "ID")) else it.toString()
        }
        val first = displayedMonth.atDay(1)
        val offset = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val start = first.minusDays(offset.toLong())
        val days = (0 until 42).map { i ->
            val d = start.plusDays(i.toLong())
            CalendarDay(d, YearMonth.from(d) == displayedMonth)
        }
        val today = LocalDate.now()
        val horizon = HomePrefs.calendarHorizonDays(requireContext())
        val end = today.plusDays(horizon.toLong())
        val highlight = (db?.getAllGoals().orEmpty())
            .mapNotNull { g ->
                g.targetDate?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }
            }
            .filter { !it.isBefore(today) && !it.isAfter(end) }
            .toSet()
        calendarAdapter?.submitDays(days, activeDate, today, highlight)
        tvSelectedDate?.text =
            getString(R.string.calendar_selected, activeDate.format(dateFormatter))
    }

    private fun animateBalance(from: Double, to: Double) {        val tv = tvBalance ?: return
        val anim = ValueAnimator.ofFloat(from.toFloat(), to.toFloat())
        anim.duration = 800
        anim.addUpdateListener { a ->
            tv.text = FormatHelper.formatRupiah((a.animatedValue as Float).toDouble())
        }
        anim.start()
    }

    /** Envelope harian: sisa budget bulan ini / sisa hari. */
    private fun renderEnvelope() {
        val database = db ?: return
        val tvAmount = tvEnvelopeAmount ?: return
        val tvSub = tvEnvelopeSub ?: return
        val totalLimit = database.getTotalBudgetLimit()
        if (totalLimit <= 0) {
            tvAmount.textSize = 14f
            tvAmount.text = getString(R.string.envelope_empty)
            tvSub.text = ""
            return
        }
        val ym = FormatHelper.yearMonthNow()
        val spent = database.getMonthExpense(ym)
        val remaining = totalLimit - spent
        val daysLeft = FormatHelper.daysLeftInMonth()
        val daily = if (daysLeft > 0) remaining / daysLeft else remaining

        tvAmount.textSize = 24f
        tvAmount.text = FormatHelper.formatRupiah(Math.max(daily, 0.0))
        tvSub.text = getString(
            R.string.envelope_days_left, daysLeft,
            FormatHelper.formatRupiah(Math.max(remaining, 0.0))
        )
        val color = if (remaining < 0) 0xFFF87171.toInt()
            else if (spent / totalLimit >= 0.8) ThemeHelper.warningColor(requireContext())
            else 0xFF0FBFA0.toInt()
        tvAmount.setTextColor(color)
    }

    private fun renderGoalNext() {
        val database = db ?: return
        val card = cardGoalNext ?: return
        val goals = database.getAllGoals()
            .filter { !it.isDone() && !it.targetDate.isNullOrBlank() }
        val next = goals.minByOrNull { it.targetDate!! }
        if (next == null) {
            card.visibility = View.GONE
            return
        }
        tvGoalNextName?.text = next.name
        val label = DateCountdownUtil.countdownLabel(next.targetDate)
        tvGoalNextCount?.text = label
        tvGoalNextCount?.setTextColor(
            if (label != null && label.startsWith("Sudah lewat")) {
                ThemeHelper.warningColor(requireContext())
            } else {
                0xFF0FBFA0.toInt()
            }
        )
        card.visibility = View.VISIBLE
    }

    private fun renderInsight() {
        val database = db ?: return
        val tv = tvInsight ?: return
        val w = FormatHelper.weekWindows()
        val thisW = database.getExpenseBetween(w[0], w[1])
        val prevW = database.getExpenseBetween(w[2], w[3])
        if (thisW <= 0 && prevW <= 0) {
            tv.text = getString(R.string.insight_empty)
            return
        }
        val sb = StringBuilder()
        if (prevW > 0) {
            val pct = Math.round(Math.abs(thisW - prevW) / prevW * 100).toInt()
            if (thisW > prevW * 1.05) {
                sb.append(
                    getString(
                        R.string.insight_up,
                        FormatHelper.formatRupiah(thisW), pct
                    )
                )
            } else if (thisW < prevW * 0.95) {
                sb.append(
                    getString(
                        R.string.insight_down,
                        FormatHelper.formatRupiah(thisW), pct
                    )
                )
            } else {
                sb.append(getString(R.string.insight_flat, FormatHelper.formatRupiah(thisW)))
            }
        } else {
            sb.append(getString(R.string.insight_flat, FormatHelper.formatRupiah(thisW)))
        }
        val top = database.getTopCategoryBetween(w[0], w[1])
        if (top != null && thisW > 0) {
            val share = Math.round(top.total / thisW * 100).toInt()
            sb.append("\n").append(
                getString(
                    R.string.insight_top_cat,
                    top.category, FormatHelper.formatRupiah(top.total), share
                )
            )
        }
        tv.text = sb.toString()
        cardInsight?.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in))
    }

    /** Proyeksi akhir bulan: rata-rata harian x sisa hari vs saldo saat ini. */
    private fun renderProjection(balance: Double) {
        val database = db ?: return
        val tv = tvProjection ?: return
        val spent = database.getMonthExpense(FormatHelper.yearMonthNow())
        if (spent <= 0) {
            tv.text = getString(R.string.projection_empty)
            tv.setTextColor(ThemeHelper.chartTextColor(requireContext()))
            return
        }
        val elapsed = Math.max(FormatHelper.daysElapsedInMonth(), 1)
        val daysLeft = FormatHelper.daysInMonth() - elapsed
        val avg = spent / elapsed
        val projectedTotal = spent + avg * daysLeft
        val rest = balance - projectedTotal

        val main = getString(
            R.string.projection_main,
            FormatHelper.formatRupiah(avg), FormatHelper.formatRupiah(projectedTotal)
        )
        val verdict = if (rest >= 0)
            getString(R.string.projection_ok, FormatHelper.formatRupiah(rest))
        else
            getString(R.string.projection_bad, FormatHelper.formatRupiah(-rest))
        tv.text = "$main\n$verdict"

        val dark = ThemeHelper.isDark(requireContext())
        val okColor = if (dark) 0xFF0FBFA0.toInt() else resources.getColor(R.color.income_green)
        val badColor = if (dark) 0xFFF87171.toInt() else resources.getColor(R.color.expense_red)
        tv.setTextColor(if (rest >= 0) okColor else badColor)
    }

    private fun renderAlert() {
        val database = db ?: return
        val card = cardAlert ?: return
        val tvMsg = tvAlertMsg ?: return
        val totalLimit = database.getTotalBudgetLimit()
        if (totalLimit <= 0) {
            card.visibility = View.GONE
            return
        }
        val spent = database.getMonthExpense(FormatHelper.yearMonthNow())
        if (spent > totalLimit) {
            tvMsg.text = getString(
                R.string.alert_budget_over,
                FormatHelper.formatRupiah(spent), FormatHelper.formatRupiah(totalLimit)
            )
            showAlert()
        } else if (spent / totalLimit >= 0.8) {
            tvMsg.text = getString(
                R.string.alert_budget_near,
                Math.round(spent / totalLimit * 100).toInt()
            )
            showAlert()
        } else {
            card.visibility = View.GONE
        }
    }

    private fun showAlert() {
        val card = cardAlert ?: return
        if (card.visibility != View.VISIBLE) {
            card.visibility = View.VISIBLE
            card.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.slide_down)
            )
        }
    }
}
