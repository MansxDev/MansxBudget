package com.budgeting.me.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.budgeting.me.R
import com.budgeting.me.data.DatabaseHelper
import com.budgeting.me.util.ExportHelper
import com.budgeting.me.util.FormatHelper
import com.budgeting.me.util.ThemeHelper
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.chip.ChipGroup
import java.util.ArrayList

class ReportFragment : Fragment(), TabRefreshable {

    private var db: DatabaseHelper? = null
    private var pie: PieChart? = null
    private var bar: BarChart? = null
    private var tableRows: LinearLayout? = null
    private var tvPieEmpty: TextView? = null
    private var tvBarEmpty: TextView? = null
    private var scopeMonth = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val database = DatabaseHelper(requireContext())
        db = database
        pie = v.findViewById(R.id.pie_chart)
        bar = v.findViewById(R.id.bar_chart)
        tableRows = v.findViewById(R.id.table_rows)
        tvPieEmpty = v.findViewById(R.id.tv_pie_empty)
        tvBarEmpty = v.findViewById(R.id.tv_bar_empty)

        val scope: ChipGroup = v.findViewById(R.id.chip_group_scope)
        scope.setOnCheckedStateChangeListener { _, ids ->
            scopeMonth = !ids.contains(R.id.chip_all_scope)
            buildTable()
        }

        val btnExport: Button = v.findViewById(R.id.btn_export)
        btnExport.setOnClickListener {
            try {
                val path = ExportHelper.exportCsv(requireContext(), database.getAllTransactions())
                Toast.makeText(
                    requireContext(),
                    getString(R.string.csv_success, path), Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                val msg = if ("empty" == e.message)
                    getString(R.string.csv_empty)
                else
                    getString(R.string.csv_failed, e.message)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        refresh()
    }

    override fun refresh() {
        val database = db ?: return
        if (!isAdded) return
        buildPie()
        buildBar()
        buildTable()
    }

    private fun buildPie() {
        val database = db ?: return
        val chart = pie ?: return
        val emptyTv = tvPieEmpty ?: return
        val ym = FormatHelper.yearMonthNow()
        val sums = database.getCategorySummariesForMonth(ym)
        val entries = ArrayList<PieEntry>()
        for (s in sums) {
            if (s.expense > 0) entries.add(PieEntry(s.expense.toFloat(), s.category))
        }
        if (entries.isEmpty()) {
            chart.visibility = View.GONE
            emptyTv.visibility = View.VISIBLE
            emptyTv.setText(R.string.pie_empty)
            return
        }
        chart.visibility = View.VISIBLE
        emptyTv.visibility = View.GONE

        val set = PieDataSet(entries, "")
        set.setColors(*ThemeHelper.chartPalette(requireContext()))
        set.sliceSpace = 2f
        set.valueTextSize = 12f
        set.valueTextColor = 0xFFFFFFFF.toInt()
        val data = PieData(set)

        chart.data = data
        chart.setUsePercentValues(true)
        chart.description.isEnabled = false
        chart.isDrawHoleEnabled = true
        chart.holeRadius = 46f
        chart.transparentCircleRadius = 52f
        chart.setHoleColor(0x00000000)
        chart.centerText = getString(R.string.pie_center)
        chart.setCenterTextSize(13f)
        chart.setCenterTextColor(ThemeHelper.chartTextColor(requireContext()))
        chart.setCenterTextTypeface(Typeface.DEFAULT_BOLD)
        chart.setEntryLabelColor(0xFFFFFFFF.toInt())
        chart.setEntryLabelTextSize(11f)
        val legend: Legend = chart.legend
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.isWordWrapEnabled = true
        legend.textColor = ThemeHelper.chartTextColor(requireContext())
        legend.textSize = 12f
        chart.animateY(900)
        chart.invalidate()
    }

    private fun buildBar() {
        val database = db ?: return
        val chart = bar ?: return
        val months = FormatHelper.lastMonths(6)
        val totals = database.getMonthlyTotals(months)
        val hasData = totals.any { it.income > 0 || it.expense > 0 }
        val emptyTv = tvBarEmpty
        if (emptyTv != null) {
            emptyTv.visibility = if (hasData) View.GONE else View.VISIBLE
        }
        chart.visibility = if (hasData) View.VISIBLE else View.GONE
        if (!hasData) {
            chart.clear()
            return
        }
        val inEntries = ArrayList<BarEntry>()
        val outEntries = ArrayList<BarEntry>()
        val labels = Array(months.size) { "" }
        for (i in months.indices) {
            val m = totals[i]
            inEntries.add(BarEntry(i.toFloat(), m.income.toFloat()))
            outEntries.add(BarEntry(i.toFloat(), m.expense.toFloat()))
            labels[i] = FormatHelper.shortMonth(m.yearMonth)
        }

        val setIn = BarDataSet(inEntries, getString(R.string.total_income))
        setIn.color = 0xFF0FBFA0.toInt()
        val setOut = BarDataSet(outEntries, getString(R.string.total_expense))
        setOut.color = 0xFFF87171.toInt()
        val compact = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value <= 0) "" else FormatHelper.formatCompact(value.toDouble())
            }
        }
        setIn.valueFormatter = compact
        setOut.valueFormatter = compact
        setIn.valueTextColor = ThemeHelper.chartTextColor(requireContext())
        setOut.valueTextColor = ThemeHelper.chartTextColor(requireContext())
        setIn.valueTextSize = 10f
        setOut.valueTextSize = 10f

