package io.github.chiragbhatn.expensetracker.domain

data class SpendingSummary(
    /** Everything charged to the card, before cashback. */
    val cardSpending: Money,
    /** UPI, cash and other payments. */
    val otherSpending: Money,
    val cashbackReceived: Money,
    /** What the spending cost the user after cashback. */
    val effectiveExpenses: Money,
    val transactionCount: Int,
) {
    companion object {
        fun of(expenses: List<Expense>): SpendingSummary {
            val (card, other) = expenses.partition { it.paymentMethod == PaymentMethod.CARD }
            return SpendingSummary(
                cardSpending = card.sumMoney { it.amounts.originalAmount },
                otherSpending = other.sumMoney { it.amounts.originalAmount },
                cashbackReceived = expenses.sumMoney { it.amounts.cashbackAmount },
                effectiveExpenses = expenses.sumMoney { it.amounts.effectiveAmount },
                transactionCount = expenses.size,
            )
        }
    }
}
