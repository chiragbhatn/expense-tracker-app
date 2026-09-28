package io.github.chiragbhatn.expensetracker.domain

import java.time.LocalDate

data class Expense(
    val id: Long,
    val merchant: String,
    val paymentMethod: PaymentMethod,
    val amounts: CashbackBreakdown,
    /** Who the purchase was for; null when it was for the user. */
    val paidFor: Person?,
    val date: LocalDate,
    val note: String,
)

/** What the user entered in the expense form. */
data class ExpenseInput(
    val originalAmount: Money,
    val cashbackPercentage: Percentage,
    val merchant: String,
    val paymentMethod: PaymentMethod,
    val paidForPersonId: Long?,
    val date: LocalDate,
    val note: String,
)

/**
 * How one transaction is recorded. The two sides are kept apart on purpose:
 * cashback belongs to the card holder, so it lowers the user's effective
 * expense but never the amount the other person owes.
 */
data class LedgerPosting(
    /** Expense side: original card spend, cashback and effective expense. */
    val expense: CashbackBreakdown,
    /** Udhaar side: what the person paid for owes — the full original amount — or null. */
    val udhaarOwed: Money?,
)

fun ExpenseInput.toLedgerPosting(): LedgerPosting {
    val expense = CashbackBreakdown.calculate(originalAmount, cashbackPercentage)
    return LedgerPosting(
        expense = expense,
        udhaarOwed = if (paidForPersonId != null) expense.originalAmount else null,
    )
}
