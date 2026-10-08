package io.github.chiragbhatn.expensetracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chiragbhatn.expensetracker.domain.BalanceState
import io.github.chiragbhatn.expensetracker.domain.CashbackBreakdown
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.ExpenseInput
import io.github.chiragbhatn.expensetracker.domain.Frequency
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.LegacyShares
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.SettlementKind
import io.github.chiragbhatn.expensetracker.domain.Share
import io.github.chiragbhatn.expensetracker.domain.Split
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private val app = TestApp()
    private val today = app.clock.today()

    @After
    fun tearDown() = app.close()

    private fun swiggy(rupees: Long, cardId: Long? = null, merchant: String = "Swiggy", split: (Money) -> Split): ExpenseInput {
        val amounts = CashbackBreakdown.calculate(Money.rupees(rupees), Percentage(1_000))
        return ExpenseInput(amounts.originalAmount, amounts.cashbackPercentage, merchant, PaymentMethod.CARD, split(amounts.effectiveAmount), today, "", "Food", cardId)
    }

    private suspend fun summaryOf(personId: Long) = app.data().balances.single { it.person.id == personId }.summary

    @Test
    fun cashbackLowersWhatThePersonOwes() = runBlocking {
        val rahul = app.people.addPerson("Rahul")
        val amit = app.people.addPerson("Amit")

        app.expenses.save(null, swiggy(200) { Split.forPerson(rahul, it) })
        app.expenses.save(null, swiggy(500) { Split.forPerson(amit, it) })

        val data = app.data()
        val small = data.expenses.single { it.amounts.originalAmount == Money.rupees(200) }
        assertEquals(Money.rupees(20), small.amounts.cashbackAmount)
        assertEquals(Money.rupees(180), small.amounts.effectiveAmount)
        assertEquals(Money.rupees(180), summaryOf(rahul).receivable)
        assertEquals(Money.rupees(450), summaryOf(amit).receivable)
        assertEquals(LedgerType.EXPENSE_SHARE, data.entries.first().type)
    }

    @Test
    fun anExpenseCanBeSplitBetweenSeveralPeople() = runBlocking {
        val rahul = app.people.addPerson("Rahul")
        val amit = app.people.addPerson("Amit")

        val id = app.expenses.save(null, swiggy(1_000) { Split.equal(it, includeMe = true, personIds = listOf(rahul, amit)) })

        val expense = app.data().expensesById.getValue(id)
        assertEquals(Money.rupees(900), expense.amounts.effectiveAmount)
        assertEquals(listOf(Money.rupees(300), Money.rupees(300)), expense.shares.map { it.amount })
        assertEquals(Money.rupees(300), expense.myShare)
        assertEquals(Money.rupees(300), summaryOf(rahul).receivable)
        assertEquals(Money.rupees(300), summaryOf(amit).receivable)
    }

    @Test
    fun aSplitThatDoesNotAddUpIsNotSaved() = runBlocking {
        val rahul = app.people.addPerson("Rahul")
        try {
            app.expenses.save(null, swiggy(1_000) { Split(Money.ZERO, listOf(Share(rahul, Money.rupees(1_000)))) })
            fail("A share above the effective amount must be refused")
        } catch (e: InvalidSplitException) {
            assertTrue(e.problems.isNotEmpty())
        }
        assertTrue(app.data().expenses.isEmpty())
        assertTrue(app.data().entries.isEmpty())
    }

    @Test
    fun editingAnExpenseUpdatesItsShares() = runBlocking {
        val rahul = app.people.addPerson("Rahul")
        val amit = app.people.addPerson("Amit")
        val id = app.expenses.save(null, swiggy(1_000) { Split.equal(it, includeMe = false, personIds = listOf(rahul, amit)) })
        val rahulShareId = app.data().entries.single { it.personId == rahul }.id

        app.expenses.save(id, swiggy(1_000) { Split.forPerson(rahul, it) })

        val entries = app.data().entries
        assertEquals(listOf(rahul), entries.map { it.personId })
        assertEquals(rahulShareId, entries.single().id)
        assertEquals(Money.rupees(900), entries.single().amount)
        assertEquals(BalanceState.SETTLED, summaryOf(amit).state)
    }

    @Test
    fun settlingPartlyOrPayingExtra() = runBlocking {
        val rahul = app.people.addPerson("Rahul")
        val amit = app.people.addPerson("Amit")
        app.expenses.save(null, swiggy(500) { Split.forPerson(rahul, it) })
        app.expenses.save(null, swiggy(500) { Split.forPerson(amit, it) })

        val partial = app.people.settle(rahul, Money.rupees(300), today, "")
        assertEquals(SettlementKind.PARTIAL, partial.kind)
        assertEquals(UdhaarDirection.GOT, partial.direction)
        assertEquals(Money.rupees(150), partial.after.receivable)

        val extra = app.people.settle(amit, Money.rupees(500), today, "")
        assertEquals(SettlementKind.EXTRA, extra.kind)
        assertEquals(Money.ZERO, extra.after.receivable)
        assertEquals(Money.rupees(50), extra.after.credit)
        assertEquals(BalanceState.HAS_CREDIT, summaryOf(amit).state)
        assertEquals(LedgerType.SETTLEMENT, app.data().entries.first { it.personId == amit && it.direction == UdhaarDirection.GOT }.type)
    }

    @Test
    fun paymentsAndUdhaarWithAPerson() = runBlocking {
        val neha = app.people.addPerson("Neha")
        app.people.addEntry(neha, LedgerType.UDHAAR_TAKEN, UdhaarDirection.GAVE, Money.rupees(1_000), today, "Trip")
        assertEquals(BalanceState.YOU_OWE, summaryOf(neha).state)
        assertEquals(Money.rupees(1_000), summaryOf(neha).payable)

        val settle = app.people.settle(neha, Money.rupees(1_000), today, "")
        assertEquals(UdhaarDirection.GAVE, settle.direction)
        assertEquals(BalanceState.SETTLED, settle.after.state)
    }

    @Test
    fun deletingAPersonKeepsTheExpense() = runBlocking {
        val rahul = app.people.addPerson("Rahul")
        val id = app.expenses.save(null, swiggy(200) { Split.forPerson(rahul, it) })

        app.people.delete(rahul)

        val data = app.data()
        assertEquals(Money.rupees(180), data.expensesById.getValue(id).myShare)
        assertTrue(data.entries.isEmpty())
    }

    @Test
    fun deletingACardKeepsItsExpenses() = runBlocking {
        val card = app.cards.save(null, CardInput("Millennia", "HDFC", "1234", Money.rupees(1_00_000), 20, 5))
        app.rules.save(null, "Amazon", Percentage(500), enabled = true, cardId = card)
        val id = app.expenses.save(null, swiggy(200, cardId = card) { Split.mine(it) })
        app.cards.addPayment(card, Money.rupees(100), today, "")

        app.cards.delete(card)

        val data = app.data()
        assertNull(data.expensesById.getValue(id).cardId)
        assertTrue(data.cards.isEmpty())
        assertTrue(data.cardPayments.isEmpty())
        assertTrue(data.rules.none { it.cardId == card })
    }

    @Test
    fun oneRulePerMerchantForAnyCardAndPerCard() = runBlocking {
        val card = app.cards.save(null, CardInput("Pay", "ICICI", "9999", Money.rupees(50_000), 1, 20))

        assertTrue(app.rules.save(null, "swiggy", Percentage(500), true) is SaveRuleResult.DuplicateMerchant)
        assertEquals(SaveRuleResult.Saved, app.rules.save(null, "Swiggy", Percentage(1_500), true, cardId = card))
        assertTrue(app.rules.save(null, "SWIGGY", Percentage(500), true, cardId = card) is SaveRuleResult.DuplicateMerchant)
        assertEquals(2, app.data().rules.count { it.merchant.equals("swiggy", ignoreCase = true) })
    }

    @Test
    fun recurringExpensesAreRecordedOnceWhenDue() = runBlocking {
        val card = app.cards.save(null, CardInput("Millennia", "HDFC", "1234", Money.rupees(1_00_000), 20, 5))
        app.rules.save(null, "Netflix", Percentage(500), enabled = true)
        val id = app.recurring.save(
            null,
            RecurringInput("Netflix", Money.rupees(649), "Subscription", PaymentMethod.CARD, card, LocalDate.of(2026, 9, 15), Frequency.MONTHLY),
        )
        // Started in the past: nothing is back-filled; the next date is in the future.
        assertEquals(LocalDate.of(2026, 10, 15), app.data().recurring.single().nextDate)
        assertTrue(app.recurring.recordDue().isEmpty())

        app.clock.date = LocalDate.of(2026, 11, 20)
        val created = app.recurring.recordDue()
        assertEquals(2, created.size)
        assertTrue(app.recurring.recordDue().isEmpty())

        val data = app.data()
        val recorded = data.expenses.filter { it.recurringId == id }.sortedBy { it.date }
        assertEquals(listOf(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15)), recorded.map { it.date })
        assertEquals(Money(3_245), recorded.first().amounts.cashbackAmount)
        assertEquals(card, recorded.first().cardId)
        assertEquals(LocalDate.of(2026, 12, 15), data.recurring.single().nextDate)

        app.recurring.setActive(id, false)
        app.clock.date = LocalDate.of(2027, 1, 20)
        assertTrue(app.recurring.recordDue().isEmpty())
        // Resuming continues from today without filling in the paused months.
        app.recurring.setActive(id, true)
        assertEquals(LocalDate.of(2027, 2, 15), app.data().recurring.single().nextDate)
    }

    @Test
    fun categoriesCanBeAddedRenamedAndDeleted() = runBlocking {
        assertEquals(13, app.data().categories.count { it.kind == CategoryKind.EXPENSE })
        assertEquals(CategoryResult.Done, app.categories.add("Pets", CategoryKind.EXPENSE))
        assertTrue(app.categories.add("pets", CategoryKind.EXPENSE) is CategoryResult.Exists)
        val id = app.expenses.save(null, swiggy(200) { Split.mine(it) }.copy(category = "Pets"))

        val pets = app.data().categories.single { it.name == "Pets" }
        app.categories.rename(pets.id, "Pet care")
        assertEquals("Pet care", app.data().expensesById.getValue(id).category)

        app.categories.delete(pets.id)
        assertEquals("Other", app.data().expensesById.getValue(id).category)
        val other = app.data().categories.first { it.name == "Other" }
        assertEquals(CategoryResult.Protected, app.categories.delete(other.id))
    }

    @Test
    fun version1SharesAreCorrectedOnlyWhenApplied() = runBlocking {
        val rahul = app.people.addPerson("Rahul")
        val id = app.expenses.save(null, swiggy(1_000) { Split.forPerson(rahul, it) })
        // Recreate what version 1 saved: the person charged the original amount.
        val share = app.database.udhaarDao().sharesForExpense(id).single()
        app.database.udhaarDao().update(share.copy(amountPaise = 1_000_00))

        val fixes = LegacyShares.find(app.data().expenses)
        assertEquals(Money.rupees(1_000), summaryOf(rahul).receivable)

        app.expenses.applyShareFixes(fixes)

        assertEquals(Money.rupees(900), summaryOf(rahul).receivable)
        assertTrue(LegacyShares.find(app.data().expenses).isEmpty())
    }

    @Test
    fun personDetailsAreSavedAndNamesStayUnique() = runBlocking {
        val result = app.people.save(null, PersonInput("Rahul", phone = "98765 43210", tags = listOf("college", "flatmate")))
        val id = (result as SavePersonResult.Saved).id
        assertTrue(app.people.save(null, PersonInput("rahul")) is SavePersonResult.NameTaken)

        val person = app.data().peopleById.getValue(id)
        assertEquals("98765 43210", person.phone)
        assertEquals(listOf("college", "flatmate"), person.tags)
        assertTrue(person.uuid.isNotEmpty())
    }
}
