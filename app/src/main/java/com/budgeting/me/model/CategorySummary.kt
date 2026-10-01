package com.budgeting.me.model

data class CategorySummary(
    var category: String = "",
    var income: Double = 0.0,
    var expense: Double = 0.0
) {
    val net: Double get() = income - expense
}
