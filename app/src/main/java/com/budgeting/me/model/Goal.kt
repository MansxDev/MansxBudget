package com.budgeting.me.model

data class Goal(
    var id: Long = 0,
    var name: String = "",
    var target: Double = 0.0,
    var saved: Double = 0.0,
    var targetDate: String? = null,
    var calendarEventId: Long? = null,
    var reminderDays: String? = null
) {
    constructor(name: String, target: Double, saved: Double) : this(0, name, target, saved)

    fun getRatio(): Float {
        if (target <= 0) return 0f
        return minOf(saved / target, 1.0).toFloat()
    }

    fun isDone(): Boolean = target > 0 && saved >= target

    fun reminderList(): List<Int> {
        val csv = reminderDays?.trim().orEmpty()
        if (csv.isEmpty()) return emptyList()
        return csv.split(",").mapNotNull { it.trim().toIntOrNull() }
    }
}