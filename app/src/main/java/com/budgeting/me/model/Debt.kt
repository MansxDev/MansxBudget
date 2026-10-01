package com.budgeting.me.model

/**
 * Catatan hutang/piutang.
 * kind = MINE (aku berhutang ke orang) atau THEIRS (teman berhutang ke aku).
 * Lunas = paid >= total (derived, seperti Goal.isDone).
 */
data class Debt(
    var id: Long = 0,
    var name: String = "",
    var kind: String = KIND_MINE,
    var total: Double = 0.0,
    var paid: Double = 0.0,
    var purpose: String = "",
    var date: String? = null,
    var dateIso: String? = null
) {
    constructor(
        name: String, kind: String, total: Double, paid: Double,
        purpose: String, date: String?
    ) : this(0, name, kind, total, paid, purpose, date, null)

    fun remaining(): Double = total - paid

    fun getRatio(): Float {
        if (total <= 0) return 0f
        return minOf(paid / total, 1.0).toFloat()
    }

    fun isDone(): Boolean = total > 0 && paid >= total

    fun isMine(): Boolean = KIND_MINE.equals(kind, ignoreCase = true)

    companion object {
        const val KIND_MINE = "MINE"
        const val KIND_THEIRS = "THEIRS"
    }
}
