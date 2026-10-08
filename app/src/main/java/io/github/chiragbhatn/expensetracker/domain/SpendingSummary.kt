package io.github.chiragbhatn.expensetracker.domain

import java.time.LocalDate
import java.time.YearMonth

data class SpendingSummary(
    /** Everything charged to the card, before cashback. */
    val cardSpending: Money,
    /** UPI, cash and other payments. */
    val otherSpending: Money,
    val cashbackReceived: Money,
    /** What the spending cost after cashback: original − cashback. */
    val effectiveExpenses: Money,
    val transactionCount: Int,
) {
    /** All spending before cashback. */
    val originalExpenses: Money get() = cardSpending + otherSpending

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

/**
 * One month (or all time) at a glance. Cashback is counted once: original
 * expenses − cashback = effective expenses.
 */
data class MonthlySummary(
    val period: Period,
    val income: Money,
    val originalExpenses: Money,
    val cashback: Money,
    val effectiveExpenses: Money,
    /** Your own part of the effective expenses (the rest is owed by others). */
    val myShareOfExpenses: Money,
    /** What people owe you at the end of the period. */
    val receivable: Money,
    /** What you owe people (money you borrowed) at the end of the period. */
    val payable: Money,
    /** What people paid beyond what they owed, held as their credit, at the end of the period. */
    val credit: Money,
) {
    /** Income − effective expenses + money to receive − money to pay − credit you hold for people. */
    val netPosition: Money get() = income - effectiveExpenses + receivable - payable - credit

    /** Income − effective expenses. */
    val cashFlow: Money get() = income - effectiveExpenses

    companion object {
        fun of(
            period: Period,
            expenses: List<Expense>,
            incomes: List<Income>,
            people: List<Person>,
            entries: List<UdhaarEntry>,
            today: LocalDate,
        ): MonthlySummary {
            val days = period.epochDays
            val inPeriod = expenses.filter { it.date.toEpochDay() in days }
            val asOf = when (period) {
                is Period.Month -> minOf(period.month.atEndOfMonth(), today)
                Period.AllTime -> today
            }
            val positions = balances(people, entries, asOf)
            return MonthlySummary(
                period = period,
                income = incomes.filter { it.date.toEpochDay() in days }.sumMoney { it.amount },
                originalExpenses = inPeriod.sumMoney { it.amounts.originalAmount },
                cashback = inPeriod.sumMoney { it.amounts.cashbackAmount },
                effectiveExpenses = inPeriod.sumMoney { it.amounts.effectiveAmount },
                myShareOfExpenses = inPeriod.sumMoney { maxOf(Money.ZERO, it.myShare) },
                receivable = positions.moneyToReceive(),
                payable = positions.moneyToPay(),
                credit = positions.creditHeld(),
            )
        }
    }
}

data class MonthPoint(
    val month: YearMonth,
    val income: Money,
    val originalExpenses: Money,
    val cashback: Money,
    val effectiveExpenses: Money,
)

data class CategoryTotal(val category: String, val effective: Money, val count: Int, val fraction: Double)

data class MerchantTotal(val merchant: String, val original: Money, val cashback: Money, val effective: Money, val count: Int)

object Reports {
    /** Month-by-month totals for the [months] months ending with [lastMonth], oldest first. */
    fun trend(expenses: List<Expense>, incomes: List<Income>, lastMonth: YearMonth, months: Int): List<MonthPoint> {
        val expensesByMonth = expenses.groupBy { YearMonth.from(it.date) }
        val incomesByMonth = incomes.groupBy { YearMonth.from(it.date) }
        return (months - 1 downTo 0).map { back ->
            val month = lastMonth.minusMonths(back.toLong())
            val monthExpenses = expensesByMonth[month].orEmpty()
            MonthPoint(
                month = month,
                income = incomesByMonth[month].orEmpty().sumMoney { it.amount },
                originalExpenses = monthExpenses.sumMoney { it.amounts.originalAmount },
                cashback = monthExpenses.sumMoney { it.amounts.cashbackAmount },
                effectiveExpenses = monthExpenses.sumMoney { it.amounts.effectiveAmount },
            )
        }
    }

    /** Effective spending per category, largest first. Fractions add up to 1 when there is spending. */
    fun byCategory(expenses: List<Expense>): List<CategoryTotal> {
        val total = expenses.sumMoney { it.amounts.effectiveAmount }
        return expenses.groupBy { it.category }
            .map { (category, items) ->
                val effective = items.sumMoney { it.amounts.effectiveAmount }
                CategoryTotal(category, effective, items.size, if (total.isZero) 0.0 else effective.paise.toDouble() / total.paise)
            }
            .sortedByDescending { it.effective }
    }

    /** Spending per merchant (merchant names compared ignoring case), largest first. */
    fun byMerchant(expenses: List<Expense>): List<MerchantTotal> =
        expenses.groupBy { nameKey(it.merchant) }
            .map { (_, items) ->
                MerchantTotal(
                    merchant = items.first().merchant,
                    original = items.sumMoney { it.amounts.originalAmount },
                    cashback = items.sumMoney { it.amounts.cashbackAmount },
                    effective = items.sumMoney { it.amounts.effectiveAmount },
                    count = items.size,
                )
            }
            .sortedByDescending { it.original }

    /** Original card spending per card; expenses without a card are left out. */
    fun byCard(expenses: List<Expense>, cards: List<CreditCard>): List<AmountByKey> {
        val names = cards.associate { it.id to it.displayName }
        return expenses.filter { it.cardId != null }
            .groupBy { it.cardId }
            .map { (cardId, items) -> AmountByKey(names[cardId] ?: "Deleted card", items.sumMoney { it.amounts.originalAmount }) }
            .sortedByDescending { it.amount }
    }
}

data class CashbackStats(
    val today: Money,
    val thisMonth: Money,
    val thisYear: Money,
    val total: Money,
    /** Card spending in the selected month before cashback, and after. */
    val monthCardSpending: Money,
    val monthEffectiveCardSpending: Money,
    /** The selected month's cashback per merchant: the top few, then "Other". */
    val monthByMerchant: List<AmountByKey>,
    /** All-time cashback per card. */
    val byCard: List<AmountByKey>,
) {
    companion object {
        fun of(expenses: List<Expense>, cards: List<CreditCard>, today: LocalDate, month: YearMonth = YearMonth.from(today), topMerchants: Int = 3): CashbackStats {
            val inMonth = expenses.filter { YearMonth.from(it.date) == month }
            val cardInMonth = inMonth.filter { it.paymentMethod == PaymentMethod.CARD }
            val merchantCashback = inMonth.filter { it.amounts.hasCashback }
                .groupBy { nameKey(it.merchant) }
                .map { (_, items) -> AmountByKey(items.first().merchant, items.sumMoney { it.amounts.cashbackAmount }) }
                .sortedByDescending { it.amount }
            val top = merchantCashback.take(topMerchants)
            val rest = merchantCashback.drop(topMerchants).sumMoney { it.amount }
            val names = cards.associate { it.id to it.displayName }
            return CashbackStats(
                today = expenses.filter { it.date == today }.sumMoney { it.amounts.cashbackAmount },
                thisMonth = expenses.filter { YearMonth.from(it.date) == YearMonth.from(today) }.sumMoney { it.amounts.cashbackAmount },
                thisYear = expenses.filter { it.date.year == today.year }.sumMoney { it.amounts.cashbackAmount },
                total = expenses.sumMoney { it.amounts.cashbackAmount },
                monthCardSpending = cardInMonth.sumMoney { it.amounts.originalAmount },
                monthEffectiveCardSpending = cardInMonth.sumMoney { it.amounts.effectiveAmount },
                monthByMerchant = if (rest.isPositive) top + AmountByKey("Other", rest) else top,
                byCard = expenses.filter { it.amounts.hasCashback }
                    .groupBy { it.cardId }
                    .map { (cardId, items) ->
                        AmountByKey(cardId?.let { names[it] ?: "Deleted card" } ?: "No card selected", items.sumMoney { it.amounts.cashbackAmount })
                    }
                    .sortedByDescending { it.amount },
            )
        }
    }
}
