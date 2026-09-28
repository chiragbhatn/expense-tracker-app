package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerTest {

    private val rahul = Person(7, "Rahul")
    private val today = LocalDate.of(2026, 9, 28)

    private fun input(amount: Money, percentage: Percentage, paidFor: Person?, method: PaymentMethod = PaymentMethod.CARD) =
        ExpenseInput(
            originalAmount = amount,
            cashbackPercentage = percentage,
            merchant = "Swiggy",
            paymentMethod = method,
            paidForPersonId = paidFor?.id,
            date = today,
            note = "",
        )

    private fun expense(input: ExpenseInput, id: Long) = Expense(
        id = id,
        merchant = input.merchant,
        paymentMethod = input.paymentMethod,
        amounts = input.toLedgerPosting().expense,
        paidFor = if (input.paidForPersonId == rahul.id) rahul else null,
        date = input.date,
        note = input.note,
    )

    @Test
    fun `paying for someone keeps cashback with the card holder`() {
        val posting = input(Money.rupees(1_000), Percentage(1_000), paidFor = rahul).toLedgerPosting()

        // Card / expense side
        assertEquals(Money.rupees(1_000), posting.expense.originalAmount)
        assertEquals(Money.rupees(100), posting.expense.cashbackAmount)
        assertEquals(Money.rupees(900), posting.expense.effectiveAmount)
        // Udhaar side: Rahul owes the full card amount, not the effective ₹900
        assertEquals(Money.rupees(1_000), posting.udhaarOwed)
    }

    @Test
    fun `paying for yourself creates no udhaar`() {
        assertNull(input(Money.rupees(1_000), Percentage(1_000), paidFor = null).toLedgerPosting().udhaarOwed)
    }

    @Test
    fun `dashboard separates card spending, cashback and effective expenses`() {
        val summary = SpendingSummary.of(listOf(expense(input(Money.rupees(1_000), Percentage(1_000), rahul), id = 1)))

        assertEquals(Money.rupees(1_000), summary.cardSpending)
        assertEquals(Money.ZERO, summary.otherSpending)
        assertEquals(Money.rupees(100), summary.cashbackReceived)
        assertEquals(Money.rupees(900), summary.effectiveExpenses)
    }

    @Test
    fun `effective expenses equal everything spent minus cashback`() {
        val summary = SpendingSummary.of(
            listOf(
                expense(input(Money.rupees(1_000), Percentage(1_000), rahul), id = 1),
                expense(input(Money.rupees(200), Percentage.ZERO, null, PaymentMethod.UPI), id = 2),
            ),
        )

        assertEquals(Money.rupees(200), summary.otherSpending)
        assertEquals(Money.rupees(1_100), summary.effectiveExpenses)
        assertEquals(summary.cardSpending + summary.otherSpending - summary.cashbackReceived, summary.effectiveExpenses)
        assertEquals(2, summary.transactionCount)
    }

    @Test
    fun `udhaar balances net what you gave against what you got`() {
        val amit = Person(8, "amit")
        val neha = Person(9, "Neha")
        fun entry(person: Person, direction: UdhaarDirection, rupees: Long) =
            UdhaarEntry(0, person.id, direction, Money.rupees(rupees), today, "", null, null)

        val result = balances(
            people = listOf(neha, rahul, amit),
            entries = listOf(
                entry(rahul, UdhaarDirection.GAVE, 1_000),
                entry(rahul, UdhaarDirection.GOT, 400),
                entry(amit, UdhaarDirection.GOT, 250),
            ),
        )

        assertEquals(listOf(amit, rahul, neha), result.map { it.person })
        assertEquals(listOf(Money.rupees(-250), Money.rupees(600), Money.ZERO), result.map { it.balance })
        assertEquals(Money.rupees(600), result.moneyToReceive())
        assertEquals(Money.rupees(250), result.moneyToGive())
    }

    @Test
    fun `a month covers its first to last day`() {
        val days = Period.Month(YearMonth.of(2026, 2)).epochDays

        assertEquals(LocalDate.of(2026, 2, 1).toEpochDay(), days.first)
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), days.last)
    }
}
