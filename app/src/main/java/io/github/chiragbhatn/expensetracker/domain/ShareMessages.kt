package io.github.chiragbhatn.expensetracker.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Editable wording for the balance message. `{name}` and `{amount}` are
 * replaced with the person's name and the formatted amount.
 */
data class ShareTemplates(
    val owes: String = DEFAULT_OWES,
    val settled: String = DEFAULT_SETTLED,
    val credit: String = DEFAULT_CREDIT,
    val youOwe: String = DEFAULT_YOU_OWE,
) {
    companion object {
        const val DEFAULT_OWES =
            "Hi {name}, your current pending balance is {amount}. Please settle it when convenient. Thanks!"
        const val DEFAULT_SETTLED = "Hi {name}, your account is fully settled. Current balance: {amount}. Thanks!"
        const val DEFAULT_CREDIT =
            "Hi {name}, you've paid {amount} extra. You currently have a {amount} credit balance with me, " +
                "which will be adjusted against your next expense."
        const val DEFAULT_YOU_OWE = "Hi {name}, I owe you {amount}. I'll settle it soon. Thanks!"
    }
}

enum class ShareKind(val label: String) {
    CURRENT_BALANCE("Current balance"),
    DETAILED_STATEMENT("Detailed statement"),
    MONTHLY_SUMMARY("Monthly summary"),
}

object ShareMessages {
    private val day = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val monthName = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    /** "Rahul owes you ₹1,500", "Account settled ✓", "Rahul has ₹500 extra credit." */
    fun headline(name: String, summary: LedgerSummary, currency: Currency = Currency.display): String = when (summary.state) {
        BalanceState.OWES_YOU -> "$name owes you ${summary.receivable.format(currency)}"
        BalanceState.SETTLED -> "Account settled ✓"
        BalanceState.HAS_CREDIT -> "$name has ${summary.credit.format(currency)} extra credit."
        BalanceState.YOU_OWE -> "You owe $name ${summary.balance.abs().format(currency)}"
    }

    /** The short, WhatsApp-friendly message for the person's current balance. */
    fun currentBalance(name: String, summary: LedgerSummary, templates: ShareTemplates, currency: Currency = Currency.display): String {
        val (template, amount) = when (summary.state) {
            BalanceState.OWES_YOU -> templates.owes to summary.receivable
            BalanceState.SETTLED -> templates.settled to Money.ZERO
            BalanceState.HAS_CREDIT -> templates.credit to summary.credit
            BalanceState.YOU_OWE -> templates.youOwe to summary.balance.abs()
        }
        return fill(template, name, amount.format(currency))
    }

    /** Every entry with its date, then totals and the balance. */
    fun detailedStatement(
        name: String,
        entries: List<UdhaarEntry>,
        asOf: LocalDate,
        currency: Currency = Currency.display,
    ): String {
        val summary = LedgerSummary.of(entries)
        return buildString {
            appendLine("Statement for $name")
            appendLine("As of ${asOf.format(day)}")
            appendLine()
            if (entries.isEmpty()) appendLine("No transactions yet.")
            entries.sortedWith(LedgerSummary.CHRONOLOGICAL).forEach { entry ->
                appendLine("• ${entry.date.format(day)} — ${describe(entry)}: ${signed(entry, currency)}")
            }
            appendLine()
            appendLine("Total charged: ${summary.totalDue.format(currency)}")
            appendLine("Total paid: ${summary.totalPaid.format(currency)}")
            append("Balance: ${headline(name, summary, currency)}")
        }
    }

    /** The month's activity with opening and closing balances. */
    fun monthlySummary(
        name: String,
        entries: List<UdhaarEntry>,
        month: YearMonth,
        currency: Currency = Currency.display,
    ): String {
        val before = entries.filter { it.date.isBefore(month.atDay(1)) }
        val during = entries.filter { YearMonth.from(it.date) == month }
        val upToEnd = before + during
        val charged = during.filter { it.direction == UdhaarDirection.GAVE }.sumMoney { it.amount }
        val paid = during.filter { it.direction == UdhaarDirection.GOT }.sumMoney { it.amount }
        return buildString {
            appendLine("$name — ${month.format(monthName)}")
            appendLine("Opening balance: ${headline(name, LedgerSummary.of(before), currency)}")
            appendLine("Added this month: ${charged.format(currency)}")
            appendLine("Paid this month: ${paid.format(currency)}")
            if (during.isNotEmpty()) {
                appendLine()
                during.sortedWith(LedgerSummary.CHRONOLOGICAL).forEach { entry ->
                    appendLine("• ${entry.date.format(day)} — ${describe(entry)}: ${signed(entry, currency)}")
                }
                appendLine()
            }
            append("Closing balance: ${headline(name, LedgerSummary.of(upToEnd), currency)}")
        }
    }

    fun fill(template: String, name: String, amount: String): String =
        template.replace("{name}", name).replace("{amount}", amount)

    /** A ledger entry in words: "Swiggy (expense share)", "Payment received". */
    fun describe(entry: UdhaarEntry): String {
        val base = when {
            entry.type == LedgerType.EXPENSE_SHARE && entry.expenseMerchant != null -> "${entry.expenseMerchant} (expense share)"
            entry.type == LedgerType.SETTLEMENT && entry.direction == UdhaarDirection.GAVE -> "Settlement paid"
            entry.type == LedgerType.SETTLEMENT -> "Settlement received"
            else -> entry.type.label
        }
        return if (entry.note.isBlank() || entry.type == LedgerType.EXPENSE_SHARE) base else "$base, ${entry.note}"
    }

    private fun signed(entry: UdhaarEntry, currency: Currency): String =
        if (entry.direction == UdhaarDirection.GAVE) "+${entry.amount.format(currency)}" else (-entry.amount).format(currency)
}
