package io.github.chiragbhatn.expensetracker.backup

import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.Frequency
import io.github.chiragbhatn.expensetracker.domain.IntervalUnit
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import java.time.LocalDate

/*
 * The complete contents of the app, as exchanged with backups, CSV imports
 * and restores. Records refer to each other by their stable `uuid`, never by
 * database row ids, so they mean the same thing on every device.
 *
 * `id` is the local database row id. It is 0 for records that came from a
 * file; records read from this device's database keep theirs, so rewriting
 * the database with merged data does not renumber existing rows.
 */

data class PersonRecord(
    val uuid: String,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    /** File name of the profile photo in app storage; photos themselves are not in backups. */
    val photoFile: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

data class CategoryRecord(
    val uuid: String,
    val name: String,
    val kind: CategoryKind,
    val isDefault: Boolean,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

data class CardRecord(
    val uuid: String,
    val name: String,
    val bank: String,
    val lastFour: String,
    val creditLimit: Money,
    val statementDay: Int,
    val dueDay: Int,
    val notes: String,
    val active: Boolean,
    val reminderDaysBefore: Int?,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

data class CashbackRuleRecord(
    val uuid: String,
    val merchant: String,
    val percentage: Percentage,
    val enabled: Boolean,
    val cardUuid: String?,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

data class ExpenseRecord(
    val uuid: String,
    val date: LocalDate,
    val merchant: String,
    val category: String,
    val paymentMethod: PaymentMethod,
    val cardUuid: String?,
    val originalAmount: Money,
    val cashbackPercentage: Percentage,
    val cashbackAmount: Money,
    val effectiveAmount: Money,
    val note: String,
    val recurringUuid: String?,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

data class IncomeRecord(
    val uuid: String,
    val date: LocalDate,
    val source: String,
    val category: String,
    val amount: Money,
    val note: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

/** One ledger entry: an expense share, udhaar, payment, settlement or adjustment. */
data class LedgerRecord(
    val uuid: String,
    val personUuid: String,
    val date: LocalDate,
    val type: LedgerType,
    val direction: UdhaarDirection,
    val amount: Money,
    /** The expense an [LedgerType.EXPENSE_SHARE] belongs to. */
    val expenseUuid: String?,
    val note: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

data class CardPaymentRecord(
    val uuid: String,
    val cardUuid: String,
    val date: LocalDate,
    val amount: Money,
    val note: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

data class RecurringRecord(
    val uuid: String,
    val title: String,
    val amount: Money,
    val category: String,
    val paymentMethod: PaymentMethod,
    val cardUuid: String?,
    val startDate: LocalDate,
    val frequency: Frequency,
    val intervalCount: Int,
    val intervalUnit: IntervalUnit,
    val nextDate: LocalDate,
    val occurrenceIndex: Int,
    val endDate: LocalDate?,
    val active: Boolean,
    val reminderDaysBefore: Int?,
    val note: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

data class ReminderRecord(
    val uuid: String,
    val title: String,
    val note: String,
    val dueDate: LocalDate,
    val personUuid: String?,
    val done: Boolean,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

/** A file kept with an expense, such as a receipt photo. Only its details are backed up. */
data class AttachmentRecord(
    val uuid: String,
    val expenseUuid: String?,
    val personUuid: String?,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val id: Long = 0,
)

data class BackupData(
    val settings: Map<String, String> = emptyMap(),
    val people: List<PersonRecord> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val cards: List<CardRecord> = emptyList(),
    val cashbackRules: List<CashbackRuleRecord> = emptyList(),
    val expenses: List<ExpenseRecord> = emptyList(),
    val incomes: List<IncomeRecord> = emptyList(),
    val ledger: List<LedgerRecord> = emptyList(),
    val cardPayments: List<CardPaymentRecord> = emptyList(),
    val recurring: List<RecurringRecord> = emptyList(),
    val reminders: List<ReminderRecord> = emptyList(),
    val attachments: List<AttachmentRecord> = emptyList(),
) {
    /** Whether anything was recorded, beyond the rules and categories a new install starts with. */
    val hasUserData: Boolean
        get() = people.isNotEmpty() || expenses.isNotEmpty() || incomes.isNotEmpty() || ledger.isNotEmpty() ||
            cards.isNotEmpty() || cardPayments.isNotEmpty() || recurring.isNotEmpty() || reminders.isNotEmpty()

    val payments: List<LedgerRecord> get() = ledger.filter { it.type in PAYMENT_TYPES }
    val settlements: List<LedgerRecord> get() = ledger.filter { it.type == LedgerType.SETTLEMENT }
    val otherLedger: List<LedgerRecord> get() = ledger.filter { it.type !in PAYMENT_TYPES && it.type != LedgerType.SETTLEMENT }

    companion object {
        val PAYMENT_TYPES = setOf(LedgerType.PAYMENT_RECEIVED, LedgerType.PAYMENT_MADE)
    }
}
