package com.budgeting.me.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.budgeting.me.MainActivity
import com.budgeting.me.R
import com.budgeting.me.util.CalculatorPanel

/** Full-page calculator. Reuses the same keypad layout and engine as the dialog. */
class CalculatorFragment : Fragment(), TabRefreshable {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calculator, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        // closeAction = null: no Tutup button on the full page.
        CalculatorPanel(
            v,
            { result -> (requireActivity() as MainActivity).openAddWithAmount(result) },
            null
        )
    }

    override fun refresh() {
        // Stateless keypad, nothing to refresh.
    }
}
