package io.github.chiragbhatn.expensetracker.domain

import java.time.LocalDate

data class Person(val id: Long, val name: String)

/** Direction of an udhaar entry, from the user's point of view. Stored by name. */
enum class UdhaarDirection {
    /** You gave money or paid for them: they owe you more. */
    GAVE,

    /** You got money from them: they owe you less. */
    GOT,
}

data class UdhaarEntry(
    val id: Long,
    val personId: Long,
    val direction: UdhaarDirection,
    val amount: Money,
    val date: LocalDate,
    val note: String,
    /** The card expense this entry came from when the user paid for the person. */
    val expenseId: Long?,
    val expenseMerchant: String?,
) {
    /** Effect on the balance: positive when it increases what the person owes you. */
    val signedAmount: Money get() = if (direction == UdhaarDirection.GAVE) amount else -amount
}

/** Positive: the person owes you. Negative: you owe them. */
data class PersonBalance(val person: Person, val balance: Money)

/** Everyone's balance, people with something outstanding first. */
fun balances(people: List<Person>, entries: List<UdhaarEntry>): List<PersonBalance> {
    val totals = entries.groupBy { it.personId }.mapValues { (_, own) -> own.sumMoney { it.signedAmount } }
    return people
        .map { PersonBalance(it, totals[it.id] ?: Money.ZERO) }
        .sortedWith(
            compareBy<PersonBalance> { it.balance.isZero }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.person.name },
        )
}

fun List<UdhaarEntry>.balance(): Money = sumMoney { it.signedAmount }

fun List<PersonBalance>.moneyToReceive(): Money = filter { it.balance.isPositive }.sumMoney { it.balance }

fun List<PersonBalance>.moneyToGive(): Money = filter { it.balance.isNegative }.sumMoney { -it.balance }
