package io.github.chiragbhatn.expensetracker.domain

import java.time.LocalDate

/** A person's part of a saved expense. */
data class PersonShare(val person: Person, val amount: Money)

data class Expense(
    val id: Long,
    val merchant: String,
    val paymentMethod: PaymentMethod,
    val amounts: CashbackBreakdown,
    /** The people this expense was (partly) for, and what each owes you for it. */
    val shares: List<PersonShare>,
    val date: LocalDate,
    val note: String,
    val category: String = DefaultCategories.OTHER,
    val cardId: Long? = null,
    /** The recurring expense that created this one, if any. */
    val recurringId: Long? = null,
    val uuid: String = "",
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    /** What others owe you for this expense. */
    val othersShare: Money get() = shares.sumMoney { it.amount }

    /**
     * Your own part: the effective amount minus what others owe. Negative only
     * for V1 expenses recorded under the old rule (person owed the full amount).
     */
    val myShare: Money get() = amounts.effectiveAmount - othersShare

    val paidFor: List<Person> get() = shares.map { it.person }

    /** The split as it was saved, for editing. */
    val split: Split get() = Split(maxOf(Money.ZERO, myShare), shares.map { Share(it.person.id, it.amount) })
}

/** What the user entered in the expense form. */
data class ExpenseInput(
    val originalAmount: Money,
    val cashbackPercentage: Percentage,
    val merchant: String,
    val paymentMethod: PaymentMethod,
    /** How the effective amount is divided between you and other people. */
    val split: Split,
    val date: LocalDate,
    val note: String,
    val category: String = DefaultCategories.OTHER,
    val cardId: Long? = null,
)

/**
 * How one transaction is recorded. Cashback is calculated on the original
 * amount and reduces both your expense and what others owe: everyone's shares
 * add up to the effective amount (original − cashback). The original amount is
 * kept unchanged alongside.
 */
data class LedgerPosting(
    /** Expense side: original card spend, cashback and effective expense. */
    val expense: CashbackBreakdown,
    val split: Split,
) {
    /** What each person owes you. */
    val shares: List<Share> get() = split.shares

    val problems: List<SplitProblem> get() = split.problems(expense.effectiveAmount)
}

fun ExpenseInput.toLedgerPosting() = LedgerPosting(CashbackBreakdown.calculate(originalAmount, cashbackPercentage), split)

data class Income(
    val id: Long,
    val amount: Money,
    /** Where the money came from, e.g. "Salary" or a client's name. */
    val source: String,
    val category: String,
    val date: LocalDate,
    val note: String,
    val uuid: String = "",
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

data class IncomeInput(
    val amount: Money,
    val source: String,
    val category: String,
    val date: LocalDate,
    val note: String,
)
