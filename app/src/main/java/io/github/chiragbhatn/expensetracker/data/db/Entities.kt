package io.github.chiragbhatn.expensetracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.Frequency
import io.github.chiragbhatn.expensetracker.domain.IntervalUnit
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection

// Amounts are stored in paise and percentages in basis points (10% = 1000).
// Every row has a `uuid`: a permanent ID that backups and imports use to
// recognise the same record on another device. Timestamps are epoch millis.
//
// Columns added to version 1 tables declare the same defaults the migration
// gives them, so new and upgraded databases have identical schemas.

@Entity(
    tableName = "cashback_rules",
    indices = [Index("merchant_key"), Index(value = ["uuid"], unique = true)],
)
data class CashbackRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    @ColumnInfo(name = "merchant_key") val merchantKey: String,
    @ColumnInfo(name = "cashback_percentage_bps") val cashbackPercentageBps: Int,
    val enabled: Boolean,
    /** Limits the rule to one card; null applies to any card. */
    @ColumnInfo(name = "card_id") val cardId: Long? = null,
    @ColumnInfo(defaultValue = "''") val uuid: String,
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long,
)

/**
 * The card/expense side of a transaction. The cashback percentage is copied
 * from the rule at the time, so later rule changes never alter saved expenses.
 */
@Entity(
    tableName = "expenses",
    indices = [Index("date_epoch_day"), Index("card_id"), Index(value = ["uuid"], unique = true)],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "original_amount_paise") val originalAmountPaise: Long,
    @ColumnInfo(name = "cashback_percentage_bps") val cashbackPercentageBps: Int,
    @ColumnInfo(name = "cashback_amount_paise") val cashbackAmountPaise: Long,
    @ColumnInfo(name = "effective_amount_paise") val effectiveAmountPaise: Long,
    val merchant: String,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    val note: String,
    @ColumnInfo(defaultValue = "'Other'") val category: String,
    @ColumnInfo(name = "card_id") val cardId: Long? = null,
    /** The recurring expense that recorded this expense. */
    @ColumnInfo(name = "recurring_id") val recurringId: Long? = null,
    @ColumnInfo(defaultValue = "''") val uuid: String,
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long,
)

@Entity(
    tableName = "people",
    indices = [Index(value = ["name_key"], unique = true), Index(value = ["uuid"], unique = true)],
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(defaultValue = "''") val phone: String = "",
    @ColumnInfo(defaultValue = "''") val email: String = "",
    @ColumnInfo(defaultValue = "''") val address: String = "",
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    /** Comma-separated. */
    @ColumnInfo(defaultValue = "''") val tags: String = "",
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(defaultValue = "''") val uuid: String,
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long,
)

/**
 * A ledger entry with a person. Entries with an [expenseId] are that person's
 * share of the expense; a person has at most one share per expense.
 */
@Entity(
    tableName = "udhaar_entries",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expense_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("person_id"),
        Index("expense_id"),
        Index(value = ["expense_id", "person_id"], unique = true),
        Index(value = ["uuid"], unique = true),
    ],
)
data class UdhaarEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "person_id") val personId: Long,
    val direction: UdhaarDirection,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    val note: String,
    @ColumnInfo(name = "expense_id") val expenseId: Long?,
    @ColumnInfo(defaultValue = "'UDHAAR_GIVEN'") val type: LedgerType,
    @ColumnInfo(defaultValue = "''") val uuid: String,
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long,
)

@Entity(
    tableName = "incomes",
    indices = [Index("date_epoch_day"), Index(value = ["uuid"], unique = true)],
)
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val source: String,
    val category: String,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    val note: String,
    val uuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "credit_cards",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class CreditCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bank: String,
    @ColumnInfo(name = "last_four") val lastFour: String,
    @ColumnInfo(name = "credit_limit_paise") val creditLimitPaise: Long,
    @ColumnInfo(name = "statement_day") val statementDay: Int,
    @ColumnInfo(name = "due_day") val dueDay: Int,
    val notes: String,
    val active: Boolean,
    @ColumnInfo(name = "reminder_days_before") val reminderDaysBefore: Int?,
    val uuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "card_payments",
    foreignKeys = [
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("card_id"), Index(value = ["uuid"], unique = true)],
)
data class CardPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: Long,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    val note: String,
    val uuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "recurring_expenses",
    foreignKeys = [
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("card_id"), Index(value = ["uuid"], unique = true)],
)
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val category: String,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "card_id") val cardId: Long?,
    @ColumnInfo(name = "start_epoch_day") val startEpochDay: Long,
    val frequency: Frequency,
    @ColumnInfo(name = "interval_count") val intervalCount: Int,
    @ColumnInfo(name = "interval_unit") val intervalUnit: IntervalUnit,
    @ColumnInfo(name = "next_epoch_day") val nextEpochDay: Long,
    @ColumnInfo(name = "occurrence_index") val occurrenceIndex: Int,
    @ColumnInfo(name = "end_epoch_day") val endEpochDay: Long?,
    val active: Boolean,
    @ColumnInfo(name = "reminder_days_before") val reminderDaysBefore: Int?,
    val note: String,
    val uuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name_key", "kind"], unique = true), Index(value = ["uuid"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    val kind: CategoryKind,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    val uuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("person_id"), Index(value = ["uuid"], unique = true)],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String,
    @ColumnInfo(name = "due_epoch_day") val dueEpochDay: Long,
    @ColumnInfo(name = "person_id") val personId: Long?,
    val done: Boolean,
    val uuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A file kept with an expense, such as a scanned receipt, stored in the app's private files. */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expense_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expense_id"), Index("person_id"), Index(value = ["uuid"], unique = true)],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "expense_id") val expenseId: Long?,
    @ColumnInfo(name = "person_id") val personId: Long?,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val uuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
