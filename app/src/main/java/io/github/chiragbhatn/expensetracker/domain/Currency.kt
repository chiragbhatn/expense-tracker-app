package io.github.chiragbhatn.expensetracker.domain

/**
 * Display currency. Amounts are never converted: changing the currency only
 * changes how amounts are shown and exported.
 */
enum class Currency(val code: String, val symbol: String, val indianGrouping: Boolean) {
    INR("INR", "₹", true),
    USD("USD", "$", false),
    EUR("EUR", "€", false),
    GBP("GBP", "£", false),
    AED("AED", "AED ", false),
    SGD("SGD", "S$", false),
    AUD("AUD", "A$", false),
    CAD("CAD", "C$", false);

    val label: String get() = "$code (${symbol.trim()})"

    companion object {
        /** The currency amounts are currently shown in; follows the app setting. */
        @Volatile
        var display: Currency = INR

        fun fromCode(code: String?): Currency = entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: INR
    }
}
