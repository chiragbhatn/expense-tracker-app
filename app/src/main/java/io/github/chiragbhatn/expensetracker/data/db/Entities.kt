package io.github.chiragbhatn.expensetracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection

// Amounts are stored in paise and percentages in basis points (10% = 1000).

@Entity(
    tableName = "cashback_rules",
    indices = [Index(value = ["merchant_key"], unique = true)],
)
data class CashbackRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    @ColumnInfo(name = "merchant_key") val merchantKey: String,
    @ColumnInfo(name = "cashback_percentage_bps") val cashbackPercentageBps: Int,
    val enabled: Boolean,
)

/**
 * The card/expense side of a transaction. The cashback percentage is copied
 * from the rule at the time, so later rule changes never alter saved expenses.
 */
@Entity(
    tableName = "expenses",
    indices = [Index("date_epoch_day")],
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
)

@Entity(
    tableName = "people",
    indices = [Index(value = ["name_key"], unique = true)],
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
)

/**
 * The udhaar side. An entry with an [expenseId] was created by paying for the
 * person on that expense and always holds the expense's full original amount.
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
    indices = [Index("person_id"), Index(value = ["expense_id"], unique = true)],
)
data class UdhaarEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "person_id") val personId: Long,
    val direction: UdhaarDirection,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    val note: String,
    @ColumnInfo(name = "expense_id") val expenseId: Long?,
)
