package com.budgeting.me

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.budgeting.me.data.DatabaseHelper
import com.budgeting.me.ui.BankConfirmSheet
import com.budgeting.me.ui.BudgetFragment
import com.budgeting.me.ui.CalculatorFragment
import com.budgeting.me.ui.DebtsFragment
import com.budgeting.me.ui.HomeCustomizeSheet
import com.budgeting.me.ui.HomeFragment
import com.budgeting.me.ui.LearnBankSheet
import com.budgeting.me.ui.ReportFragment
import com.budgeting.me.ui.SettingsFragment
import com.budgeting.me.ui.TabRefreshable
import com.budgeting.me.ui.TransactionSheet
import com.budgeting.me.ui.TransactionsFragment
import com.budgeting.me.util.BankNotifListener
import com.budgeting.me.util.BankNotifParser
import com.budgeting.me.util.RecurringReceiver
import com.budgeting.me.util.ThemeHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var bottomNav: BottomNavigationView
    private val cache = HashMap<Int, Fragment>()
    private var currentTab = R.id.nav_home
    private var previousTab = R.id.nav_home

    /** Hasil parse notifikasi bank (BroadcastReceiver internal, non-exported). */
    private val bankNotifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isFinishing || supportFragmentManager.isStateSaved) return
            when (intent.action) {
                BankNotifListener.ACTION_BANK_NOTIF_PARSED -> {
                    val amount = intent.getLongExtra(BankNotifListener.EXTRA_AMOUNT, 0L)
                    if (amount <= 0) return
                    val kind = intent.getStringExtra(BankNotifListener.EXTRA_KIND)
                        ?: BankNotifParser.KIND_OUT
                    val bank = intent.getStringExtra(BankNotifListener.EXTRA_BANK).orEmpty()
                    val raw = intent.getStringExtra(BankNotifListener.EXTRA_RAW).orEmpty()
                    val classification =
                        intent.getStringExtra(BankNotifListener.EXTRA_CLASSIFICATION)
                    val category = intent.getStringExtra(BankNotifListener.EXTRA_CATEGORY)
                    val merchant = intent.getStringExtra(BankNotifListener.EXTRA_MERCHANT)
                    BankConfirmSheet.show(
                        supportFragmentManager, amount, kind, bank, raw,
                        classification, category, merchant
                    )
                }
                BankNotifListener.ACTION_BANK_UNKNOWN -> {
                    val pkg = intent.getStringExtra(BankNotifListener.EXTRA_PKG).orEmpty()
                    if (pkg.isEmpty()) return
                    val label = intent.getStringExtra(BankNotifListener.EXTRA_LABEL).orEmpty()
                    val raw = intent.getStringExtra(BankNotifListener.EXTRA_RAW).orEmpty()
                    val amount = intent.getLongExtra(BankNotifListener.EXTRA_AMOUNT, 0L)
                    if (amount <= 0) return
                    val kind = intent.getStringExtra(BankNotifListener.EXTRA_KIND)
                        ?: BankNotifParser.KIND_OUT
                    LearnBankSheet.show(
                        supportFragmentManager, pkg, label, raw, amount, kind)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.resolveTheme(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        bottomNav = findViewById(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            switchTab(item.itemId)
            true
        }

        val fab: FloatingActionButton = findViewById(R.id.fab_add)
        fab.setOnClickListener { openAdd() }

        // Daily recurring scheduler (idempotent) + catch-up for missed days.
        RecurringReceiver.schedule(this)
        requestNotificationPermission()

        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentTab == R.id.action_calc
                        || currentTab == R.id.action_settings) {
                        // Back from calculator/settings page: return to previous tab, not exit.
                        switchTab(previousTab)
                        bottomNav.selectedItemId = previousTab
                    } else {
                        showExitDialog()
                    }
                }
            })

        if (savedInstanceState == null) {
            switchTab(R.id.nav_home)
        } else {
            currentTab = savedInstanceState.getInt("tab", R.id.nav_home)
            switchTab(currentTab)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTab)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BankNotifListener.ACTION_BANK_NOTIF_PARSED)
            addAction(BankNotifListener.ACTION_BANK_UNKNOWN)
        }
        ContextCompat.registerReceiver(
            this, bankNotifReceiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(bankNotifReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // "Atur Beranda" hanya relevan di tab Beranda.
        menu.findItem(R.id.action_home_customize)?.isVisible = currentTab == R.id.nav_home
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_calc) {
            showCalculator()
            return true
        }
        if (item.itemId == R.id.action_settings) {
            showSettings()
            return true
        }
        if (item.itemId == R.id.action_home_customize) {
            HomeCustomizeSheet.show(supportFragmentManager)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun switchTab(id: Int) {
        if (currentTab != R.id.action_calc && currentTab != R.id.action_settings) {
            previousTab = currentTab
        }
        currentTab = id
        var f = cache[id]
        if (f == null) {
            f = createTab(id)
            cache[id] = f
        }
        toolbar.title = titleFor(id)
        invalidateOptionsMenu()
        val tx: FragmentTransaction = supportFragmentManager.beginTransaction()
        tx.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
        tx.replace(R.id.fragment_container, f)
        tx.commit()
    }

    private fun createTab(id: Int): Fragment {
        if (id == R.id.nav_transactions) return TransactionsFragment()
        if (id == R.id.nav_debts) return DebtsFragment()
        if (id == R.id.nav_report) return ReportFragment()
        if (id == R.id.nav_budget) return BudgetFragment()
        if (id == R.id.action_settings) return SettingsFragment()
        if (id == R.id.action_calc) return CalculatorFragment()
        return HomeFragment()
    }

    private fun titleFor(id: Int): String {
        if (id == R.id.nav_transactions) return getString(R.string.nav_transactions)
        if (id == R.id.nav_debts) return getString(R.string.nav_debts)
        if (id == R.id.nav_report) return getString(R.string.nav_report)
        if (id == R.id.nav_budget) return getString(R.string.nav_budget)
        if (id == R.id.action_settings) return getString(R.string.nav_settings)
        if (id == R.id.action_calc) return getString(R.string.nav_calculator)
        return getString(R.string.header_title)
    }

    // ---------- cross-tab actions ----------

    fun goToTransactions() {
        bottomNav.selectedItemId = R.id.nav_transactions
    }

    fun goToBudget() {
        bottomNav.selectedItemId = R.id.nav_budget
    }

    fun openEditor(id: Long) {
        val s = TransactionSheet.forEdit(id)
        s.setOnSaved(object : TransactionSheet.OnSaved {
            override fun onSaved() = refreshAllTabs()
        })
        s.show(supportFragmentManager, "sheet")
    }

    fun openAdd() {
        val s = TransactionSheet.forAdd()
        s.setOnSaved(object : TransactionSheet.OnSaved {
            override fun onSaved() = refreshAllTabs()
        })
        s.show(supportFragmentManager, "sheet")
    }

    fun openAddWithAmount(amount: Double) {
        val s = TransactionSheet.forAddWithAmount(amount)
        s.setOnSaved(object : TransactionSheet.OnSaved {
            override fun onSaved() = refreshAllTabs()
        })
        s.show(supportFragmentManager, "sheet")
    }

    /**
     * Buka form transaksi pre-filled dari konfirmasi notifikasi bank.
     *
     * [category] adalah hasil auto-kategorisasi merchant (v4.7) dan hanya
     * dipakai kalau benar-benar salah satu kategori existing, supaya form
     * tidak membuat chip kategori baru.
     */
    fun openTransactionFromBankNotif(
        amount: Long, kind: String, bank: String, raw: String,
        category: String? = null, merchant: String? = null
    ) {
        val type = if (kind == BankNotifParser.KIND_IN) {
            DatabaseHelper.TYPE_INCOME
        } else {
            DatabaseHelper.TYPE_EXPENSE
        }
        val note = buildBankNote(bank, raw, merchant)
        val safeCategory = category?.takeIf { it in TransactionSheet.DEFAULT_CATEGORIES }
        val s = TransactionSheet.forBankNotif(amount, type, note, safeCategory)
        s.setOnSaved(object : TransactionSheet.OnSaved {
            override fun onSaved() = refreshAllTabs()
        })
        s.show(supportFragmentManager, "sheet")
    }

    /**
     * Catatan transaksi: "{bank} - {cuplikan notif}".
     * Merchant tidak diulang kalau sudah muncul di cuplikan notif (v4.7).
     */
    private fun buildBankNote(bank: String, raw: String, merchant: String?): String {
        val head = bank.trim()
        val body = raw.trim().take(60)
        val note = if (head.isEmpty()) body else "$head - $body"
        val m = merchant?.trim().orEmpty()
        if (m.isEmpty() || note.contains(m, ignoreCase = true)) return note
        return "$note - $m"
    }

    fun refreshAllTabs() {
        for (f in cache.values) {
            if (f is TabRefreshable && f.isAdded) {
                f.refresh()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9001
            )
        }
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_msg)
            .setPositiveButton(R.string.exit_yes) { _, _ -> finish() }
            .setNegativeButton(R.string.exit_no, null)
            .show()
    }

    private fun showCalculator() {
        switchTab(R.id.action_calc)
    }

    private fun showSettings() {
        switchTab(R.id.action_settings)
    }
}
