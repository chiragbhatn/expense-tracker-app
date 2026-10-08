package io.github.chiragbhatn.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Rows are only ever written with @Insert/@Update, never REPLACE: a REPLACE
// deletes the old row first, which would cascade-delete rows that refer to it.
//
// Each table can be read whole (the app works on all of its data in memory,
// which keeps every total consistent) and emptied and refilled for restores.

@Dao
interface CashbackRuleDao {
    @Query("SELECT * FROM cashback_rules ORDER BY merchant COLLATE NOCASE, card_id")
    fun observeAll(): Flow<List<CashbackRuleEntity>>

    @Query("SELECT * FROM cashback_rules ORDER BY id")
    suspend fun getAll(): List<CashbackRuleEntity>

    /** The rule for a merchant on one card, or the any-card rule when [cardId] is null. */
    @Query("SELECT * FROM cashback_rules WHERE merchant_key = :merchantKey AND card_id IS :cardId")
    suspend fun find(merchantKey: String, cardId: Long?): CashbackRuleEntity?

    @Query("SELECT * FROM cashback_rules WHERE merchant_key = :merchantKey ORDER BY card_id IS NOT NULL, id LIMIT 1")
    suspend fun findAnyForMerchant(merchantKey: String): CashbackRuleEntity?

    @Insert
    suspend fun insert(rule: CashbackRuleEntity): Long

    @Insert
    suspend fun insertAll(rules: List<CashbackRuleEntity>)

    @Update
    suspend fun update(rule: CashbackRuleEntity)

    @Query("UPDATE cashback_rules SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM cashback_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM cashback_rules WHERE card_id = :cardId")
    suspend fun deleteForCard(cardId: Long)

    @Query("DELETE FROM cashback_rules")
    suspend fun deleteAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date_epoch_day DESC, created_at DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY id")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): ExpenseEntity?