        val data = BarData(setIn, setOut)
        val groupSpace = 0.3f
        val barSpace = 0.05f
        val barWidth = 0.3f
        data.barWidth = barWidth
        chart.data = data
        chart.description.isEnabled = false
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)

        val x: XAxis = chart.xAxis
        x.position = XAxis.XAxisPosition.BOTTOM
        x.granularity = 1f
        x.setCenterAxisLabels(true)
        x.valueFormatter = IndexAxisValueFormatter(labels)
        x.textColor = ThemeHelper.chartTextColor(requireContext())
        x.setDrawGridLines(false)
        x.axisMinimum = 0f
        x.axisMaximum = months.size.toFloat()

        val left = chart.axisLeft
        left.textColor = ThemeHelper.chartTextColor(requireContext())
        left.gridColor = ThemeHelper.chartGridColor(requireContext())
        left.valueFormatter = compact
        chart.axisRight.isEnabled = false

        val legend: Legend = chart.legend
        legend.textColor = ThemeHelper.chartTextColor(requireContext())
        legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT

        chart.groupBars(0f, groupSpace, barSpace)
        chart.animateY(900)
        chart.invalidate()
    }

    private fun buildTable() {
        val database = db ?: return
        val rows = tableRows ?: return
        rows.removeAllViews()
        val sums = if (scopeMonth)
            database.getCategorySummariesForMonth(FormatHelper.yearMonthNow())
        else
            database.getCategorySummaries()
        val dark = ThemeHelper.isDark(requireContext())
        val stripe = if (dark) 0xFF1B2530.toInt() else 0xFFF1F5F4.toInt()
        val incomeC = if (dark) 0xFF0FBFA0.toInt() else resources.getColor(R.color.income_green)
        val expenseC = if (dark) 0xFFF87171.toInt() else resources.getColor(R.color.expense_red)
        val netUp = incomeC
        val netDown = expenseC

        for (i in sums.indices) {
            val s = sums[i]
            val row = TableRow(requireContext())
            if (i % 2 == 1) row.setBackgroundColor(stripe)
            row.setPadding(4, 14, 4, 14)

            val tvCat = cell(
                s.category, 110, Gravity.START,
                ThemeHelper.chartTextColor(requireContext()), false, true
            )
            val tvInc = cell(
                FormatHelper.formatCompact(s.income), 80, Gravity.END,
                incomeC, true, true
            )
            val tvExp = cell(
                FormatHelper.formatCompact(s.expense), 80, Gravity.END,
                expenseC, true, true
            )
            val netColor = if (s.net >= 0) netUp else netDown
            val tvNet = cell(
                FormatHelper.formatCompact(s.net), 80, Gravity.END,
                netColor, true, true
            )

            row.addView(tvCat)
            row.addView(tvInc)
            row.addView(tvExp)
            row.addView(tvNet)
            rows.addView(row)

            val a: Animation = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
            a.startOffset = i * 60L
            row.startAnimation(a)
        }
        if (sums.isEmpty()) {
            val empty = TextView(requireContext())
            empty.text = getString(R.string.empty_transactions)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, 24, 0, 24)
            empty.setTextColor(ThemeHelper.chartTextColor(requireContext()))
            rows.addView(empty)
        }
    }

    private fun cell(
        text: String?, widthDp: Int, gravity: Int, color: Int, bold: Boolean,
        singleLine: Boolean
    ): TextView {
        val tv = TextView(requireContext())
        tv.text = text
        tv.gravity = gravity
        tv.setTextColor(color)
        tv.textSize = 12f
        if (bold) tv.typeface = Typeface.DEFAULT_BOLD
        if (singleLine) {
            tv.setSingleLine(true)
            tv.ellipsize = TextUtils.TruncateAt.END
        }
        val density = resources.displayMetrics.density
        val lp = TableRow.LayoutParams(
            (widthDp * density + 0.5f).toInt(), TableRow.LayoutParams.WRAP_CONTENT
        )
        tv.layoutParams = lp
        return tv
    }
}
