package io.github.chiragbhatn.expensetracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.domain.ExpenseInput
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.Period
import io.github.chiragbhatn.expensetracker.domain.SpendingSummary
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var rules: CashbackRuleRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var udhaar: UdhaarRepository
    private val today = LocalDate.of(2026, 9, 28)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(AppDatabase.SeedDefaultRules)
            .build()
        rules = CashbackRuleRepository(database)
        expenses = ExpenseRepository(database)
        udhaar = UdhaarRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private fun swiggy(rupees: Long, paidFor: Long? = null, merchant: String = "Swiggy") = ExpenseInput(
        originalAmount = Money.rupees(rupees),
        cashbackPercentage = Percentage(1_000),
        merchant = merchant,
        paymentMethod = PaymentMethod.CARD,
        paidForPersonId = paidFor,
        date = today,
        note = "",
    )

    private suspend fun balanceOf(personId: Long): Money =
        udhaar.observeBalances().first().single { it.person.id == personId }.balance

    @Test
    fun seedsTheDefaultRules() = runBlocking<Unit> {
        val seeded = rules.observeAll().first()

        assertEquals(
            listOf(Triple("Amazon", 500, false), Triple("Swiggy", 1_000, true), Triple("Zomato", 1_000, false)),
            seeded.map { Triple(it.merchant, it.percentage.basisPoints, it.enabled) },
        )
    }

    @Test
    fun payingForRahulKeepsTheExpenseAndUdhaarSidesApart() = runBlocking<Unit> {
        val rahul = udhaar.addPerson("Rahul")

        val id = expenses.save(null, swiggy(1_000, paidFor = rahul))

        val saved = expenses.get(id)!!
        assertEquals(Money.rupees(1_000), saved.amounts.originalAmount)
        assertEquals(Percentage(1_000), saved.amounts.cashbackPercentage)
        assertEquals(Money.rupees(100), saved.amounts.cashbackAmount)
        assertEquals(Money.rupees(900), saved.amounts.effectiveAmount)
        assertEquals("Rahul", saved.paidFor?.name)
        // Rahul owes the full card amount, not the ₹900 effective expense.
        assertEquals(Money.rupees(1_000), balanceOf(rahul))

        val summary = SpendingSummary.of(expenses.observe(Period.AllTime).first())
        assertEquals(Money.rupees(1_000), summary.cardSpending)
        assertEquals(Money.rupees(100), summary.cashbackReceived)
        assertEquals(Money.rupees(900), summary.effectiveExpenses)
    }

    @Test
    fun editingAnExpenseKeepsItsUdhaarEntryInStep() = runBlocking<Unit> {
        val rahul = udhaar.addPerson("Rahul")
        val amit = udhaar.addPerson("Amit")
        val id = expenses.save(null, swiggy(1_000, paidFor = rahul))

        expenses.save(id, swiggy(1_500, paidFor = rahul))
        assertEquals(Money.rupees(1_500), balanceOf(rahul))

        expenses.save(id, swiggy(1_500, paidFor = amit))
        assertEquals(Money.ZERO, balanceOf(rahul))
        assertEquals(Money.rupees(1_500), balanceOf(amit))

        expenses.save(id, swiggy(1_500, paidFor = null))
        assertEquals(Money.ZERO, balanceOf(amit))
        assertEquals(Money.rupees(1_350), expenses.get(id)!!.amounts.effectiveAmount)
    }

    @Test
    fun deletingAnExpenseRemovesItsUdhaarEntry() = runBlocking<Unit> {
        val rahul = udhaar.addPerson("Rahul")
        val id = expenses.save(null, swiggy(1_000, paidFor = rahul))

        expenses.delete(id)

        assertNull(expenses.get(id))
        assertEquals(Money.ZERO, balanceOf(rahul))
    }

    @Test
    fun repaymentsReduceWhatIsOwedButNotTheExpense() = runBlocking<Unit> {
        val rahul = udhaar.addPerson("Rahul")
        expenses.save(null, swiggy(1_000, paidFor = rahul))

        udhaar.addEntry(rahul, UdhaarDirection.GOT, Money.rupees(400), today, "UPI")

        assertEquals(Money.rupees(600), balanceOf(rahul))
        assertEquals(Money.rupees(900), expenses.observe(Period.AllTime).first().single().amounts.effectiveAmount)
        val history = udhaar.observeEntries(rahul).first()
        assertEquals(listOf("Swiggy", null), history.sortedBy { it.id }.map { it.expenseMerchant })
    }

    @Test
    fun deletingAPersonKeepsTheirExpensesAsYourOwn() = runBlocking<Unit> {
        val rahul = udhaar.addPerson("Rahul")
        val id = expenses.save(null, swiggy(1_000, paidFor = rahul))

        udhaar.deletePerson(rahul)

        val expense = expenses.get(id)!!
        assertNull(expense.paidFor)
        assertEquals(Money.rupees(900), expense.amounts.effectiveAmount)
        assertTrue(udhaar.observeBalances().first().isEmpty())
    }

    @Test
    fun ruleChangesDoNotRewriteSavedExpenses() = runBlocking<Unit> {
        val id = expenses.save(null, swiggy(500))
        val rule = rules.observeAll().first().single { it.merchant == "Swiggy" }

        rules.save(rule.id, "Swiggy", Percentage(2_000), enabled = true)
        rules.setEnabled(rule.id, false)

        assertEquals(Money.rupees(50), expenses.get(id)!!.amounts.cashbackAmount)
        assertEquals(Money.rupees(450), expenses.get(id)!!.amounts.effectiveAmount)
    }

    @Test
    fun rulesAreUniquePerMerchantIgnoringCase() = runBlocking<Unit> {
        assertEquals(
            SaveRuleResult.DuplicateMerchant("Swiggy"),
            rules.save(null, " swiggy ", Percentage(500), enabled = true),
        )
        assertEquals(SaveRuleResult.Saved, rules.save(null, "Custom", Percentage(200), enabled = false))

        val custom = rules.observeAll().first().single { it.merchant == "Custom" }
        assertEquals(SaveRuleResult.Saved, rules.save(custom.id, "Custom", Percentage(250), enabled = true))
        assertEquals(Percentage(250), rules.observeAll().first().single { it.id == custom.id }.percentage)

        rules.delete(custom.id)
        assertTrue(rules.observeAll().first().none { it.merchant == "Custom" })
    }

    @Test
    fun merchantsTakeTheRuleSpelling() = runBlocking<Unit> {
        val id = expenses.save(null, swiggy(100, merchant = "  swiggy "))

        assertEquals("Swiggy", expenses.get(id)!!.merchant)
    }

    @Test
    fun peopleAreUniqueIgnoringCase() = runBlocking<Unit> {
        val rahul = udhaar.addPerson("Rahul")
        assertEquals(rahul, udhaar.addPerson(" rahul"))

        val amit = udhaar.addPerson("Amit")
        assertFalse(udhaar.renamePerson(amit, "RAHUL"))
        assertTrue(udhaar.renamePerson(amit, "Amit K"))
    }
}
