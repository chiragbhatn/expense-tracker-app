package io.github.chiragbhatn.expensetracker.domain

import java.time.LocalDate
import java.time.YearMonth

data class CreditCard(
    val id: Long,
    val name: String,
    val bank: String,
    val lastFour: String,
    val creditLimit: Money,
    /** Day of the month the statement is generated; short months use their last day. */
    val statementDay: Int,
    /** Day of the month the bill is due. */
    val dueDay: Int,
    val notes: String,
    val active: Boolean,
    /** Days before the due date to remind you; null for no reminder. */
    val reminderDaysBefore: Int? = null,
    val uuid: String = "",
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    val displayName: String get() = if (lastFour.isBlank()) name else "$name ••$lastFour"
}

/** A payment towards a card's bill. Not an expense: it moves money, it doesn't spend it. */
data class CardPayment(
    val id: Long,
    val cardId: Long,
    val amount: Money,
    val date: LocalDate,
    val note: String,
    val uuid: String = "",
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

data class AmountByKey(val key: String, val amount: Money)

data class CardSummary(
    val card: CreditCard,
    /** Everything charged to the card, before cashback. */
    val totalSpending: Money,
    val cashbackEarned: Money,
    val totalPaid: Money,
    /** Charges minus cashback (credited to the statement) minus payments. */
    val outstanding: Money,
    val availableLimit: Money,
    val monthSpending: Money,
    /** Charges that other people owe you for. */
    val spentOnBehalfOfOthers: Money,
    val byMerchant: List<AmountByKey>,
    val byCategory: List<AmountByKey>,
    val lastStatementDate: LocalDate,
    val nextStatementDate: LocalDate,
    /** What the last statement billed and is still unpaid. */
    val statementBalance: Money,
    /** When the last statement's bill is due. */
    val dueDate: LocalDate,
) {
    val isOverLimit: Boolean get() = outstanding > card.creditLimit
}

/** A card bill that is due soon, or overdue. */
data class UpcomingCardPayment(val card: CreditCard, val dueDate: LocalDate, val amount: Money, val overdue: Boolean)

object CardMath {
    /** [day] in [month], or the month's last day when the month is shorter. */
    fun dayIn(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /** The latest date on or before [date] that falls on [day] of its month. */
    fun lastOnOrBefore(day: Int, date: LocalDate): LocalDate {
        val thisMonth = dayIn(YearMonth.from(date), day)
        return if (!thisMonth.isAfter(date)) thisMonth else dayIn(YearMonth.from(date).minusMonths(1), day)
    }

    /** The first date strictly after [date] that falls on [day] of its month. */
    fun firstAfter(day: Int, date: LocalDate): LocalDate {
        val thisMonth = dayIn(YearMonth.from(date), day)
        return if (thisMonth.isAfter(date)) thisMonth else dayIn(YearMonth.from(date).plusMonths(1), day)
    }

    fun summary(card: CreditCard, expenses: List<Expense>, payments: List<CardPayment>, today: LocalDate): CardSummary {
        val onCard = expenses.filter { it.cardId == card.id }
        val spending = onCard.sumMoney { it.amounts.originalAmount }
        val cashback = onCard.sumMoney { it.amounts.cashbackAmount }
        val paid = payments.filter { it.cardId == card.id }.sumMoney { it.amount }
        val outstanding = spending - cashback - paid
        val lastStatement = lastOnOrBefore(card.statementDay, today)
        val billed = onCard.filter { !it.date.isAfter(lastStatement) }.sumMoney { it.amounts.effectiveAmount }
        val month = YearMonth.from(today)
        return CardSummary(
            card = card,
            totalSpending = spending,
            cashbackEarned = cashback,
            totalPaid = paid,
            outstanding = outstanding,
            availableLimit = maxOf(Money.ZERO, card.creditLimit - outstanding),
            monthSpending = onCard.filter { YearMonth.from(it.date) == month }.sumMoney { it.amounts.originalAmount },
            spentOnBehalfOfOthers = onCard.sumMoney { it.othersShare },
            byMerchant = onCard.totalsBy { it.merchant },
            byCategory = onCard.totalsBy { it.category },
            lastStatementDate = lastStatement,
            nextStatementDate = firstAfter(card.statementDay, today),
            statementBalance = maxOf(Money.ZERO, billed - paid),
            dueDate = firstAfter(card.dueDay, lastStatement),
        )
    }

    /** Active cards with an unpaid statement due within [withinDays], soonest first. */
    fun upcomingPayments(summaries: List<CardSummary>, today: LocalDate, withinDays: Long = 31): List<UpcomingCardPayment> =
        summaries
            .filter { it.card.active && it.statementBalance.isPositive && !it.dueDate.isAfter(today.plusDays(withinDays)) }
            .map { UpcomingCardPayment(it.card, it.dueDate, it.statementBalance, overdue = it.dueDate.isBefore(today)) }
            .sortedBy { it.dueDate }
}

/** Original amounts grouped by [key], largest first. */
fun List<Expense>.totalsBy(key: (Expense) -> String): List<AmountByKey> =
    groupBy(key)
        .map { (name, expenses) -> AmountByKey(name, expenses.sumMoney { it.amounts.originalAmount }) }
        .sortedByDescending { it.amount }
