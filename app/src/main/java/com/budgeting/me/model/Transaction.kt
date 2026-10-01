package com.budgeting.me.model

data class Transaction(
    var id: Long = -1,
    var title: String = "",
    var amount: Double = 0.0,
    var type: String = "",
    var category: String = "",
    var date: String? = null,
    var dateIso: String? = null,
    var recurring: String = FREQ_NONE,
    var lastRun: String? = null,
    var auto: Int = 0
) {
    constructor(title: String, amount: Double, type: String, category: String, date: String?) :
            this(-1, title, amount, type, category, date)

    fun isIncome(): Boolean = "Pemasukan".equals(type, ignoreCase = true)

    fun isRecurringTemplate(): Boolean = !FREQ_NONE.equals(recurring, ignoreCase = true)

    companion object {
        const val FREQ_NONE = "NONE"
        const val FREQ_DAILY = "DAILY"
        const val FREQ_WEEKLY = "WEEKLY"
        const val FREQ_MONTHLY = "MONTHLY"
    }
}