    /** Distinct merchants, most recently used first. */
    @Query(
        """
        SELECT merchant FROM expenses
        GROUP BY LOWER(merchant)
        ORDER BY MAX(date_epoch_day) DESC, MAX(id) DESC
        LIMIT 30
        """,
    )
    fun observeRecentMerchants(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM expenses WHERE recurring_id = :recurringId AND date_epoch_day = :epochDay")
    suspend fun countForOccurrence(recurringId: Long, epochDay: Long): Int

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Insert
    suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long>

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE expenses SET card_id = NULL WHERE card_id = :cardId")
    suspend fun clearCard(cardId: Long)

    @Query("UPDATE expenses SET recurring_id = NULL WHERE recurring_id = :recurringId")
    suspend fun clearRecurring(recurringId: Long)

    @Query("UPDATE expenses SET category = :to, updated_at = :updatedAt WHERE category = :from COLLATE NOCASE")
    suspend fun renameCategory(from: String, to: String, updatedAt: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people ORDER BY id")
    suspend fun getAll(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun get(id: Long): PersonEntity?

    @Query("SELECT * FROM people WHERE name_key = :nameKey")
    suspend fun findByKey(nameKey: String): PersonEntity?

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Insert
    suspend fun insertAll(people: List<PersonEntity>): List<Long>

    @Update
    suspend fun update(person: PersonEntity)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM people")
    suspend fun deleteAll()
}

@Dao
interface UdhaarDao {
    @Query("SELECT * FROM udhaar_entries ORDER BY date_epoch_day DESC, created_at DESC, id DESC")
    fun observeAll(): Flow<List<UdhaarEntryEntity>>

    @Query("SELECT * FROM udhaar_entries ORDER BY id")
    suspend fun getAll(): List<UdhaarEntryEntity>

    @Query("SELECT * FROM udhaar_entries WHERE id = :id")
    suspend fun get(id: Long): UdhaarEntryEntity?

    @Query("SELECT * FROM udhaar_entries WHERE expense_id = :expenseId")
    suspend fun sharesForExpense(expenseId: Long): List<UdhaarEntryEntity>

    @Query("SELECT * FROM udhaar_entries WHERE person_id = :personId")
    suspend fun forPerson(personId: Long): List<UdhaarEntryEntity>

    @Insert
    suspend fun insert(entry: UdhaarEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<UdhaarEntryEntity>)

    @Update
    suspend fun update(entry: UdhaarEntryEntity)

    @Query("DELETE FROM udhaar_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM udhaar_entries")
    suspend fun deleteAll()
}

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes ORDER BY date_epoch_day DESC, created_at DESC, id DESC")
    fun observeAll(): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes ORDER BY id")
    suspend fun getAll(): List<IncomeEntity>

    @Query("SELECT * FROM incomes WHERE id = :id")
    suspend fun get(id: Long): IncomeEntity?

    @Insert
    suspend fun insert(income: IncomeEntity): Long

    @Insert
    suspend fun insertAll(incomes: List<IncomeEntity>)

    @Update
    suspend fun update(income: IncomeEntity)

    @Query("UPDATE incomes SET category = :to, updated_at = :updatedAt WHERE category = :from COLLATE NOCASE")
    suspend fun renameCategory(from: String, to: String, updatedAt: Long)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM incomes")
    suspend fun deleteAll()
}

@Dao
interface CardDao {
    @Query("SELECT * FROM credit_cards ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CreditCardEntity>>

    @Query("SELECT * FROM credit_cards ORDER BY id")
    suspend fun getAll(): List<CreditCardEntity>

    @Query("SELECT * FROM credit_cards WHERE id = :id")
    suspend fun get(id: Long): CreditCardEntity?

    @Insert
    suspend fun insert(card: CreditCardEntity): Long

    @Insert
    suspend fun insertAll(cards: List<CreditCardEntity>): List<Long>

    @Update
    suspend fun update(card: CreditCardEntity)

    @Query("DELETE FROM credit_cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM credit_cards")
    suspend fun deleteAll()

    @Query("SELECT * FROM card_payments ORDER BY date_epoch_day DESC, id DESC")
    fun observePayments(): Flow<List<CardPaymentEntity>>

    @Query("SELECT * FROM card_payments ORDER BY id")
    suspend fun getAllPayments(): List<CardPaymentEntity>

    @Insert
    suspend fun insertPayment(payment: CardPaymentEntity): Long

    @Insert
    suspend fun insertPayments(payments: List<CardPaymentEntity>)

    @Query("DELETE FROM card_payments WHERE id = :id")
    suspend fun deletePayment(id: Long)

    @Query("DELETE FROM card_payments")
    suspend fun deleteAllPayments()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_expenses ORDER BY active DESC, next_epoch_day, id")
    fun observeAll(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses ORDER BY id")
    suspend fun getAll(): List<RecurringExpenseEntity>

    @Query("SELECT * FROM recurring_expenses WHERE id = :id")
    suspend fun get(id: Long): RecurringExpenseEntity?

    @Insert
    suspend fun insert(item: RecurringExpenseEntity): Long

    @Insert
    suspend fun insertAll(items: List<RecurringExpenseEntity>): List<Long>

    @Update
    suspend fun update(item: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE recurring_expenses SET category = :to, updated_at = :updatedAt WHERE category = :from COLLATE NOCASE")
    suspend fun renameCategory(from: String, to: String, updatedAt: Long)

    @Query("DELETE FROM recurring_expenses")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, is_default DESC, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE name_key = :nameKey AND kind = :kind")
    suspend fun find(nameKey: String, kind: String): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY done, due_epoch_day, id")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY id")
    suspend fun getAll(): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Insert
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments ORDER BY id")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments ORDER BY id")
    suspend fun getAll(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE expense_id = :expenseId")
    suspend fun forExpense(expenseId: Long): List<AttachmentEntity>

    @Insert
    suspend fun insert(attachment: AttachmentEntity): Long

    @Insert
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()
}
