package io.github.chiragbhatn.expensetracker.data

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
import io.github.chiragbhatn.expensetracker.domain.CardPayment
import io.github.chiragbhatn.expensetracker.domain.CashbackBreakdown
import io.github.chiragbhatn.expensetracker.domain.CashbackRule
import io.github.chiragbhatn.expensetracker.domain.Category
import io.github.chiragbhatn.expensetracker.domain.CreditCard
import io.github.chiragbhatn.expensetracker.domain.Expense
import io.github.chiragbhatn.expensetracker.domain.Income
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.PersonShare
import io.github.chiragbhatn.expensetracker.domain.RecurrenceSchedule
import io.github.chiragbhatn.expensetracker.domain.RecurringExpense
import io.github.chiragbhatn.expensetracker.domain.Reminder
import io.github.chiragbhatn.expensetracker.domain.UdhaarEntry
import java.time.LocalDate

/** A file attached to an expense or person. */
data class Attachment(
    val id: Long,
    val expenseId: Long?,
    val personId: Long?,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val uuid: String,
)

internal fun CashbackRuleEntity.toDomain() = CashbackRule(
    id = id,
    merchant = merchant,
    percentage = Percentage(cashbackPercentageBps),
    enabled = enabled,
    cardId = cardId,
    uuid = uuid,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

internal fun PersonEntity.toDomain() = Person(
    id = id,
    name = name,
    phone = phone,
    email = email,
    address = address,
    notes = notes,
    tags = splitTags(tags),
    photoPath = photoPath,
    uuid = uuid,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

internal fun splitTags(tags: String): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

internal fun joinTags(tags: List<String>): String = tags.map { it.replace(",", " ").trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")

internal fun ExpenseEntity.toDomain(shares: List<PersonShare>) = Expense(
    id = id,
    merchant = merchant,
    paymentMethod = paymentMethod,
    amounts = CashbackBreakdown(
        originalAmount = Money(originalAmountPaise),
        cashbackPercentage = Percentage(cashbackPercentageBps),
        cashbackAmount = Money(cashbackAmountPaise),
        effectiveAmount = Money(effectiveAmountPaise),
    ),
    shares = shares,
    date = LocalDate.ofEpochDay(dateEpochDay),
    note = note,
    category = category,
    cardId = cardId,
    recurringId = recurringId,
    uuid = uuid,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

internal fun UdhaarEntryEntity.toDomain(expenseMerchant: String?) = UdhaarEntry(
    id = id,
    personId = personId,
    direction = direction,
    amount = Money(amountPaise),
    date = LocalDate.ofEpochDay(dateEpochDay),
    note = note,
    expenseId = expenseId,
    expenseMerchant = expenseMerchant,
    type = type,
    uuid = uuid,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

internal fun IncomeEntity.toDomain() = Income(
    id = id,
    amount = Money(amountPaise),
    source = source,
    category = category,
    date = LocalDate.ofEpochDay(dateEpochDay),
    note = note,
    uuid = uuid,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

internal fun CreditCardEntity.toDomain() = CreditCard(
    id = id,
    name = name,
    bank = bank,
    lastFour = lastFour,
    creditLimit = Money(creditLimitPaise),
    statementDay = statementDay,
    dueDay = dueDay,
    notes = notes,
    active = active,
    reminderDaysBefore = reminderDaysBefore,
    uuid = uuid,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

internal fun CardPaymentEntity.toDomain() = CardPayment(
    id = id,
    cardId = cardId,
    amount = Money(amountPaise),
    date = LocalDate.ofEpochDay(dateEpochDay),
    note = note,
    uuid = uuid,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

internal fun RecurringExpenseEntity.toDomain() = RecurringExpense(
    id = id,
    title = title,
    amount = Money(amountPaise),
    category = category,
    paymentMethod = paymentMethod,
    cardId = cardId,
    schedule = RecurrenceSchedule(LocalDate.ofEpochDay(startEpochDay), frequency, intervalCount, intervalUnit),
    nextDate = LocalDate.ofEpochDay(nextEpochDay),
    occurrenceIndex = occurrenceIndex,
    endDate = endEpochDay?.let(LocalDate::ofEpochDay),
    active = active,
    note = note,
    reminderDaysBefore = reminderDaysBefore,
    uuid = uuid,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

internal fun CategoryEntity.toDomain() = Category(id = id, uuid = uuid, name = name, kind = kind, isDefault = isDefault)

internal fun ReminderEntity.toDomain() = Reminder(
    id = id,
    title = title,
    note = note,
    dueDate = LocalDate.ofEpochDay(dueEpochDay),
    personId = personId,
    done = done,
    uuid = uuid,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

internal fun AttachmentEntity.toDomain() = Attachment(id, expenseId, personId, fileName, mimeType, sizeBytes, uuid)
