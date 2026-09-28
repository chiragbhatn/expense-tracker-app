package io.github.chiragbhatn.expensetracker.domain

// Stored by name in the database, so constants must not be renamed.
enum class PaymentMethod(val label: String) {
    CARD("Card"),
    UPI("UPI"),
    CASH("Cash"),
    OTHER("Other");

    /** Merchant cashback is a benefit of the user's card, so only card payments earn it. */
    val earnsCashback: Boolean get() = this == CARD
}
