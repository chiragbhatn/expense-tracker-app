package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val rahul = Person(1, "Rahul", phone = "98765 43210", tags = listOf("college"))
    private val amit = Person(2, "Amit")
    private val card = CreditCard(1, "Millennia", "HDFC", "1234", Money.rupees(50_000), 20, 5, "", true)

    private fun expense(id: Long, merchant: String, rupees: Long, bps: Int, shares: List<PersonShare>, cardId: Long?, method: PaymentMethod = PaymentMethod.CARD) =
        Expense(id, merchant, method, CashbackBreakdown.calculate(Money.rupees(rupees), Percentage(bps)), shares, today.minusDays(id), "", "Food", cardId)

    private val data = SearchData(
        people = listOf(rahul, amit),
        expenses = listOf(
            expense(1, "Swiggy", 200, 1_000, listOf(PersonShare(rahul, Money.rupees(180))), cardId = 1),
            expense(2, "swiggy", 500, 1_000, listOf(PersonShare(amit, Money.rupees(450))), cardId = 1),
            expense(3, "Zomato", 300, 0, emptyList(), cardId = null, method = PaymentMethod.UPI),
        ),
        incomes = listOf(Income(1, Money.rupees(50_000), "Salary", "Salary", today, "October")),
        entries = listOf(
            UdhaarEntry(1, rahul.id, UdhaarDirection.GAVE, Money.rupees(180), today.minusDays(1), "", 1, "Swiggy", LedgerType.EXPENSE_SHARE),
            UdhaarEntry(2, amit.id, UdhaarDirection.GAVE, Money.rupees(450), today.minusDays(2), "", 2, "swiggy", LedgerType.EXPENSE_SHARE),
            UdhaarEntry(3, rahul.id, UdhaarDirection.GOT, Money.rupees(100), today, "GPay", null, null, LedgerType.PAYMENT_RECEIVED),
        ),
        cards = listOf(card),
    )

    @Test
    fun `a merchant shows its totals, people and cards`() {
        val results = Search.run("Swiggy", data)

        val swiggy = results.merchants.single()
        assertEquals(Money.rupees(700), swiggy.original)
        assertEquals(Money.rupees(70), swiggy.cashback)
        assertEquals(Money.rupees(630), swiggy.effective)
        assertEquals(2, swiggy.count)
        assertEquals(listOf(amit, rahul), swiggy.people)
        assertEquals(listOf(card), swiggy.cards)
        assertEquals(listOf(1L, 2L), results.expenses.map { it.id })
        assertEquals(listOf(1L, 2L), results.transactions.map { it.entry.id })
        assertTrue(results.people.isEmpty())
    }

    @Test
    fun `a person shows their profile, balance, expenses and payments`() {
        val results = Search.run("rahul", data)

        val match = results.people.single()
        assertEquals(rahul, match.person)
        assertEquals(Money.rupees(80), match.summary.receivable)
        assertEquals(1, match.expenseCount)
        assertEquals(1, match.paymentCount)
        assertEquals(listOf(1L), results.expenses.map { it.id })
        assertEquals(listOf(3L, 1L), results.transactions.map { it.entry.id })
        assertEquals(rahul, results.transactions.first().person)
    }

    @Test
    fun `every word must match`() {
        assertEquals(listOf(1L), Search.run("swiggy  RAHUL", data).expenses.map { it.id })
        assertEquals(listOf(3L), Search.run("zomato upi", data).expenses.map { it.id })
    }

    @Test
    fun `finds cards, income, phone numbers, tags and notes`() {
        assertEquals(listOf(card), Search.run("hdfc", data).cards.map { it.card })
        assertEquals(Money.rupees(700), Search.run("1234", data).cards.single().spending)
        assertEquals(listOf(1L), Search.run("october", data).incomes.map { it.id })
        assertEquals(listOf(rahul), Search.run("98765", data).people.map { it.person })
        assertEquals(listOf(rahul), Search.run("college", data).people.map { it.person })
        assertEquals(listOf(3L), Search.run("gpay", data).transactions.map { it.entry.id })
    }

    @Test
    fun `blank queries find nothing`() {
        assertTrue(Search.run("   ", data).isEmpty)
        assertTrue(Search.run("nothing-like-this", data).isEmpty)
    }
}
