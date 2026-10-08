package io.github.chiragbhatn.expensetracker.data

import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.AttachmentEntity
import io.github.chiragbhatn.expensetracker.data.db.CardPaymentEntity
import io.github.chiragbhatn.expensetracker.data.db.CashbackRuleEntity
import io.github.chiragbhatn.expensetracker.data.db.CategoryEntity
import io.github.chiragbhatn.expensetracker.data.db.CreditCardEntity
import io.github.chiragbhatn.expensetracker.data.db.ExpenseEntity
import io.github.chiragbhatn.expensetracker.data.db.IncomeEntity
import io.github.chiragbhatn.expensetracker.data.db.PersonEntity
import io.github.chiragbhatn.expensetracker.data.db.RecurringExpenseEntity
import io.github.chiragbhatn.expensetracker.data.db.ReminderEntity
import io.github.chiragbhatn.expensetracker.data.db.UdhaarEntryEntity
import io.github.chiragbhatn.expensetracker.domain.CardMath
import io.github.chiragbhatn.expensetracker.domain.CardPayment
import io.github.chiragbhatn.expensetracker.domain.CardSummary
import io.github.chiragbhatn.expensetracker.domain.CashbackRule
import io.github.chiragbhatn.expensetracker.domain.Category
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.CreditCard
import io.github.chiragbhatn.expensetracker.domain.Expense
import io.github.chiragbhatn.expensetracker.domain.Income
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.PersonBalance
import io.github.chiragbhatn.expensetracker.domain.PersonShare
import io.github.chiragbhatn.expensetracker.domain.RecurringExpense
import io.github.chiragbhatn.expensetracker.domain.Reminder
import io.github.chiragbhatn.expensetracker.domain.SearchData
import io.github.chiragbhatn.expensetracker.domain.UdhaarEntry
import io.github.chiragbhatn.expensetracker.domain.balances
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * Everything the app has recorded, as domain objects. Screens derive their
 * totals from this one consistent snapshot, so a number never disagrees
 * with another screen's.
 */
data class AppData(
    val people: List<Person> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val incomes: List<Income> = emptyList(),
    val entries: List<UdhaarEntry> = emptyList(),
    val cards: List<CreditCard> = emptyList(),
    val cardPayments: List<CardPayment> = emptyList(),
    val rules: List<CashbackRule> = emptyList(),
    val recurring: List<RecurringExpense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
) {
    val peopleById: Map<Long, Person> by lazy { people.associateBy { it.id } }
    val cardsById: Map<Long, CreditCard> by lazy { cards.associateBy { it.id } }
    val expensesById: Map<Long, Expense> by lazy { expenses.associateBy { it.id } }
    val entriesByPerson: Map<Long, List<UdhaarEntry>> by lazy { entries.groupBy { it.personId } }

    val balances: List<PersonBalance> by lazy { balances(people, entries) }

    fun categoryNames(kind: CategoryKind): List<String> = categories.filter { it.kind == kind }.map { it.name }

    fun cardSummaries(today: LocalDate): List<CardSummary> = cards.map { CardMath.summary(it, expenses, cardPayments, today) }

    fun searchData() = SearchData(people, expenses, incomes, entries, cards)

    /** Merchants seen in expenses and rules, most recent first. */
    val merchants: List<String> by lazy {
        (expenses.sortedByDescending { it.date }.map { it.merchant } + rules.map { it.merchant })
            .distinctBy { it.lowercase() }
    }

    companion object {
        val EMPTY = AppData()
    }
}

/** Observes all tables and rebuilds [AppData] whenever any of them changes. */
fun AppDatabase.observeAppData(): Flow<AppData> {
    val tables: List<Flow<List<Any>>> = listOf(
        personDao().observeAll(),
        expenseDao().observeAll(),
        udhaarDao().observeAll(),
        incomeDao().observeAll(),
        cardDao().observeAll(),
        cardDao().observePayments(),
        cashbackRuleDao().observeAll(),
        recurringDao().observeAll(),
        categoryDao().observeAll(),
        reminderDao().observeAll(),
        attachmentDao().observeAll(),
    )
    return combine(tables) { values ->
        @Suppress("UNCHECKED_CAST")
        fun <T> table(index: Int) = values[index] as List<T>
        buildAppData(
            people = table(0),
            expenses = table(1),
            entries = table(2),
            incomes = table(3),
            cards = table(4),
            payments = table(5),
            rules = table(6),
            recurring = table(7),
            categories = table(8),
            reminders = table(9),
            attachments = table(10),
        )
    }
}

internal fun buildAppData(
    people: List<PersonEntity>,
    expenses: List<ExpenseEntity>,
    entries: List<UdhaarEntryEntity>,
    incomes: List<IncomeEntity>,
    cards: List<CreditCardEntity>,
    payments: List<CardPaymentEntity>,
    rules: List<CashbackRuleEntity>,
    recurring: List<RecurringExpenseEntity>,
    categories: List<CategoryEntity>,
    reminders: List<ReminderEntity>,
    attachments: List<AttachmentEntity>,
): AppData {
    val domainPeople = people.map { it.toDomain() }
    val peopleById = domainPeople.associateBy { it.id }
    val merchants = expenses.associate { it.id to it.merchant }
    val sharesByExpense = entries
        .filter { it.type == LedgerType.EXPENSE_SHARE && it.expenseId != null }
        .groupBy { it.expenseId!! }
    return AppData(
        people = domainPeople,
        expenses = expenses.map { expense ->
            val shares = sharesByExpense[expense.id].orEmpty()
                .sortedBy { it.id }
                .mapNotNull { share -> peopleById[share.personId]?.let { PersonShare(it, Money(share.amountPaise)) } }
            expense.toDomain(shares)
        },
        incomes = incomes.map { it.toDomain() },
        entries = entries.map { it.toDomain(it.expenseId?.let(merchants::get)) },
        cards = cards.map { it.toDomain() },
        cardPayments = payments.map { it.toDomain() },
        rules = rules.map { it.toDomain() },
        recurring = recurring.map { it.toDomain() },
        categories = categories.map { it.toDomain() },
        reminders = reminders.map { it.toDomain() },
        attachments = attachments.map { it.toDomain() },
    )
}
