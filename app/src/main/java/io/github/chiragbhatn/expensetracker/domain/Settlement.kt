package io.github.chiragbhatn.expensetracker.domain

enum class SettlementKind { FULL, PARTIAL, EXTRA }

/**
 * Settling up with a person. When they owe you, they pay you; when you owe
 * them (or they hold credit), you pay them. Paying more than the balance is
 * allowed: the extra becomes credit instead of a negative expense.
 */
object Settlement {
    /** GOT when they pay you, GAVE when you pay them. */
    fun directionFor(summary: LedgerSummary): UdhaarDirection =
        if (summary.balance.isNegative) UdhaarDirection.GAVE else UdhaarDirection.GOT

    /** The amount that brings the balance to zero. */
    fun fullAmount(summary: LedgerSummary): Money = summary.balance.abs()

    fun kind(summary: LedgerSummary, amount: Money): SettlementKind {
        val full = fullAmount(summary)
        return when {
            amount == full -> SettlementKind.FULL
            amount < full -> SettlementKind.PARTIAL
            else -> SettlementKind.EXTRA
        }
    }

    /** The position after a settlement payment of [amount]. */
    fun preview(summary: LedgerSummary, amount: Money): LedgerSummary {
        require(amount.isPositive) { "A settlement must be positive" }
        return summary.after(directionFor(summary), LedgerType.SETTLEMENT, amount)
    }
}
