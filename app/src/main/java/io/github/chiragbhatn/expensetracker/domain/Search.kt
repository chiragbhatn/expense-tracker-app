package io.github.chiragbhatn.expensetracker.domain

import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything global search looks through. */
data class SearchData(
    val people: List<Person>,
    val expenses: List<Expense>,
    val incomes: List<Income>,
    val entries: List<UdhaarEntry>,
    val cards: List<CreditCard>,
)

data class PersonMatch(val person: Person, val summary: LedgerSummary, val expenseCount: Int, val paymentCount: Int)

/** A merchant and what was spent there, with whom and on which cards. */
data class MerchantMatch(
    val merchant: String,
    val original: Money,
    val cashback: Money,
    val effective: Money,
    val count: Int,
    val people: List<Person>,
    val cards: List<CreditCard>,
)

data class CardMatch(val card: CreditCard, val spending: Money, val count: Int)

data class EntryMatch(val entry: UdhaarEntry, val person: Person?)

data class SearchResults(
    val query: String,
    val people: List<PersonMatch>,
    val merchants: List<MerchantMatch>,
    val cards: List<CardMatch>,
    val expenses: List<Expense>,
    val incomes: List<Income>,
    val transactions: List<EntryMatch>,
) {
    val isEmpty: Boolean
        get() = people.isEmpty() && merchants.isEmpty() && cards.isEmpty() &&
            expenses.isEmpty() && incomes.isEmpty() && transactions.isEmpty()

    companion object {
        val EMPTY = SearchResults("", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/**
 * Case-insensitive search across people, merchants, cards, expenses, income
 * and ledger entries. Every word of the query must appear somewhere in an
 * item: "swiggy rahul" finds Swiggy orders shared with Rahul.
 */
object Search {
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun run(query: String, data: SearchData): SearchResults {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return SearchResults.EMPTY
        fun matches(vararg fields: String?): Boolean {
            val text = fields.filterNotNull().joinToString(" ").lowercase(Locale.ROOT)
            return words.all { it in text }
        }

        val peopleById = data.people.associateBy { it.id }
        val cardsById = data.cards.associateBy { it.id }
        val entriesByPerson = data.entries.groupBy { it.personId }

        val people = data.people
            .filter { matches(it.name, it.phone, it.email, it.address, it.notes, it.tags.joinToString(" ")) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .map { person ->
                val own = entriesByPerson[person.id].orEmpty()
                PersonMatch(
                    person = person,
                    summary = LedgerSummary.of(own),
                    expenseCount = own.count { it.type == LedgerType.EXPENSE_SHARE },
                    paymentCount = own.count { it.type == LedgerType.PAYMENT_RECEIVED || it.type == LedgerType.PAYMENT_MADE || it.type == LedgerType.SETTLEMENT },
                )
            }

        val merchants = data.expenses
            .filter { matches(it.merchant) }
            .groupBy { nameKey(it.merchant) }
            .map { (_, items) ->
                MerchantMatch(
                    merchant = items.maxBy { it.date }.merchant,
                    original = items.sumMoney { it.amounts.originalAmount },
                    cashback = items.sumMoney { it.amounts.cashbackAmount },
                    effective = items.sumMoney { it.amounts.effectiveAmount },
                    count = items.size,
                    people = items.flatMap { it.paidFor }.distinctBy { it.id }.sortedBy { it.name.lowercase(Locale.ROOT) },
                    cards = items.mapNotNull { expense -> expense.cardId?.let(cardsById::get) }.distinctBy { it.id },
                )
            }
            .sortedByDescending { it.original }

        val cards = data.cards
            .filter { matches(it.name, it.bank, it.lastFour, it.notes) }
            .map { card ->
                val onCard = data.expenses.filter { it.cardId == card.id }
                CardMatch(card, onCard.sumMoney { it.amounts.originalAmount }, onCard.size)
            }

        val expenses = data.expenses
            .filter { expense ->
                val card = expense.cardId?.let(cardsById::get)
                matches(
                    expense.merchant,
                    expense.category,
                    expense.note,
                    expense.paymentMethod.label,
                    card?.name,
                    card?.bank,
                    card?.lastFour,
                    expense.paidFor.joinToString(" ") { it.name },
                    expense.amounts.originalAmount.toInputString(),
                    expense.amounts.effectiveAmount.toInputString(),
                    expense.date.format(isoDate),
                    expense.date.format(shortDate),
                )
            }
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })

        val incomes = data.incomes
            .filter { matches(it.source, it.category, it.note, it.amount.toInputString(), it.date.format(isoDate), it.date.format(shortDate)) }
            .sortedWith(compareByDescending<Income> { it.date }.thenByDescending { it.id })

        val transactions = data.entries
            .filter { entry ->
                matches(
                    peopleById[entry.personId]?.name,
                    entry.type.label,
                    entry.note,
                    entry.expenseMerchant,
                    entry.amount.toInputString(),
                    entry.date.format(isoDate),
                    entry.date.format(shortDate),
                )
            }
            .sortedWith(LedgerSummary.CHRONOLOGICAL.reversed())
            .map { EntryMatch(it, peopleById[it.personId]) }

        return SearchResults(query.trim(), people, merchants, cards, expenses, incomes, transactions)
    }
}
