package io.github.chiragbhatn.expensetracker.data

import io.github.chiragbhatn.expensetracker.data.db.CashbackRuleEntity
import io.github.chiragbhatn.expensetracker.data.db.ExpenseRow
import io.github.chiragbhatn.expensetracker.data.db.PersonEntity
import io.github.chiragbhatn.expensetracker.data.db.UdhaarEntryRow
import io.github.chiragbhatn.expensetracker.domain.CashbackBreakdown
import io.github.chiragbhatn.expensetracker.domain.CashbackRule
import io.github.chiragbhatn.expensetracker.domain.Expense
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.UdhaarEntry
import java.time.LocalDate

internal fun CashbackRuleEntity.toDomain() = CashbackRule(
    id = id,
    merchant = merchant,
    percentage = Percentage(cashbackPercentageBps),
    enabled = enabled,
)

internal fun ExpenseRow.toDomain() = Expense(
    id = expense.id,
    merchant = expense.merchant,
    paymentMethod = expense.paymentMethod,
    amounts = CashbackBreakdown(
        originalAmount = Money(expense.originalAmountPaise),
        cashbackPercentage = Percentage(expense.cashbackPercentageBps),
        cashbackAmount = Money(expense.cashbackAmountPaise),
        effectiveAmount = Money(expense.effectiveAmountPaise),
    ),
    paidFor = if (paidForId != null && paidForName != null) Person(paidForId, paidForName) else null,
    date = LocalDate.ofEpochDay(expense.dateEpochDay),
    note = expense.note,
)

internal fun PersonEntity.toDomain() = Person(id = id, name = name)

internal fun UdhaarEntryRow.toDomain() = UdhaarEntry(
    id = entry.id,
    personId = entry.personId,
    direction = entry.direction,
    amount = Money(entry.amountPaise),
    date = LocalDate.ofEpochDay(entry.dateEpochDay),
    note = entry.note,
    expenseId = entry.expenseId,
    expenseMerchant = expenseMerchant,
)
