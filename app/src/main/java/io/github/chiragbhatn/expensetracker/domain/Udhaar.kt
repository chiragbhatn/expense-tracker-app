package io.github.chiragbhatn.expensetracker.domain

import java.time.LocalDate

data class Person(
    val id: Long,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    /** A copy of the profile photo in the app's private storage. */
    val photoPath: String? = null,
    val uuid: String = "",
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

/** Direction of a ledger entry, from the user's point of view. Stored by name. */
enum class UdhaarDirection {
    /** Value went from you to them (you paid for them, lent or paid them): they owe you more. */
    GAVE,

    /** Value came from them to you (they paid you or lent you): they owe you less. */
    GOT,
}

/** What a ledger entry records. Stored by name, so constants must not be renamed. */
enum class LedgerType(val label: String, val fixedDirection: UdhaarDirection?) {
    /** Their share of an expense you paid. Created and updated by the expense. */
    EXPENSE_SHARE("Expense share", UdhaarDirection.GAVE),
    UDHAAR_GIVEN("Udhaar given", UdhaarDirection.GAVE),
    UDHAAR_TAKEN("Udhaar taken", UdhaarDirection.GOT),
    PAYMENT_RECEIVED("Payment received", UdhaarDirection.GOT),
    PAYMENT_MADE("Payment made", UdhaarDirection.GAVE),

    /** Settles the balance; they pay you when they owe you, you pay them otherwise. */
    SETTLEMENT("Settlement", null),

    /** A correction in either direction. */
    ADJUSTMENT("Adjustment", null);

    companion object {
        /** Types recorded by hand on a person's page (expense shares come from expenses). */
        val manual = listOf(UDHAAR_GIVEN, UDHAAR_TAKEN, PAYMENT_RECEIVED, PAYMENT_MADE, ADJUSTMENT)

        /** The type a V1 entry is migrated to. */
        fun fromV1(direction: UdhaarDirection, expenseId: Long?) = when {
            direction == UdhaarDirection.GOT -> PAYMENT_RECEIVED
            expenseId != null -> EXPENSE_SHARE
            else -> UDHAAR_GIVEN
        }
    }
}

data class UdhaarEntry(
    val id: Long,
    val personId: Long,
    val direction: UdhaarDirection,
    val amount: Money,
    val date: LocalDate,
    val note: String,
    /** The expense this share belongs to, for [LedgerType.EXPENSE_SHARE] entries. */
    val expenseId: Long?,
    val expenseMerchant: String?,
    val type: LedgerType = LedgerType.fromV1(direction, expenseId),
    val uuid: String = "",
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    /** Effect on the balance: positive when it increases what the person owes you. */
    val signedAmount: Money get() = if (direction == UdhaarDirection.GAVE) amount else -amount
}

enum class BalanceState { OWES_YOU, SETTLED, HAS_CREDIT, YOU_OWE }

/**
 * A person's position, keeping three things apart:
 * - [receivable]: what they owe you;
 * - [payable]: what you owe them because you borrowed from them;
 * - [credit]: what they paid beyond what they owed, to be adjusted later.
 */
data class LedgerSummary(
    /** Everything charged to them: expense shares, udhaar given and money you paid them. */
    val totalDue: Money,
    /** Everything that came from them: payments, settlements and money they lent you. */
    val totalPaid: Money,
    /** Money you borrowed from them and have not repaid; never more than you hold of theirs. */
    val borrowed: Money,
) {
    /** Positive: they owe you. Negative: you hold their money. */
    val balance: Money get() = totalDue - totalPaid

    val receivable: Money get() = if (balance.isPositive) balance else Money.ZERO

    val payable: Money get() = if (balance.isNegative) minOf(balance.abs(), borrowed) else Money.ZERO

    val credit: Money get() = if (balance.isNegative) balance.abs() - payable else Money.ZERO

    val state: BalanceState
        get() = when {
            balance.isPositive -> BalanceState.OWES_YOU
            balance.isZero -> BalanceState.SETTLED
            payable.isPositive -> BalanceState.YOU_OWE
            else -> BalanceState.HAS_CREDIT
        }

    /** The position after [entry]; entries must be applied oldest first. */
    operator fun plus(entry: UdhaarEntry): LedgerSummary = after(entry.direction, entry.type, entry.amount)

    fun after(direction: UdhaarDirection, type: LedgerType, amount: Money): LedgerSummary {
        val due = if (direction == UdhaarDirection.GAVE) totalDue + amount else totalDue
        val paid = if (direction == UdhaarDirection.GOT) totalPaid + amount else totalPaid
        val stillBorrowed = when {
            type == LedgerType.UDHAAR_TAKEN -> borrowed + amount
            direction == UdhaarDirection.GAVE && (type == LedgerType.PAYMENT_MADE || type == LedgerType.SETTLEMENT) -> borrowed - amount
            else -> borrowed
        }
        // Borrowing only counts while you actually hold their money: once the
        // balance is back to zero or they owe you, it has been repaid or offset.
        val held = maxOf(Money.ZERO, paid - due)
        return LedgerSummary(due, paid, stillBorrowed.coerceIn(Money.ZERO, held))
    }

    companion object {
        val EMPTY = LedgerSummary(Money.ZERO, Money.ZERO, Money.ZERO)

        /** Oldest first; entries on the same day in the order they were recorded. */
        val CHRONOLOGICAL: Comparator<UdhaarEntry> =
            compareBy<UdhaarEntry> { it.date }.thenBy { it.createdAtMillis }.thenBy { it.id }

        fun of(entries: List<UdhaarEntry>): LedgerSummary =
            entries.sortedWith(CHRONOLOGICAL).fold(EMPTY) { summary, entry -> summary + entry }
    }
}

data class PersonBalance(
    val person: Person,
    val summary: LedgerSummary,
    /** Date of the latest entry, or null when there are none. */
    val lastActivity: LocalDate? = null,
) {
    val balance: Money get() = summary.balance
}

/** Everyone's position, people with something outstanding first. Only entries up to [asOf] count. */
fun balances(people: List<Person>, entries: List<UdhaarEntry>, asOf: LocalDate? = null): List<PersonBalance> {
    val relevant = if (asOf == null) entries else entries.filter { !it.date.isAfter(asOf) }
    val byPerson = relevant.groupBy { it.personId }
    return people
        .map { person ->
            val own = byPerson[person.id].orEmpty()
            PersonBalance(person, LedgerSummary.of(own), own.maxOfOrNull { it.date })
        }
        .sortedWith(
            compareBy<PersonBalance> { it.balance.isZero }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.person.name },
        )
}

fun List<UdhaarEntry>.balance(): Money = sumMoney { it.signedAmount }

/** Total that people owe you. */
fun List<PersonBalance>.moneyToReceive(): Money = sumMoney { it.summary.receivable }

/** Total you owe people: money you borrowed plus credit they hold with you. */
fun List<PersonBalance>.moneyToGive(): Money = sumMoney { it.summary.payable + it.summary.credit }

/** Total you owe people because you borrowed from them. */
fun List<PersonBalance>.moneyToPay(): Money = sumMoney { it.summary.payable }

/** Total that people paid you beyond what they owed, held as their credit. */
fun List<PersonBalance>.creditHeld(): Money = sumMoney { it.summary.credit }

/** People who have owed you money with no ledger activity for more than [days] days. */
fun List<PersonBalance>.overdue(today: LocalDate, days: Long = 30): List<PersonBalance> =
    filter { balance ->
        val last = balance.lastActivity
        balance.summary.receivable.isPositive && last != null && last.isBefore(today.minusDays(days))
    }
