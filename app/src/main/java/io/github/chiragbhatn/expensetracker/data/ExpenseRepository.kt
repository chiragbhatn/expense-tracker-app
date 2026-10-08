package io.github.chiragbhatn.expensetracker.data

import androidx.room.withTransaction
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.AttachmentEntity
import io.github.chiragbhatn.expensetracker.data.db.ExpenseEntity
import io.github.chiragbhatn.expensetracker.data.db.IncomeEntity
import io.github.chiragbhatn.expensetracker.data.db.UdhaarEntryEntity
import io.github.chiragbhatn.expensetracker.domain.ExpenseInput
import io.github.chiragbhatn.expensetracker.domain.IncomeInput
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.LegacyShareFix
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.SplitProblem
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.domain.newUuid
import io.github.chiragbhatn.expensetracker.domain.toLedgerPosting
import kotlinx.coroutines.flow.Flow
import java.io.File

class InvalidSplitException(val problems: List<SplitProblem>) : IllegalArgumentException("The split does not add up: $problems")

/** A receipt photo to keep with an expense, already copied into app storage. */
data class NewAttachment(val file: File, val mimeType: String)

class ExpenseRepository(
    private val database: AppDatabase,
    private val clock: AppClock,
    private val files: AppFiles,
) {
    private val expenses = database.expenseDao()
    private val shares = database.udhaarDao()
    private val rules = database.cashbackRuleDao()
    private val incomes = database.incomeDao()
    private val attachments = database.attachmentDao()
    private val categories = CategoryRepository(database, clock)

    fun observeRecentMerchants(): Flow<List<String>> = expenses.observeRecentMerchants()

    /**
     * Saves the expense and each person's share of it together. Shares must
     * add up to the effective amount (original − cashback), so cashback
     * lowers what everyone owes; otherwise nothing is saved. Returns the id.
     */
    suspend fun save(id: Long?, input: ExpenseInput, receipt: NewAttachment? = null, recurringId: Long? = null): Long {
        val posting = input.toLedgerPosting()
        if (posting.problems.isNotEmpty()) throw InvalidSplitException(posting.problems)
        val now = clock.now()
        return database.withTransaction {
            val current = id?.let { expenses.get(it) }
            require(id == null || current != null) { "Expense $id no longer exists" }
            val category = categories.ensure(input.category, CategoryKind.EXPENSE)
            val entity = ExpenseEntity(
                id = id ?: 0,
                originalAmountPaise = posting.expense.originalAmount.paise,
                cashbackPercentageBps = posting.expense.cashbackPercentage.basisPoints,
                cashbackAmountPaise = posting.expense.cashbackAmount.paise,
                effectiveAmountPaise = posting.expense.effectiveAmount.paise,
                merchant = canonicalMerchant(input.merchant),
                paymentMethod = input.paymentMethod,
                dateEpochDay = input.date.toEpochDay(),
                note = input.note.trim(),
                category = category,
                cardId = input.cardId,
                recurringId = current?.recurringId ?: recurringId,
                uuid = current?.uuid ?: newUuid(),
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
            )
            val expenseId = if (current == null) expenses.insert(entity) else entity.id.also { expenses.update(entity) }

            // Keep each person's existing share row (and its id) when they stay in the split.
            val existing = shares.sharesForExpense(expenseId).associateBy { it.personId }
            val wanted = posting.shares.associateBy { it.personId }
            existing.values.filter { it.personId !in wanted }.forEach { shares.delete(it.id) }
            wanted.values.forEach { share ->
                val old = existing[share.personId]
                if (old == null) {
                    shares.insert(
                        UdhaarEntryEntity(
                            personId = share.personId,
                            direction = UdhaarDirection.GAVE,
                            amountPaise = share.amount.paise,
                            dateEpochDay = entity.dateEpochDay,
                            note = "",
                            expenseId = expenseId,
                            type = LedgerType.EXPENSE_SHARE,
                            uuid = newUuid(),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                } else if (old.amountPaise != share.amount.paise || old.dateEpochDay != entity.dateEpochDay) {
                    shares.update(old.copy(amountPaise = share.amount.paise, dateEpochDay = entity.dateEpochDay, updatedAt = now))
                }
            }
            if (receipt != null) {
                attachments.insert(
                    AttachmentEntity(
                        expenseId = expenseId,
                        personId = null,
                        fileName = receipt.file.name,
                        mimeType = receipt.mimeType,
                        sizeBytes = receipt.file.length(),
                        uuid = receipt.file.nameWithoutExtension,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            expenseId
        }
    }

    /** Deletes the expense, everyone's share of it and its receipts. */
    suspend fun delete(id: Long) {
        val receipts = attachments.forExpense(id)
        database.withTransaction {
            shares.sharesForExpense(id).forEach { shares.delete(it.id) }
            expenses.delete(id)
        }
        receipts.forEach { files.attachment(it.fileName).delete() }
    }

    /**
     * Applies reviewed corrections to version 1 expenses: each person is
     * charged the effective amount instead of the original amount.
     */
    suspend fun applyShareFixes(fixes: List<LegacyShareFix>) = database.withTransaction {
        val now = clock.now()
        fixes.forEach { fix ->
            val share = shares.sharesForExpense(fix.expense.id).singleOrNull { it.personId == fix.person.id } ?: return@forEach
            if (share.amountPaise == fix.oldShare.paise) shares.update(share.copy(amountPaise = fix.newShare.paise, updatedAt = now))
        }
    }

    suspend fun saveIncome(id: Long?, input: IncomeInput): Long {
        require(input.amount.isPositive) { "Income must be more than zero" }
        val now = clock.now()
        return database.withTransaction {
            val current = id?.let { incomes.get(it) }
            val category = categories.ensure(input.category, CategoryKind.INCOME)
            val entity = IncomeEntity(
                id = id ?: 0,
                amountPaise = input.amount.paise,
                source = cleanName(input.source),
                category = category,
                dateEpochDay = input.date.toEpochDay(),
                note = input.note.trim(),
                uuid = current?.uuid ?: newUuid(),
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
            )
            if (current == null) incomes.insert(entity) else entity.id.also { incomes.update(entity) }
        }
    }

    suspend fun deleteIncome(id: Long) = incomes.delete(id)

    // Uses the rule's spelling when the merchant has one: "swiggy" is saved as "Swiggy".
    private suspend fun canonicalMerchant(merchant: String): String {
        val name = cleanName(merchant)
        return rules.findAnyForMerchant(nameKey(name))?.merchant ?: name
    }
}
