package com.budgeting.me.util

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.budgeting.me.R

/**
 * Wires the shared calculator keypad layout (dialog_calculator) to the
 * [CalcEngine]. Used by both the popup dialog and the full calculator
 * page, so the two never drift apart.
 *
 * Pass `closeAction = null` on the full page to hide the Tutup button.
 */
class CalculatorPanel(
    root: View,
    private val resultListener: OnResultListener?,
    private val closeAction: Runnable?
) : View.OnClickListener {

    fun interface OnResultListener {
        fun onResult(result: Double)
    }

    private val tvExpression: TextView = root.findViewById(R.id.tvCalcExpression)
    private val tvResult: TextView = root.findViewById(R.id.tvCalcResult)

    private var expression = ""
    private var currentResult = 0.0

    init {
        val btnIds = intArrayOf(
            R.id.btnCalc0, R.id.btnCalc1, R.id.btnCalc2, R.id.btnCalc3,
            R.id.btnCalc4, R.id.btnCalc5, R.id.btnCalc6, R.id.btnCalc7,
            R.id.btnCalc8, R.id.btnCalc9, R.id.btnCalcDot,
            R.id.btnCalcAdd, R.id.btnCalcSub, R.id.btnCalcMul, R.id.btnCalcDiv,
            R.id.btnCalcClear, R.id.btnCalcDel, R.id.btnCalcEqual,
            R.id.btnCalcUseResult, R.id.btnCalcClose
        )
        for (id in btnIds) {
            root.findViewById<View>(id)?.setOnClickListener(this)
        }

        root.findViewById<View>(R.id.btnCalcClose)?.visibility =
            if (closeAction == null) View.GONE else View.VISIBLE
    }

    override fun onClick(v: View) {
        val id = v.id
        when (id) {
            R.id.btnCalcClear -> {
                expression = ""
                currentResult = 0.0
                updateDisplay("0")
            }
            R.id.btnCalcDel -> {
                if (expression.isNotEmpty()) {
                    expression = expression.substring(0, expression.length - 1)
                    calculateExpression()
                }
            }
            R.id.btnCalcEqual -> {
                calculateExpression()
                expression = currentResult.toLong().toString()
                tvExpression.text = ""
                tvResult.text = CalcEngine.formatResult(currentResult)
            }
            R.id.btnCalcUseResult -> {
                calculateExpression()
                resultListener?.onResult(currentResult)
            }
            R.id.btnCalcClose -> {
                closeAction?.run()
            }
            else -> {
                val btn = v as? Button ?: return
                var text = btn.text.toString()
                if ("÷" == text) text = "/"
                if ("×" == text) text = "*"
                expression += text
                calculateExpression()
            }
        }
    }

    private fun calculateExpression() {
        tvExpression.text = expression
        if (expression.isEmpty()) {
            currentResult = 0.0
            tvResult.text = "0"
            return
        }

        try {
            val res = CalcEngine.eval(expression)
            currentResult = res
            tvResult.text = CalcEngine.formatResult(res)
        } catch (e: Exception) {
            // Ignore partial expressions
        }
    }

    private fun updateDisplay(resText: String) {
        tvExpression.text = expression
        tvResult.text = resText
    }
}
