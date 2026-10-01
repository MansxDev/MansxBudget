package com.budgeting.me.util

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import com.budgeting.me.R

class CalculatorDialog(context: Context, private val listener: OnResultListener?) : Dialog(context) {

    fun interface OnResultListener {
        fun onResult(result: Double)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_calculator)

        val root: View = findViewById(android.R.id.content)
        CalculatorPanel(root, { result ->
            listener?.onResult(result)
            dismiss()
        }, Runnable { dismiss() })
    }
}
