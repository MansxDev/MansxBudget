package com.budgeting.me.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.budgeting.me.MainActivity
import com.budgeting.me.R
import com.budgeting.me.data.DatabaseHelper
import com.budgeting.me.util.BankWhitelist
import com.budgeting.me.util.ExportHelper
import com.budgeting.me.util.ReminderReceiver
import com.budgeting.me.util.ThemeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment(), TabRefreshable {

    private var db: DatabaseHelper? = null
    private var recAdapter: TransactionAdapter? = null
    private var tvRecEmpty: TextView? = null
    private var tvNotifStatus: TextView? = null
    private var tvBankSources: TextView? = null
    private var bankSourcesDialog: androidx.appcompat.app.AlertDialog? = null
    private lateinit var restoreLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                try {
                    val json = ExportHelper.readUri(requireContext(), uri)
                    val count = db?.importJson(json) ?: 0
                    toast(getString(R.string.restore_success, count))
                    (requireActivity() as MainActivity).refreshAllTabs()
                } catch (e: Exception) {
                    toast(getString(R.string.restore_failed, e.message))
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val database = DatabaseHelper(requireContext())
        db = database

        // Theme
        val rg: RadioGroup = v.findViewById(R.id.rg_theme)
        val rbWhite: RadioButton = v.findViewById(R.id.rb_white)
        val rbDark: RadioButton = v.findViewById(R.id.rb_dark)
        val rbAmoled: RadioButton = v.findViewById(R.id.rb_amoled)
        val cur = ThemeHelper.getTheme(requireContext())
        if (ThemeHelper.THEME_WHITE.equals(cur, ignoreCase = true)) rbWhite.isChecked = true
        else if (ThemeHelper.THEME_AMOLED.equals(cur, ignoreCase = true)) rbAmoled.isChecked = true
        else rbDark.isChecked = true
        rg.setOnCheckedChangeListener { _, id ->
            if (id == R.id.rb_white) ThemeHelper.saveTheme(requireContext(), ThemeHelper.THEME_WHITE)
            else if (id == R.id.rb_amoled) ThemeHelper.saveTheme(requireContext(), ThemeHelper.THEME_AMOLED)
            else ThemeHelper.saveTheme(requireContext(), ThemeHelper.THEME_DARK)
            requireActivity().recreate()
        }

        // Reminder
        val sw: CompoundButton = v.findViewById(R.id.sw_reminder)
        val tp: TimePicker = v.findViewById(R.id.time_picker)
        sw.isChecked = ThemeHelper.isReminderOn(requireContext())
        tp.visibility = if (sw.isChecked) View.VISIBLE else View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tp.hour = ThemeHelper.getReminderHour(requireContext())
            tp.minute = ThemeHelper.getReminderMinute(requireContext())
        } else {
            tp.currentHour = ThemeHelper.getReminderHour(requireContext())
            tp.currentMinute = ThemeHelper.getReminderMinute(requireContext())
        }
        sw.setOnCheckedChangeListener { _, checked ->
            tp.visibility = if (checked) View.VISIBLE else View.GONE
        }
        val btnSaveReminder: Button = v.findViewById(R.id.btn_save_reminder)
        btnSaveReminder.setOnClickListener {
            val h: Int
            val m: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                h = tp.hour
                m = tp.minute
            } else {
                h = tp.currentHour
                m = tp.currentMinute
            }
            if (sw.isChecked) {
                ThemeHelper.setReminder(requireContext(), true, h, m)
                ReminderReceiver.scheduleDaily(requireContext(), h, m)
                toast(getString(R.string.reminder_saved, h, m))
            } else {
                ThemeHelper.setReminder(requireContext(), false, h, m)
                ReminderReceiver.cancel(requireContext())
                toast(getString(R.string.reminder_disabled))
            }
        }

        // Notification access (auto-parse notifikasi bank)
        val rowNotif: View = v.findViewById(R.id.row_notif_access)
        tvNotifStatus = v.findViewById(R.id.tv_notif_status)
        rowNotif.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                toast(getString(R.string.notif_access_error))
            }
        }
        refreshNotifAccess()

        // Bank sources (auto-parse notifikasi bank)
        val rowSources: View = v.findViewById(R.id.row_bank_sources)
        tvBankSources = v.findViewById(R.id.tv_bank_sources)
        rowSources.setOnClickListener { showBankSources() }
        refreshBankSources()

        // Recurring templates
        val rv: RecyclerView = v.findViewById(R.id.rv_recurring)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.isNestedScrollingEnabled = false
        val listAdapter = TransactionAdapter { t ->
            (requireActivity() as MainActivity).openEditor(t.id)
        }
        recAdapter = listAdapter
        rv.adapter = listAdapter
        tvRecEmpty = v.findViewById(R.id.tv_rec_empty)

        // Data
        v.findViewById<View>(R.id.btn_backup).setOnClickListener {
            try {
                val path = ExportHelper.exportJson(requireContext(), database.exportJson())
                toast(getString(R.string.backup_success, path))
            } catch (e: Exception) {
                toast(getString(R.string.backup_failed, e.message))
            }
        }
        v.findViewById<View>(R.id.btn_restore).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            restoreLauncher.launch(i)
        }
        v.findViewById<View>(R.id.btn_export_csv).setOnClickListener {
            try {
                val path = ExportHelper.exportCsv(requireContext(), database.getAllTransactions())
                toast(getString(R.string.csv_success, path))
            } catch (e: Exception) {
                toast(
                    if ("empty" == e.message)
                        getString(R.string.csv_empty)
                    else
                        getString(R.string.csv_failed, e.message)
                )
            }
        }

        refresh()
    }

    override fun refresh() {
        val database = db ?: return
        if (!isAdded) return
        val adapter = recAdapter ?: return
        adapter.setData(database.getRecurringTemplates())
        tvRecEmpty?.visibility =
            if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        // User mungkin baru balik dari Settings sistem.
        refreshNotifAccess()
        refreshBankSources()
    }

    private fun refreshBankSources() {
        val tv = tvBankSources ?: return
        if (!isAdded) return
        tv.text = getString(
            R.string.bank_sources_sub,
            BankWhitelist.builtinCount(), BankWhitelist.customCount(requireContext())
        )
    }

    /** Dialog list bank bawaan + custom (custom bisa dihapus per item). */
    private fun showBankSources() {
        if (!isAdded) return
        val ctx = requireContext()
        val custom = BankWhitelist.getCustom(ctx)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, (8 * resources.displayMetrics.density).toInt(), pad, 0)
        }
        val scroll = ScrollView(ctx)
        scroll.addView(root)

        val builtinNames = BankWhitelist.BUILTIN.values.toSortedSet()
        val tvBuiltin = TextView(ctx).apply {
            text = getString(R.string.bank_sources_builtin, builtinNames.joinToString(", "))
            textSize = 12f
        }
        root.addView(tvBuiltin)

        if (custom.isNotEmpty()) {
            val tvHead = TextView(ctx).apply {
                text = getString(R.string.bank_sources_custom_head)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                val top = (12 * resources.displayMetrics.density).toInt()
                setPadding(0, top, 0, 0)
            }
            root.addView(tvHead)
            for ((pkg, name) in custom) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val tb = (4 * resources.displayMetrics.density).toInt()
                    setPadding(0, tb, 0, tb)
                }
                val tvItem = TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = "$name\n$pkg"
                    textSize = 12f
                }
                val btnDel = TextView(ctx).apply {
                    setText(R.string.btn_delete)
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    val pad = (12 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                    setTextColor(
                        try {
                            val tv = android.util.TypedValue()
                            ctx.theme.resolveAttribute(
                                com.google.android.material.R.attr.colorError, tv, true)
                            tv.data
                        } catch (e: Exception) {
                            0xFFB00020.toInt()
                        }
                    )
                    setOnClickListener {
                        BankWhitelist.removeCustom(ctx, pkg)
                        refreshBankSources()
                        try {
                            bankSourcesDialog?.dismiss()
                        } catch (e: Exception) {
                        }
                        bankSourcesDialog = null
                        showBankSources()
                    }
                }
                row.addView(tvItem)
                row.addView(btnDel)
                root.addView(row)
            }
        }

        bankSourcesDialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.bank_sources_title)
            .setView(scroll)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    private fun refreshNotifAccess() {
        val tv = tvNotifStatus ?: return
        val on = isNotifAccessEnabled()
        tv.text = getString(
            if (on) R.string.notif_access_on else R.string.notif_access_off
        )
        if (on) tv.setTextColor(0xFF0FBFA0.toInt())
    }

    private fun isNotifAccessEnabled(): Boolean {
        return try {
            NotificationManagerCompat.getEnabledListenerPackages(requireContext())
                .contains(requireContext().packageName)
        } catch (e: Exception) {
            false
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }
}
