package io.github.chiragbhatn.expensetracker.data

import androidx.room.withTransaction
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.ExpenseEntity
import io.github.chiragbhatn.expensetracker.data.db.UdhaarEntryEntity
import io.github.chiragbhatn.expensetracker.domain.Expense
import io.github.chiragbhatn.expensetracker.domain.ExpenseInput
import io.github.chiragbhatn.expensetracker.domain.Period
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.domain.toLedgerPosting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExpenseRepository(private val database: AppDatabase) {
    private val expenses = database.expenseDao()
    private val udhaar = database.udhaarDao()
    private val rules = database.cashbackRuleDao()

    fun observe(period: Period): Flow<List<Expense>> {
        val days = period.epochDays
        return expenses.observeBetween(days.first, days.last).map { rows -> rows.map { it.toDomain() } }
    }

    fun observeRecentMerchants(): Flow<List<String>> = expenses.observeRecentMerchants()

    suspend fun get(id: Long): Expense? = expenses.get(id)?.toDomain()

    /**
     * Saves both sides of the transaction together: the expense (original,
     * cashback, effective) and, when it was paid for someone, their udhaar
     * entry for the full original amount. Returns the expense id.
     */
    suspend fun save(id: Long?, input: ExpenseInput): Long = database.withTransaction {
        val posting = input.toLedgerPosting()
        val expense = ExpenseEntity(
            id = id ?: 0,
            originalAmountPaise = posting.expense.originalAmount.paise,
            cashbackPercentageBps = posting.expense.cashbackPercentage.basisPoints,
            cashbackAmountPaise = posting.expense.cashbackAmount.paise,
            effectiveAmountPaise = posting.expense.effectiveAmount.paise,
            merchant = canonicalMerchant(input.merchant),
            paymentMethod = input.paymentMethod,
            dateEpochDay = input.date.toEpochDay(),
            note = input.note.trim(),
        )
        val expenseId = if (id == null) expenses.insert(expense) else id.also { expenses.update(expense) }

        val existing = udhaar.findForExpense(expenseId)
        val personId = input.paidForPersonId
        val owed = posting.udhaarOwed
        if (personId != null && owed != null) {
            val entry = UdhaarEntryEntity(
                id = existing?.id ?: 0,
                personId = personId,
                direction = UdhaarDirection.GAVE,
                amountPaise = owed.paise,
                dateEpochDay = expense.dateEpochDay,
                note = expense.note,
                expenseId = expenseId,
            )
            if (existing == null) udhaar.insert(entry) else udhaar.update(entry)
        } else if (existing != null) {
            udhaar.delete(existing.id)
        }
        expenseId
    }

    /** Deletes the expense together with the udhaar entry it created, if any. */
    suspend fun delete(id: Long) = database.withTransaction {
        udhaar.deleteForExpense(id)
        expenses.delete(id)
    }

    // Uses the rule's spelling when the merchant has one: "swiggy" is saved as "Swiggy".
    private suspend fun canonicalMerchant(merchant: String): String {
        val name = cleanName(merchant)
        return rules.findByKey(nameKey(name))?.merchant ?: name
    }
}
