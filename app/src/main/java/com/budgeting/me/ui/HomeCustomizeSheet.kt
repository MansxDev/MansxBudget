package com.budgeting.me.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.budgeting.me.MainActivity
import com.budgeting.me.R
import com.budgeting.me.util.HomePrefs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider

/** Sheet "Atur Beranda": toggle 7 widget + slider horizon kalender. */
class HomeCustomizeSheet : BottomSheetDialogFragment() {

    private val keyToCheck = mapOf(
        HomePrefs.W_SALDO to R.id.chk_saldo,
        HomePrefs.W_QUICK to R.id.chk_quick,
        HomePrefs.W_PLAN to R.id.chk_plan,
        HomePrefs.W_CALENDAR to R.id.chk_calendar,
        HomePrefs.W_CHART to R.id.chk_chart,
        HomePrefs.W_BUDGET to R.id.chk_budget,
        HomePrefs.W_LAST_TRX to R.id.chk_last
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.sheet_home_customize, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val enabled = HomePrefs.enabled(ctx)
        for ((key, resId) in keyToCheck) {
            v.findViewById<MaterialCheckBox>(resId).isChecked = key in enabled
        }

        var horizon = HomePrefs.calendarHorizonDays(ctx)
        val slider: Slider = v.findViewById(R.id.slider_horizon)
        val label: TextView = v.findViewById(R.id.tv_horizon_label)
        slider.value = horizon.toFloat()
        label.text = getString(R.string.horizon_label, horizon)
        slider.addOnChangeListener { _, value, _ ->
            horizon = Math.round(value).coerceIn(7, 90)
            label.text = getString(R.string.horizon_label, horizon)
        }

        v.findViewById<View>(R.id.btn_save).setOnClickListener {
            val set = mutableSetOf<String>()
            for ((key, resId) in keyToCheck) {
                if (v.findViewById<MaterialCheckBox>(resId).isChecked) set.add(key)
            }
            HomePrefs.setEnabled(ctx, set)
            HomePrefs.setCalendarHorizon(ctx, horizon)
            dismiss()
            (activity as? MainActivity)?.refreshAllTabs()
        }

        v.findViewById<View>(R.id.btn_reset).setOnClickListener {
            HomePrefs.resetToDefault(ctx)
            for ((_, resId) in keyToCheck) {
                v.findViewById<MaterialCheckBox>(resId).isChecked = true
            }
            horizon = 30
            slider.value = 30f
            label.text = getString(R.string.horizon_label, 30)
        }
    }

    companion object {
        fun show(fm: FragmentManager): HomeCustomizeSheet {
            val s = HomeCustomizeSheet()
            s.show(fm, "home_customize")
            return s
        }
    }
}