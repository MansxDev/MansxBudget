package com.budgeting.me.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.budgeting.me.model.Budget
import com.budgeting.me.model.CategorySummary
import com.budgeting.me.model.Debt
import com.budgeting.me.model.Goal
import com.budgeting.me.model.Transaction
import com.budgeting.me.util.FormatHelper
import org.json.JSONArray
import org.json.JSONObject

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(TABLE_CREATE_V2)
        db.execSQL(BUDGET_CREATE)
        db.execSQL(GOAL_CREATE)
        db.execSQL(DEBT_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN $COLUMN_DATE_ISO TEXT")
            db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN $COLUMN_RECURRING TEXT DEFAULT 'NONE'")
            db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN $COLUMN_LAST_RUN TEXT")
            db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN $COLUMN_AUTO INTEGER DEFAULT 0")
            db.execSQL(BUDGET_CREATE)
            backfillIsoDates(db)
        }
        if (oldVersion < 3) {
            db.execSQL(GOAL_CREATE)
        }
        if (oldVersion < 4) {
            db.execSQL(DEBT_CREATE)
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE $TABLE_GOALS ADD COLUMN $COLUMN_GOAL_TARGET_DATE TEXT")
            db.execSQL("ALTER TABLE $TABLE_GOALS ADD COLUMN $COLUMN_GOAL_CALENDAR_EVENT INTEGER")
            db.execSQL("ALTER TABLE $TABLE_GOALS ADD COLUMN $COLUMN_GOAL_REMINDER_DAYS TEXT DEFAULT ''")
        }
    }

    private fun backfillIsoDates(db: SQLiteDatabase) {
        var c: Cursor? = null
        try {
            c = db.query(TABLE_TRANSACTIONS, arrayOf(COLUMN_ID, COLUMN_DATE),
                null, null, null, null, null)
            if (c != null && c.moveToFirst()) {
                do {
                    val id = c.getLong(0)
                    val iso = FormatHelper.parseDisplayToIso(c.getString(1))
                    if (iso != null) {
                        val v = ContentValues()
                        v.put(COLUMN_DATE_ISO, iso)
                        db.update(TABLE_TRANSACTIONS, v, "$COLUMN_ID=?",
                            arrayOf(id.toString()))
                    }
                } while (c.moveToNext())
            }
        } catch (ignored: Exception) {
        } finally {
            c?.close()
        }
    }

    // ---------- CRUD ----------

    fun insertTransaction(t: Transaction): Long {
        val db = writableDatabase
        val v = toValues(t)
        val id = db.insert(TABLE_TRANSACTIONS, null, v)
        db.close()
        return id
    }

    fun updateTransaction(t: Transaction): Int {
        val db = writableDatabase
        val rows = db.update(TABLE_TRANSACTIONS, toValues(t), "$COLUMN_ID=?",
            arrayOf(t.id.toString()))
        db.close()
        return rows
    }

    private fun toValues(t: Transaction): ContentValues {
        // Keep ISO + display in sync.
        var iso = t.dateIso
        var display = t.date
        if (iso == null && display != null) {
            iso = FormatHelper.parseDisplayToIso(display)
        }
        if (display == null && iso != null) {
            display = FormatHelper.toDisplay(iso)
        }
        if (iso == null) {
            iso = FormatHelper.todayIso()
            display = FormatHelper.toDisplay(iso)
        }
        if (display == null) display = iso
        t.dateIso = iso
        t.date = display

        val v = ContentValues()
        v.put(COLUMN_TITLE, t.title)
        v.put(COLUMN_AMOUNT, t.amount)
        v.put(COLUMN_TYPE, t.type)
        v.put(COLUMN_CATEGORY, t.category)
        v.put(COLUMN_DATE, t.date)
        v.put(COLUMN_DATE_ISO, t.dateIso)
        v.put(COLUMN_RECURRING, t.recurring)
        v.put(COLUMN_LAST_RUN, t.lastRun)
        v.put(COLUMN_AUTO, t.auto)
        return v
    }

    fun deleteTransaction(id: Long): Int {
        val db = writableDatabase
        val rows = db.delete(TABLE_TRANSACTIONS, "$COLUMN_ID=?",
            arrayOf(id.toString()))
        db.close()
        return rows
    }

    /**
     * Dedup lapis kedua untuk auto-capture: cari transaksi otomatis dengan
     * nominal sama pada tanggal yang sama. Notifikasi kembar sering datang
     * dari 2 aplikasi (mis. GoPay lalu BCA) sehingga fingerprint SharedPreferences
     * saja tidak cukup.
     */
    fun findAutoByAmount(amount: Double, dateIso: String): Transaction? {
        val db = readableDatabase
        val c = db.query(
            TABLE_TRANSACTIONS, null,
            "$COLUMN_AMOUNT=? AND $COLUMN_DATE_ISO=? AND $COLUMN_AUTO=1",
            arrayOf(amount.toString(), dateIso), null, null, "$COLUMN_ID DESC", "1"
        )
        var t: Transaction? = null
        if (c != null) {
            if (c.moveToFirst()) t = fromCursor(c)
            c.close()
        }
        db.close()
        return t
    }

    /** Jumlah transaksi otomatis (untuk baris ringkasan di Pengaturan). */
    fun countAuto(): Long {
        val db = readableDatabase
        var n = 0L
        val c = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_TRANSACTIONS WHERE $COLUMN_AUTO=1", null
        )
        if (c != null) {
            if (c.moveToFirst()) n = c.getLong(0)
            c.close()
        }
        db.close()
        return n
    }

    fun getTransaction(id: Long): Transaction? {
        val db = readableDatabase
        val c = db.query(TABLE_TRANSACTIONS, null, "$COLUMN_ID=?",
            arrayOf(id.toString()), null, null, null)
        var t: Transaction? = null
        if (c != null) {
            if (c.moveToFirst()) t = fromCursor(c)
            c.close()
        }
        db.close()
        return t
    }

    fun getAllTransactions(): List<Transaction> {
        return queryTransactions(null, null, null, null, null, null, null)
    }

    /** Filtered search. Null/empty params are ignored. type: "Pemasukan"/"Pengeluaran" or null. */
    fun queryTransactions(
        search: String?, type: String?, category: String?,
        fromIso: String?, toIso: String?,
        minAmount: Double?, maxAmount: Double?
    ): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val where = StringBuilder("1=1")
        val args = mutableListOf<String>()
        if (!search.isNullOrBlank()) {
            where.append(" AND ").append(COLUMN_TITLE).append(" LIKE ?")
            args.add("%" + search.trim() + "%")
        }
        if (!type.isNullOrEmpty()) {
            where.append(" AND ").append(COLUMN_TYPE).append("=?")
            args.add(type)
        }
        if (!category.isNullOrEmpty()) {
            where.append(" AND ").append(COLUMN_CATEGORY).append("=?")
            args.add(category)
        }
        if (!fromIso.isNullOrEmpty()) {
            where.append(" AND ").append(COLUMN_DATE_ISO).append(">=?")
            args.add(fromIso)
        }
        if (!toIso.isNullOrEmpty()) {
            where.append(" AND ").append(COLUMN_DATE_ISO).append("<=?")
            args.add(toIso)
        }
        if (minAmount != null) {
            where.append(" AND ").append(COLUMN_AMOUNT).append(">=?")
            args.add(minAmount.toString())
        }
        if (maxAmount != null) {
            where.append(" AND ").append(COLUMN_AMOUNT).append("<=?")
            args.add(maxAmount.toString())
        }
        val db = readableDatabase
        val c = db.query(TABLE_TRANSACTIONS, null, where.toString(),
            args.toTypedArray(), null, null,
            "$COLUMN_DATE_ISO DESC, $COLUMN_ID DESC")
        if (c != null) {
            while (c.moveToNext()) list.add(fromCursor(c))
            c.close()
        }
        db.close()
        return list
    }

    private fun fromCursor(c: Cursor): Transaction {
        val iId = c.getColumnIndex(COLUMN_ID)
        val iTitle = c.getColumnIndex(COLUMN_TITLE)
        val iAmount = c.getColumnIndex(COLUMN_AMOUNT)
        val iType = c.getColumnIndex(COLUMN_TYPE)
        val iCat = c.getColumnIndex(COLUMN_CATEGORY)
        val iDate = c.getColumnIndex(COLUMN_DATE)
        val iIso = c.getColumnIndex(COLUMN_DATE_ISO)
        val iRec = c.getColumnIndex(COLUMN_RECURRING)
        val iLast = c.getColumnIndex(COLUMN_LAST_RUN)
        val iAuto = c.getColumnIndex(COLUMN_AUTO)
        return Transaction(
            if (iId < 0) -1 else c.getLong(iId),
            if (iTitle < 0) "" else c.getString(iTitle) ?: "",
            if (iAmount < 0) 0.0 else c.getDouble(iAmount),
            if (iType < 0) "" else c.getString(iType) ?: "",
            if (iCat < 0) "" else c.getString(iCat) ?: "",
            if (iDate < 0) "" else c.getString(iDate),
            if (iIso < 0) null else c.getString(iIso),
            if (iRec < 0) Transaction.FREQ_NONE else (c.getString(iRec) ?: Transaction.FREQ_NONE),
            if (iLast < 0) null else c.getString(iLast),
            if (iAuto < 0) 0 else c.getInt(iAuto)
        )
    }

    // ---------- Summaries ----------

    fun getTotalIncome(): Double = sumByType(TYPE_INCOME, null, null)

    fun getTotalExpense(): Double = sumByType(TYPE_EXPENSE, null, null)

    fun getTotalBalance(): Double = getTotalIncome() - getTotalExpense()

    fun getExpenseBetween(fromIso: String, toIso: String): Double =
        sumByType(TYPE_EXPENSE, fromIso, toIso)

    fun getIncomeBetween(fromIso: String, toIso: String): Double =
        sumByType(TYPE_INCOME, fromIso, toIso)

    private fun sumByType(type: String, fromIso: String?, toIso: String?): Double {
        var total = 0.0
        val db = readableDatabase
        val q = StringBuilder("SELECT SUM($COLUMN_AMOUNT) FROM " +
                "$TABLE_TRANSACTIONS WHERE $COLUMN_TYPE=?")
        val args = mutableListOf(type)
        if (fromIso != null) { q.append(" AND ").append(COLUMN_DATE_ISO).append(">=?"); args.add(fromIso) }
        if (toIso != null) { q.append(" AND ").append(COLUMN_DATE_ISO).append("<=?"); args.add(toIso) }
        val c = db.rawQuery(q.toString(), args.toTypedArray())
        if (c != null) {
            if (c.moveToFirst()) total = c.getDouble(0)
            c.close()
        }
        db.close()
        return total
    }

    fun getCategoryExpenseMonth(category: String, yearMonth: String): Double {
        var total = 0.0
        val db = readableDatabase
        val c = db.rawQuery("SELECT SUM($COLUMN_AMOUNT) FROM $TABLE_TRANSACTIONS"
                + " WHERE $COLUMN_TYPE=? AND $COLUMN_CATEGORY=?"
                + " AND substr($COLUMN_DATE_ISO,1,7)=?",
            arrayOf(TYPE_EXPENSE, category, yearMonth))
        if (c != null) {
            if (c.moveToFirst()) total = c.getDouble(0)
            c.close()
        }
        db.close()
        return total
    }

    fun getMonthExpense(yearMonth: String): Double {
        var total = 0.0
        val db = readableDatabase
        val c = db.rawQuery("SELECT SUM($COLUMN_AMOUNT) FROM $TABLE_TRANSACTIONS"
                + " WHERE $COLUMN_TYPE=? AND substr($COLUMN_DATE_ISO,1,7)=?",
            arrayOf(TYPE_EXPENSE, yearMonth))
        if (c != null) {
            if (c.moveToFirst()) total = c.getDouble(0)
            c.close()
        }
        db.close()
        return total
    }

    class MonthTotal(val yearMonth: String) {
        var income = 0.0
        var expense = 0.0
    }

    fun getMonthlyTotals(months: List<String>): List<MonthTotal> {
        val out = mutableListOf<MonthTotal>()
        val db = readableDatabase
        for (ym in months) {
            val m = MonthTotal(ym)
            val c = db.rawQuery("SELECT "
                    + "SUM(CASE WHEN $COLUMN_TYPE=? THEN $COLUMN_AMOUNT ELSE 0 END), "
                    + "SUM(CASE WHEN $COLUMN_TYPE=? THEN $COLUMN_AMOUNT ELSE 0 END) "
                    + "FROM $TABLE_TRANSACTIONS WHERE substr($COLUMN_DATE_ISO,1,7)=?",
                arrayOf(TYPE_INCOME, TYPE_EXPENSE, ym))
            if (c != null) {
                if (c.moveToFirst()) {
                    m.income = c.getDouble(0)
                    m.expense = c.getDouble(1)
                }
                c.close()
            }
            out.add(m)
        }
        db.close()
        return out
    }

    class Spend(val category: String, val total: Double)

    fun getTopCategoryBetween(fromIso: String, toIso: String): Spend? {
        val db = readableDatabase
        var s: Spend? = null
        val c = db.rawQuery("SELECT $COLUMN_CATEGORY, SUM($COLUMN_AMOUNT) FROM "
                + "$TABLE_TRANSACTIONS WHERE $COLUMN_TYPE=?"
                + " AND $COLUMN_DATE_ISO>=? AND $COLUMN_DATE_ISO<=?"
                + " GROUP BY $COLUMN_CATEGORY ORDER BY SUM($COLUMN_AMOUNT) DESC LIMIT 1",
            arrayOf(TYPE_EXPENSE, fromIso, toIso))
        if (c != null) {
            if (c.moveToFirst()) s = Spend(c.getString(0) ?: "", c.getDouble(1))
            c.close()
        }
        db.close()
        return s
    }

    fun getCategorySummaries(): List<CategorySummary> = getCategorySummariesForMonth(null)

    fun getCategorySummariesForMonth(yearMonth: String?): List<CategorySummary> {
        val out = mutableListOf<CategorySummary>()
        val db = readableDatabase
        var q = "SELECT $COLUMN_CATEGORY, " +
                "SUM(CASE WHEN $COLUMN_TYPE=? THEN $COLUMN_AMOUNT ELSE 0 END), " +
                "SUM(CASE WHEN $COLUMN_TYPE=? THEN $COLUMN_AMOUNT ELSE 0 END) " +
                "FROM $TABLE_TRANSACTIONS "
        val args = mutableListOf(TYPE_INCOME, TYPE_EXPENSE)
        if (yearMonth != null) {
            q += "WHERE substr($COLUMN_DATE_ISO,1,7)=? "
            args.add(yearMonth)
        }
        q += "GROUP BY $COLUMN_CATEGORY ORDER BY SUM($COLUMN_AMOUNT) DESC"
        val c = db.rawQuery(q, args.toTypedArray())
        if (c != null) {
            while (c.moveToNext()) {
                out.add(CategorySummary(c.getString(0) ?: "", c.getDouble(1), c.getDouble(2)))
            }
            c.close()
        }
        db.close()
        return out
    }

    fun getDistinctCategories(): List<String> {
        val out = mutableListOf<String>()
        val db = readableDatabase
        val c = db.rawQuery("SELECT DISTINCT $COLUMN_CATEGORY FROM $TABLE_TRANSACTIONS"
                + " ORDER BY $COLUMN_CATEGORY", null)
        if (c != null) {
            while (c.moveToNext()) {
                val s = c.getString(0)
                if (!s.isNullOrBlank()) out.add(s)
            }
            c.close()
        }
        db.close()
        return out
    }

    // ---------- Budgets ----------

    fun getAllBudgets(): List<Budget> {
        val out = mutableListOf<Budget>()
        val db = readableDatabase
        val c = db.query(TABLE_BUDGETS, null, null, null, null, null,
            "$COLUMN_BUDGET_LIMIT DESC")
        if (c != null) {
            while (c.moveToNext()) {
                out.add(Budget(c.getString(c.getColumnIndexOrThrow(COLUMN_BUDGET_CATEGORY)) ?: "",
                    c.getDouble(c.getColumnIndexOrThrow(COLUMN_BUDGET_LIMIT))))
            }
            c.close()
        }
        db.close()
        return out
    }

    fun upsertBudget(category: String, limit: Double) {
        val db = writableDatabase
        val v = ContentValues()
        v.put(COLUMN_BUDGET_CATEGORY, category)
        v.put(COLUMN_BUDGET_LIMIT, limit)
        db.insertWithOnConflict(TABLE_BUDGETS, null, v, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun deleteBudget(category: String) {
        val db = writableDatabase
        db.delete(TABLE_BUDGETS, "$COLUMN_BUDGET_CATEGORY=?", arrayOf(category))
        db.close()
    }

    fun getTotalBudgetLimit(): Double {
        var total = 0.0
        val db = readableDatabase
        val c = db.rawQuery("SELECT SUM($COLUMN_BUDGET_LIMIT) FROM $TABLE_BUDGETS", null)
        if (c != null) {
            if (c.moveToFirst()) total = c.getDouble(0)
            c.close()
        }
        db.close()
        return total
    }

    // ---------- Goals / Rencana ----------

    fun getAllGoals(): List<Goal> {
        val out = mutableListOf<Goal>()
        val db = readableDatabase
        val c = db.query(TABLE_GOALS, null, null, null, null, null,
            "$COLUMN_GOAL_ID ASC")
        if (c != null) {
            val ixDate = c.getColumnIndex(COLUMN_GOAL_TARGET_DATE)
            val ixCal = c.getColumnIndex(COLUMN_GOAL_CALENDAR_EVENT)
            val ixRem = c.getColumnIndex(COLUMN_GOAL_REMINDER_DAYS)
            while (c.moveToNext()) {
                out.add(Goal(
                    c.getLong(c.getColumnIndexOrThrow(COLUMN_GOAL_ID)),
                    c.getString(c.getColumnIndexOrThrow(COLUMN_GOAL_NAME)) ?: "",
                    c.getDouble(c.getColumnIndexOrThrow(COLUMN_GOAL_TARGET)),
                    c.getDouble(c.getColumnIndexOrThrow(COLUMN_GOAL_SAVED)),
                    if (ixDate >= 0 && !c.isNull(ixDate)) c.getString(ixDate) else null,
                    if (ixCal >= 0 && !c.isNull(ixCal)) c.getLong(ixCal) else null,
                    if (ixRem >= 0 && !c.isNull(ixRem)) c.getString(ixRem) else null))
            }
            c.close()
        }
        db.close()
        return out
    }

    fun insertGoal(g: Goal): Long {
        val db = writableDatabase
        val v = ContentValues()
        v.put(COLUMN_GOAL_NAME, g.name)
        v.put(COLUMN_GOAL_TARGET, g.target)
        v.put(COLUMN_GOAL_SAVED, g.saved)
        v.put(COLUMN_GOAL_TARGET_DATE, g.targetDate)
        if (g.calendarEventId != null) v.put(COLUMN_GOAL_CALENDAR_EVENT, g.calendarEventId)
        else v.putNull(COLUMN_GOAL_CALENDAR_EVENT)
        v.put(COLUMN_GOAL_REMINDER_DAYS, g.reminderDays)
        val id = db.insert(TABLE_GOALS, null, v)
        db.close()
        return id
    }

    fun updateGoal(g: Goal): Int {
        val db = writableDatabase
        val v = ContentValues()
        v.put(COLUMN_GOAL_NAME, g.name)
        v.put(COLUMN_GOAL_TARGET, g.target)
        v.put(COLUMN_GOAL_SAVED, g.saved)
        v.put(COLUMN_GOAL_TARGET_DATE, g.targetDate)
        if (g.calendarEventId != null) v.put(COLUMN_GOAL_CALENDAR_EVENT, g.calendarEventId)
        else v.putNull(COLUMN_GOAL_CALENDAR_EVENT)
        v.put(COLUMN_GOAL_REMINDER_DAYS, g.reminderDays)
        val rows = db.update(TABLE_GOALS, v, "$COLUMN_GOAL_ID=?",
            arrayOf(g.id.toString()))
        db.close()
        return rows
    }

    fun deleteGoal(id: Long): Int {
        val db = writableDatabase
        val rows = db.delete(TABLE_GOALS, "$COLUMN_GOAL_ID=?",
            arrayOf(id.toString()))
        db.close()
        return rows
    }

    // ---------- Debts / Hutang ----------

    fun getAllDebts(): List<Debt> = queryDebts(null, null, null, null, null)

    /** Filtered search. kind: Debt.KIND_MINE / KIND_THEIRS or null. doneOnly: true=lunas, false=belum, null=semua. */
    fun queryDebts(
        search: String?, kind: String?, doneOnly: Boolean?,
        fromIso: String?, toIso: String?
    ): List<Debt> {
        val list = mutableListOf<Debt>()
        val where = StringBuilder("1=1")
        val args = mutableListOf<String>()
        if (!search.isNullOrBlank()) {
            where.append(" AND (").append(COLUMN_DEBT_NAME).append(" LIKE ? OR ")
                .append(COLUMN_DEBT_PURPOSE).append(" LIKE ?)")
            args.add("%" + search.trim() + "%")
            args.add("%" + search.trim() + "%")
        }
        if (!kind.isNullOrEmpty()) {
            where.append(" AND ").append(COLUMN_DEBT_KIND).append("=?")
            args.add(kind)
        }
        if (doneOnly != null) {
            val doneExpr = StringBuilder().append(COLUMN_DEBT_PAID)
                .append(">=").append(COLUMN_DEBT_TOTAL)
                .append(" AND ").append(COLUMN_DEBT_TOTAL).append(">0").toString()
            if (doneOnly) where.append(" AND ").append(doneExpr)
            else where.append(" AND NOT (").append(doneExpr).append(")")
        }
        if (!fromIso.isNullOrEmpty()) {
            where.append(" AND ").append(COLUMN_DEBT_DATE_ISO).append(">=?")
            args.add(fromIso)
        }
        if (!toIso.isNullOrEmpty()) {
            where.append(" AND ").append(COLUMN_DEBT_DATE_ISO).append("<=?")
            args.add(toIso)
        }
        val db = readableDatabase
        val c = db.query(TABLE_DEBTS, null, where.toString(),
            args.toTypedArray(), null, null,
            "$COLUMN_DEBT_DATE_ISO DESC, $COLUMN_DEBT_ID DESC")
        if (c != null) {
            while (c.moveToNext()) list.add(debtFromCursor(c))
            c.close()
        }
        db.close()
        return list
    }

    fun getDebt(id: Long): Debt? {
        val db = readableDatabase
        val c = db.query(TABLE_DEBTS, null, "$COLUMN_DEBT_ID=?",
            arrayOf(id.toString()), null, null, null)
        var d: Debt? = null
        if (c != null) {
            if (c.moveToFirst()) d = debtFromCursor(c)
            c.close()
        }
        db.close()
        return d
    }

    fun insertDebt(d: Debt): Long {
        val db = writableDatabase
        val id = db.insert(TABLE_DEBTS, null, debtValues(d))
        db.close()
        return id
    }

    fun updateDebt(d: Debt): Int {
        val db = writableDatabase
        val rows = db.update(TABLE_DEBTS, debtValues(d), "$COLUMN_DEBT_ID=?",
            arrayOf(d.id.toString()))
        db.close()
        return rows
    }

    fun deleteDebt(id: Long): Int {
        val db = writableDatabase
        val rows = db.delete(TABLE_DEBTS, "$COLUMN_DEBT_ID=?",
            arrayOf(id.toString()))
        db.close()
        return rows
    }

    fun getTotalDebtByKind(kind: String): Double =
        sumDebtColumn(kind, COLUMN_DEBT_TOTAL)

    fun getTotalPaidByKind(kind: String): Double =
        sumDebtColumn(kind, COLUMN_DEBT_PAID)

    private fun sumDebtColumn(kind: String, column: String): Double {
        var total = 0.0
        val db = readableDatabase
        val c = db.rawQuery("SELECT SUM($column) FROM $TABLE_DEBTS WHERE $COLUMN_DEBT_KIND=?",
            arrayOf(kind))
        if (c != null) {
            if (c.moveToFirst()) total = c.getDouble(0)
            c.close()
        }
        db.close()
        return total
    }

    private fun debtValues(d: Debt): ContentValues {
        // Keep ISO + display in sync (same contract as transactions).
        var iso = d.dateIso
        var display = d.date
        if (iso == null && display != null) {
            iso = FormatHelper.parseDisplayToIso(display)
        }
        if (display == null && iso != null) {
            display = FormatHelper.toDisplay(iso)
        }
        if (iso == null) {
            iso = FormatHelper.todayIso()
            display = FormatHelper.toDisplay(iso)
        }
        if (display == null) display = iso
        d.dateIso = iso
        d.date = display

        val v = ContentValues()
        v.put(COLUMN_DEBT_NAME, d.name)
        v.put(COLUMN_DEBT_KIND, d.kind)
        v.put(COLUMN_DEBT_TOTAL, d.total)
        v.put(COLUMN_DEBT_PAID, d.paid)
        v.put(COLUMN_DEBT_PURPOSE, d.purpose)
        v.put(COLUMN_DEBT_DATE, d.date)
        v.put(COLUMN_DEBT_DATE_ISO, d.dateIso)
        return v
    }

    private fun debtFromCursor(c: Cursor): Debt {
        val iId = c.getColumnIndex(COLUMN_DEBT_ID)
        val iName = c.getColumnIndex(COLUMN_DEBT_NAME)
        val iKind = c.getColumnIndex(COLUMN_DEBT_KIND)
        val iTotal = c.getColumnIndex(COLUMN_DEBT_TOTAL)
        val iPaid = c.getColumnIndex(COLUMN_DEBT_PAID)
        val iPurpose = c.getColumnIndex(COLUMN_DEBT_PURPOSE)
        val iDate = c.getColumnIndex(COLUMN_DEBT_DATE)
        val iIso = c.getColumnIndex(COLUMN_DEBT_DATE_ISO)
        return Debt(
            if (iId < 0) 0 else c.getLong(iId),
            if (iName < 0) "" else c.getString(iName) ?: "",
            if (iKind < 0) Debt.KIND_MINE else (c.getString(iKind) ?: Debt.KIND_MINE),
            if (iTotal < 0) 0.0 else c.getDouble(iTotal),
            if (iPaid < 0) 0.0 else c.getDouble(iPaid),
            if (iPurpose < 0) "" else c.getString(iPurpose) ?: "",
            if (iDate < 0) "" else c.getString(iDate),
            if (iIso < 0) null else c.getString(iIso)
        )
    }

    // ---------- Recurring ----------

    fun getRecurringTemplates(): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val db = readableDatabase
        val c = db.query(TABLE_TRANSACTIONS, null, "$COLUMN_RECURRING!=?",
            arrayOf(Transaction.FREQ_NONE), null, null, "$COLUMN_ID DESC")
        if (c != null) {
            while (c.moveToNext()) list.add(fromCursor(c))
            c.close()
        }
        db.close()
        return list
    }

    /** Insert due recurring templates for today. Returns number inserted. */
    fun processDueRecurring(todayIso: String): Int {
        val templates = getRecurringTemplates()
        var inserted = 0
        val display = FormatHelper.toDisplay(todayIso)
        for (t in templates) {
            val last = t.lastRun ?: t.dateIso
            val due: Boolean
            if (last == null) {
                due = true
            } else if (last >= todayIso) {
                due = false
            } else {
                val freq = t.recurring
                due = when {
                    Transaction.FREQ_DAILY.equals(freq, ignoreCase = true) -> true
                    Transaction.FREQ_WEEKLY.equals(freq, ignoreCase = true) ->
                        FormatHelper.addDays(last, 7) <= todayIso
                    Transaction.FREQ_MONTHLY.equals(freq, ignoreCase = true) ->
                        last.substring(0, 7) != todayIso.substring(0, 7)
                    else -> false
                }
            }
            if (due) {
                val copy = Transaction(t.title, t.amount, t.type, t.category, display)
                copy.dateIso = todayIso
                copy.recurring = Transaction.FREQ_NONE
                copy.auto = 1
                insertTransaction(copy)
                t.lastRun = todayIso
                updateTransaction(t)
                inserted++
            }
        }
        return inserted
    }

    // ---------- Backup / restore ----------

    @Throws(Exception::class)
    fun exportJson(): String {
        val root = JSONObject()
        val arr = JSONArray()
        for (t in getAllTransactions()) {
            val o = JSONObject()
            o.put("title", t.title)
            o.put("amount", t.amount)
            o.put("type", t.type)
            o.put("category", t.category)
            o.put("date", t.date)
            o.put("date_iso", t.dateIso)
            o.put("recurring", t.recurring)
            o.put("last_run", if (t.lastRun == null) JSONObject.NULL else t.lastRun)
            o.put("is_auto", t.auto)
            arr.put(o)
        }
        root.put("transactions", arr)
        val b = JSONArray()
        for (bg in getAllBudgets()) {
            val o = JSONObject()
            o.put("category", bg.category)
            o.put("limit", bg.limit)
            b.put(o)
        }
        root.put("budgets", b)
        val g = JSONArray()
        for (goal in getAllGoals()) {
            val o = JSONObject()
            o.put("name", goal.name)
            o.put("target", goal.target)
            o.put("saved", goal.saved)
            o.put("target_date", if (goal.targetDate == null) JSONObject.NULL else goal.targetDate)
            o.put("calendar_event_id",
                if (goal.calendarEventId == null) JSONObject.NULL else goal.calendarEventId)
            o.put("reminder_days", if (goal.reminderDays == null) JSONObject.NULL else goal.reminderDays)
            g.put(o)
        }
        root.put("goals", g)
        val dt = JSONArray()
        for (debt in getAllDebts()) {
            val o = JSONObject()
            o.put("name", debt.name)
            o.put("kind", debt.kind)
            o.put("total", debt.total)
            o.put("paid", debt.paid)
            o.put("purpose", debt.purpose)
            o.put("date", debt.date)
            o.put("date_iso", debt.dateIso)
            dt.put(o)
        }
        root.put("debts", dt)
        root.put("version", DATABASE_VERSION)
        return root.toString()
    }

    @Throws(Exception::class)
    fun importJson(json: String): Int {
        val root = JSONObject(json)
        val arr = root.getJSONArray("transactions")
        val db = writableDatabase
        db.beginTransaction()
        var count = 0
        try {
            db.delete(TABLE_TRANSACTIONS, null, null)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val t = Transaction(
                    o.optString("title", ""),
                    o.optDouble("amount", 0.0),
                    o.optString("type", TYPE_EXPENSE),
                    o.optString("category", "Lainnya"),
                    o.optString("date", ""))
                t.dateIso = if (o.isNull("date_iso")) null else o.optString("date_iso", null)
                t.recurring = o.optString("recurring", Transaction.FREQ_NONE)
                t.lastRun = if (o.isNull("last_run")) null else o.optString("last_run", null)
                t.auto = o.optInt("is_auto", 0)
                val v = toValues(t)
                db.insert(TABLE_TRANSACTIONS, null, v)
                count++
            }
            if (root.has("budgets")) {
                db.delete(TABLE_BUDGETS, null, null)
                val b = root.getJSONArray("budgets")
                for (i in 0 until b.length()) {
                    val o = b.getJSONObject(i)
                    val v = ContentValues()
                    v.put(COLUMN_BUDGET_CATEGORY, o.optString("category", ""))
                    v.put(COLUMN_BUDGET_LIMIT, o.optDouble("limit", 0.0))
                    db.insert(TABLE_BUDGETS, null, v)
                }
            }
            if (root.has("goals")) {
                db.delete(TABLE_GOALS, null, null)
                val g = root.getJSONArray("goals")
                for (i in 0 until g.length()) {
                    val o = g.getJSONObject(i)
                    val v = ContentValues()
                    v.put(COLUMN_GOAL_NAME, o.optString("name", ""))
                    v.put(COLUMN_GOAL_TARGET, o.optDouble("target", 0.0))
                    v.put(COLUMN_GOAL_SAVED, o.optDouble("saved", 0.0))
                    if (o.isNull("target_date")) v.putNull(COLUMN_GOAL_TARGET_DATE)
                    else v.put(COLUMN_GOAL_TARGET_DATE, o.optString("target_date", null))
                    if (o.isNull("calendar_event_id")) v.putNull(COLUMN_GOAL_CALENDAR_EVENT)
                    else v.put(COLUMN_GOAL_CALENDAR_EVENT, o.optLong("calendar_event_id"))
                    if (o.isNull("reminder_days")) v.putNull(COLUMN_GOAL_REMINDER_DAYS)
                    else v.put(COLUMN_GOAL_REMINDER_DAYS, o.optString("reminder_days", null))
                    db.insert(TABLE_GOALS, null, v)
                }
            }
            if (root.has("debts")) {
                db.delete(TABLE_DEBTS, null, null)
                val dt = root.getJSONArray("debts")
                for (i in 0 until dt.length()) {
                    val o = dt.getJSONObject(i)
                    val d = Debt(
                        o.optString("name", ""),
                        o.optString("kind", Debt.KIND_MINE),
                        o.optDouble("total", 0.0),
                        o.optDouble("paid", 0.0),
                        o.optString("purpose", ""),
                        o.optString("date", ""))
                    d.dateIso = if (o.isNull("date_iso")) null else o.optString("date_iso", null)
                    db.insert(TABLE_DEBTS, null, debtValues(d))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
        return count
    }

    companion object {
        private const val DATABASE_NAME = "budgeting.db"
        private const val DATABASE_VERSION = 5

        const val TABLE_TRANSACTIONS = "transactions"
        const val COLUMN_ID = "_id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_AMOUNT = "amount"
        const val COLUMN_TYPE = "type"
        const val COLUMN_CATEGORY = "category"
        const val COLUMN_DATE = "date"
        const val COLUMN_DATE_ISO = "date_iso"
        const val COLUMN_RECURRING = "recurring"
        const val COLUMN_LAST_RUN = "last_run"
        const val COLUMN_AUTO = "is_auto"

        const val TABLE_BUDGETS = "budgets"
        const val COLUMN_BUDGET_CATEGORY = "category"
        const val COLUMN_BUDGET_LIMIT = "limit_amount"

        const val TABLE_GOALS = "goals"
        const val COLUMN_GOAL_ID = "_id"
        const val COLUMN_GOAL_NAME = "name"
        const val COLUMN_GOAL_TARGET = "target"
        const val COLUMN_GOAL_SAVED = "saved"
        const val COLUMN_GOAL_TARGET_DATE = "target_date"
        const val COLUMN_GOAL_CALENDAR_EVENT = "calendar_event_id"
        const val COLUMN_GOAL_REMINDER_DAYS = "reminder_days"

        const val TABLE_DEBTS = "debts"
        const val COLUMN_DEBT_ID = "_id"
        const val COLUMN_DEBT_NAME = "name"
        const val COLUMN_DEBT_KIND = "kind"
        const val COLUMN_DEBT_TOTAL = "total"
        const val COLUMN_DEBT_PAID = "paid"
        const val COLUMN_DEBT_PURPOSE = "purpose"
        const val COLUMN_DEBT_DATE = "date"
        const val COLUMN_DEBT_DATE_ISO = "date_iso"

        const val TYPE_INCOME = "Pemasukan"
        const val TYPE_EXPENSE = "Pengeluaran"

        private const val TABLE_CREATE_V2 =
            "CREATE TABLE transactions (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "title TEXT, amount REAL, type TEXT, category TEXT, date TEXT, " +
                    "date_iso TEXT, recurring TEXT DEFAULT 'NONE', last_run TEXT, " +
                    "is_auto INTEGER DEFAULT 0)"

        private const val BUDGET_CREATE =
            "CREATE TABLE budgets (category TEXT PRIMARY KEY, limit_amount REAL)"

        private const val GOAL_CREATE =
            "CREATE TABLE goals (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT, target REAL, saved REAL DEFAULT 0, " +
                    "target_date TEXT, calendar_event_id INTEGER, " +
                    "reminder_days TEXT DEFAULT '')"

        private const val DEBT_CREATE =
            "CREATE TABLE debts (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT, kind TEXT DEFAULT 'MINE', total REAL, " +
                    "paid REAL DEFAULT 0, purpose TEXT, date TEXT, date_iso TEXT)"
    }
}
