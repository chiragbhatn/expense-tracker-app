package io.github.chiragbhatn.expensetracker.backup

import io.github.chiragbhatn.expensetracker.backup.BackupFixtures.T0
import io.github.chiragbhatn.expensetracker.backup.BackupFixtures.date
import io.github.chiragbhatn.expensetracker.domain.AppSettings
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.ThemeMode
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergerTest {

    /** This device's data, with database row ids. */
    private val existing = BackupFixtures.data.let { data ->
        data.copy(
            people = data.people.mapIndexed { i, p -> p.copy(id = i + 1L) },
            expenses = data.expenses.mapIndexed { i, e -> e.copy(id = i + 1L) },
            ledger = data.ledger.mapIndexed { i, l -> l.copy(id = i + 1L) },
            cards = data.cards.mapIndexed { i, c -> c.copy(id = i + 1L) },
        )
    }

    private val vikas = PersonRecord("p-vikas", "Vikas", createdAt = T0, updatedAt = T0)

    /** A backup made on another phone that shares some history with this one. */
    private val incoming = BackupData(
        settings = AppSettings(themeMode = ThemeMode.LIGHT).toMap(),
        people = listOf(
            BackupFixtures.rahul.copy(phone = "91234 56789", updatedAt = T0 + 100),
            PersonRecord("p-amit-other-phone", "amit", createdAt = T0, updatedAt = T0 - 1),
            vikas,
        ),
        categories = listOf(CategoryRecord("cat-pets-2", "pets", CategoryKind.EXPENSE, false, T0, T0)),
        cards = listOf(BackupFixtures.hdfc.copy(uuid = "c-hdfc-other-phone", name = "HDFC Millennia")),
        cashbackRules = listOf(CashbackRuleRecord("rule-swiggy-2", "swiggy", Percentage(1_200), true, null, T0, T0 + 50)),
        expenses = listOf(
            BackupFixtures.swiggySplit.copy(updatedAt = T0 + 100, note = "Edited on the other phone"),
            BackupFixtures.swiggyForRahul.copy(merchant = "Old name", updatedAt = T0),
            ExpenseRecord(
                "e-6", date(10, 9), "Zomato", "Food", PaymentMethod.CARD, "c-hdfc-other-phone", Money.rupees(400), Percentage.ZERO,
                Money.ZERO, Money.rupees(400), "", null, T0, T0,
            ),
        ),
        ledger = listOf(
            LedgerRecord("l-2", "p-rahul", date(10, 5), LedgerType.EXPENSE_SHARE, UdhaarDirection.GAVE, Money.rupees(450), "e-2", "", T0, T0 + 100),
            LedgerRecord("l-10", "p-vikas", date(10, 5), LedgerType.EXPENSE_SHARE, UdhaarDirection.GAVE, Money.rupees(450), "e-2", "", T0, T0 + 100),
            LedgerRecord("l-1", "p-rahul", date(10, 3), LedgerType.EXPENSE_SHARE, UdhaarDirection.GAVE, Money.rupees(200), "e-1", "", T0, T0 + 100),
            LedgerRecord("l-12", "p-amit-other-phone", date(10, 9), LedgerType.EXPENSE_SHARE, UdhaarDirection.GAVE, Money.rupees(200), "e-6", "", T0, T0),
            BackupFixtures.data.ledger.single { it.uuid == "l-5" },
            LedgerRecord("l-11", "p-vikas", date(10, 9), LedgerType.PAYMENT_RECEIVED, UdhaarDirection.GOT, Money.rupees(100), null, "", T0, T0),
        ),
        cardPayments = listOf(CardPaymentRecord("cp-2", "c-hdfc-other-phone", date(10, 1), Money.rupees(1_000), "", T0, T0)),
    )

    private val result = BackupMerger.merge(existing, incoming)
    private val merged = result.data

    @Test
    fun `people are matched by id or by name, never duplicated`() {
        assertEquals(listOf("Rahul", "Amit", "Neha", "Vikas"), merged.people.map { it.name })
        val rahul = merged.people.single { it.uuid == "p-rahul" }
        assertEquals("91234 56789", rahul.phone)
        assertEquals(1L, rahul.id)
        assertEquals(TableMerge(added = 1, updated = 1, unchanged = 1), result.stats[BackupWorkbook.PEOPLE])
    }

    @Test
    fun `the newer version of an expense wins, together with its shares`() {
        val split = merged.expenses.single { it.uuid == "e-2" }
        assertEquals("Edited on the other phone", split.note)
        assertEquals(2L, split.id)
        val shares = merged.ledger.filter { it.expenseUuid == "e-2" }.associate { it.personUuid to it.amount }
        assertEquals(mapOf("p-rahul" to Money.rupees(450), "p-vikas" to Money.rupees(450)), shares)
        // The share that existed before keeps its row id.
        assertEquals(2L, merged.ledger.single { it.uuid == "l-2" }.id)

        val older = merged.expenses.single { it.uuid == "e-1" }
        assertEquals("Swiggy", older.merchant)
        assertEquals(Money.rupees(180), merged.ledger.single { it.uuid == "l-1" }.amount)
    }

    @Test
    fun `references follow records that were matched by name`() {
        assertEquals("p-amit", merged.ledger.single { it.uuid == "l-12" }.personUuid)
        assertEquals("c-hdfc", merged.expenses.single { it.uuid == "e-6" }.cardUuid)
        assertEquals("c-hdfc", merged.cardPayments.single { it.uuid == "cp-2" }.cardUuid)
        assertEquals(2, merged.cards.size)
        assertEquals(existing.categories.size, merged.categories.size)
    }

    @Test
    fun `a newer rule for the same merchant updates the existing rule`() {
        val swiggy = merged.cashbackRules.filter { it.merchant.equals("swiggy", ignoreCase = true) }

        assertEquals(1, swiggy.size)
        assertEquals("rule-swiggy", swiggy.single().uuid)
        assertEquals(Percentage(1_200), swiggy.single().percentage)
    }

    @Test
    fun `nothing is deleted and this device keeps its settings`() {
        existing.expenses.forEach { expense -> assertTrue(merged.expenses.any { it.uuid == expense.uuid }) }
        existing.ledger.filter { it.type != LedgerType.EXPENSE_SHARE }.forEach { entry -> assertTrue(merged.ledger.any { it.uuid == entry.uuid }) }
        assertEquals(existing.settings, merged.settings)
        assertEquals(existing.recurring, merged.recurring)
    }

    @Test
    fun `counts what will change`() {
        assertEquals(TableMerge(added = 1, updated = 1, unchanged = 1), result.stats[BackupWorkbook.EXPENSES])
        assertEquals(TableMerge(added = 1, unchanged = 1), result.stats[BackupWorkbook.PAYMENTS])
        assertEquals(1, result.stats.getValue(BackupWorkbook.CARD_PAYMENTS).added)
    }

    @Test
    fun `merging the same backup twice changes nothing more`() {
        val again = BackupMerger.merge(merged, incoming)

        assertEquals(0, again.added)
        assertEquals(0, again.updated)
        assertEquals(merged, again.data)
    }

    @Test
    fun `merging into an empty app adds everything`() {
        val fresh = BackupMerger.merge(BackupData(), BackupFixtures.data)

        assertEquals(BackupFixtures.data.expenses.size, fresh.stats.getValue(BackupWorkbook.EXPENSES).added)
        assertEquals(BackupFixtures.data.ledger.toSet(), fresh.data.ledger.toSet())
    }
}
