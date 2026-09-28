package io.github.chiragbhatn.expensetracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Expenses and udhaar entries are only ever written with @Insert/@Update, never
// REPLACE: a REPLACE deletes the old row first, which would cascade-delete the
// udhaar entry linked to an expense.

@Dao
interface CashbackRuleDao {
    @Query("SELECT * FROM cashback_rules ORDER BY merchant COLLATE NOCASE")
    fun observeAll(): Flow<List<CashbackRuleEntity>>

    @Query("SELECT * FROM cashback_rules WHERE merchant_key = :merchantKey")
    suspend fun findByKey(merchantKey: String): CashbackRuleEntity?

    @Insert
    suspend fun insert(rule: CashbackRuleEntity): Long

    @Update
    suspend fun update(rule: CashbackRuleEntity)

    @Query("UPDATE cashback_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM cashback_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

/** An expense and, through its udhaar entry, the person it was paid for. */
data class ExpenseRow(
    @Embedded val expense: ExpenseEntity,
    @ColumnInfo(name = "paid_for_id") val paidForId: Long?,
    @ColumnInfo(name = "paid_for_name") val paidForName: String?,
)

@Dao
interface ExpenseDao {
    @Query(
        """
        SELECT expenses.*, people.id AS paid_for_id, people.name AS paid_for_name
        FROM expenses
        LEFT JOIN udhaar_entries ON udhaar_entries.expense_id = expenses.id
        LEFT JOIN people ON people.id = udhaar_entries.person_id
        WHERE expenses.date_epoch_day BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY expenses.date_epoch_day DESC, expenses.id DESC
        """,
    )
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<ExpenseRow>>

    @Query(
        """
        SELECT expenses.*, people.id AS paid_for_id, people.name AS paid_for_name
        FROM expenses
        LEFT JOIN udhaar_entries ON udhaar_entries.expense_id = expenses.id
        LEFT JOIN people ON people.id = udhaar_entries.person_id
        WHERE expenses.id = :id
        """,
    )
    suspend fun get(id: Long): ExpenseRow?

    /** Distinct merchants, most recently used first. */
    @Query(
        """
        SELECT merchant FROM expenses
        GROUP BY LOWER(merchant)
        ORDER BY MAX(date_epoch_day) DESC, MAX(id) DESC
        LIMIT 20
        """,
    )
    fun observeRecentMerchants(): Flow<List<String>>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :id")
    fun observe(id: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM people WHERE name_key = :nameKey")
    suspend fun findByKey(nameKey: String): PersonEntity?

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun delete(id: Long)
}

/** An udhaar entry and the merchant of the expense it came from, if any. */
data class UdhaarEntryRow(
    @Embedded val entry: UdhaarEntryEntity,
    @ColumnInfo(name = "expense_merchant") val expenseMerchant: String?,
)

@Dao
interface UdhaarDao {
    @Query(
        """
        SELECT udhaar_entries.*, expenses.merchant AS expense_merchant
        FROM udhaar_entries
        LEFT JOIN expenses ON expenses.id = udhaar_entries.expense_id
        ORDER BY udhaar_entries.date_epoch_day DESC, udhaar_entries.id DESC
        """,
    )
    fun observeAll(): Flow<List<UdhaarEntryRow>>

    @Query(
        """
        SELECT udhaar_entries.*, expenses.merchant AS expense_merchant
        FROM udhaar_entries
        LEFT JOIN expenses ON expenses.id = udhaar_entries.expense_id
        WHERE udhaar_entries.person_id = :personId
        ORDER BY udhaar_entries.date_epoch_day DESC, udhaar_entries.id DESC
        """,
    )
    fun observeForPerson(personId: Long): Flow<List<UdhaarEntryRow>>

    @Query("SELECT * FROM udhaar_entries WHERE expense_id = :expenseId")
    suspend fun findForExpense(expenseId: Long): UdhaarEntryEntity?

    @Insert
    suspend fun insert(entry: UdhaarEntryEntity): Long

    @Update
    suspend fun update(entry: UdhaarEntryEntity)

    @Query("DELETE FROM udhaar_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM udhaar_entries WHERE expense_id = :expenseId")
    suspend fun deleteForExpense(expenseId: Long)

    @Query("DELETE FROM udhaar_entries WHERE person_id = :personId")
    suspend fun deleteForPerson(personId: Long)
}
