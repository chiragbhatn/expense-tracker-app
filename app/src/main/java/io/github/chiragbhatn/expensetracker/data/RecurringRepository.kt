package io.github.chiragbhatn.expensetracker.data

import androidx.room.withTransaction
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.RecurringExpenseEntity
import io.github.chiragbhatn.expensetracker.domain.CashbackBreakdown
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.ExpenseInput
import io.github.chiragbhatn.expensetracker.domain.Frequency
import io.github.chiragbhatn.expensetracker.domain.IntervalUnit
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.RecurrenceSchedule
import io.github.chiragbhatn.expensetracker.domain.RecurringExpense
import io.github.chiragbhatn.expensetracker.domain.Split
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.newUuid
import io.github.chiragbhatn.expensetracker.domain.quoteCashback
import java.time.LocalDate

data class RecurringInput(
    val title: String,
    val amount: Money,
    val category: String,
    val paymentMethod: PaymentMethod,
    val cardId: Long?,
    val start: LocalDate,
    val frequency: Frequency,
    val intervalCount: Int = 1,
    val intervalUnit: IntervalUnit = IntervalUnit.MONTHS,
    val endDate: LocalDate? = null,
    val active: Boolean = true,
    val reminderDaysBefore: Int? = null,
    val note: String = "",
) {
    val schedule: RecurrenceSchedule get() = RecurrenceSchedule(start, frequency, intervalCount.coerceAtLeast(1), intervalUnit)
}

class RecurringRepository(
    private val database: AppDatabase,
    private val clock: AppClock,
    private val expenses: ExpenseRepository,
) {
    private val recurring = database.recurringDao()

    /**
     * Saves a recurring expense. Its next occurrence is the first one from
     * today on: past dates are not filled in. Editing keeps the next date
     * unless the schedule itself changed.
     */
    suspend fun save(id: Long?, input: RecurringInput): Long {
        val title = cleanName(input.title)
        require(title.isNotEmpty()) { "A name is required" }
        require(input.amount.isPositive) { "The amount must be more than zero" }
        require(input.endDate == null || !input.endDate.isBefore(input.start)) { "The end date is before the start date" }
        val now = clock.now()
        return database.withTransaction {
            val current = id?.let { recurring.get(it) }
            val category = CategoryRepository(database, clock).ensure(input.category, CategoryKind.EXPENSE)
            val sameSchedule = current != null && current.toDomain().schedule == input.schedule
            val next = if (sameSchedule) {
                RecurringExpense.Occurrence(current!!.occurrenceIndex, LocalDate.ofEpochDay(current.nextEpochDay))
            } else {
                RecurringExpense.firstPending(input.schedule, clock.today())
            }
            val entity = RecurringExpenseEntity(
                id = id ?: 0,
                title = title,
                amountPaise = input.amount.paise,
                category = category,
                paymentMethod = input.paymentMethod,
                cardId = input.cardId,
                startEpochDay = input.start.toEpochDay(),
                frequency = input.frequency,
                intervalCount = input.intervalCount.coerceAtLeast(1),
                intervalUnit = input.intervalUnit,
                nextEpochDay = next.date.toEpochDay(),
                occurrenceIndex = next.index,
                endEpochDay = input.endDate?.toEpochDay(),
                active = input.active,
                reminderDaysBefore = input.reminderDaysBefore,
                note = input.note.trim(),
                uuid = current?.uuid ?: newUuid(),
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
            )
            if (current == null) recurring.insert(entity) else entity.id.also { recurring.update(entity) }
        }
    }

    /** Pauses or resumes. Resuming continues from today, without filling in what was skipped. */
    suspend fun setActive(id: Long, active: Boolean) = database.withTransaction {
        val current = recurring.get(id) ?: return@withTransaction
        val next = if (active && !current.active) RecurringExpense.firstPending(current.toDomain().schedule, clock.today()) else null
        recurring.update(
            current.copy(
                active = active,
                nextEpochDay = next?.date?.toEpochDay() ?: current.nextEpochDay,
                occurrenceIndex = next?.index ?: current.occurrenceIndex,
                updatedAt = clock.now(),
            ),
        )
    }

    /** Deletes the schedule. Expenses it already recorded stay. */
    suspend fun delete(id: Long) = database.withTransaction {
        database.expenseDao().clearRecurring(id)
        recurring.delete(id)
    }

    /**
     * Records every occurrence that is due by today as an expense, applying
     * the current cashback rules, and moves each schedule to its next date.
     * Safe to run repeatedly: an occurrence is never recorded twice.
     * Returns the ids of the new expenses.
     */
    suspend fun recordDue(): List<Long> {
        val today = clock.today()
        val rules = database.cashbackRuleDao().getAll().map { it.toDomain() }
        val created = mutableListOf<Long>()
        recurring.getAll().map { it.toDomain() }.filter { it.active }.forEach { item ->
            val due = item.dueOccurrences(today)
            if (due.isEmpty()) return@forEach
            database.withTransaction {
                due.forEach { occurrence ->
                    if (database.expenseDao().countForOccurrence(item.id, occurrence.date.toEpochDay()) > 0) return@forEach
                    val quote = quoteCashback(item.title, item.paymentMethod, rules, saved = null, cardId = item.cardId)
                    val effective = CashbackBreakdown.calculate(item.amount, quote.percentage).effectiveAmount
                    created += expenses.save(
                        id = null,
                        input = ExpenseInput(
                            originalAmount = item.amount,
                            cashbackPercentage = quote.percentage,
                            merchant = item.title,
                            paymentMethod = item.paymentMethod,
                            split = Split.mine(effective),
                            date = occurrence.date,
                            note = item.note,
                            category = item.category,
                            cardId = item.cardId,
                        ),
                        recurringId = item.id,
                    )
                }
                val nextIndex = due.last().index + 1
                val current = recurring.get(item.id) ?: return@withTransaction
                recurring.update(
                    current.copy(
                        occurrenceIndex = nextIndex,
                        nextEpochDay = item.schedule.occurrence(nextIndex).toEpochDay(),
                        updatedAt = clock.now(),
                    ),
                )
            }
        }
        return created
    }
}
