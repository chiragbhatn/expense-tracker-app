package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class CardsTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val rahul = Person(1, "Rahul")
    private val millennia = CreditCard(
        id = 1,
        name = "Millennia",
        bank = "HDFC",
        lastFour = "1234",
        creditLimit = Money.rupees(1_00_000),
        statementDay = 20,
        dueDay = 5,
        notes = "",
        active = true,
    )

    private fun expense(id: Long, merchant: String, rupees: Long, bps: Int, date: LocalDate, cardId: Long? = 1, shares: List<PersonShare> = emptyList(), category: String = "Food") =
        Expense(
            id = id,
            merchant = merchant,
            paymentMethod = PaymentMethod.CARD,
            amounts = CashbackBreakdown.calculate(Money.rupees(rupees), Percentage(bps)),
            shares = shares,
            date = date,
            note = "",
            category = category,
            cardId = cardId,
        )

    private val expenses = listOf(
        expense(1, "Swiggy", 1_000, 1_000, LocalDate.of(2026, 9, 10), shares = listOf(PersonShare(rahul, Money.rupees(900)))),
        expense(2, "Amazon", 2_000, 0, LocalDate.of(2026, 10, 2), category = "Shopping"),
        expense(3, "Swiggy", 500, 1_000, LocalDate.of(2026, 10, 3)),
        expense(4, "Petrol", 3_000, 0, LocalDate.of(2026, 10, 4), cardId = 2, category = "Fuel"),
    )
    private val payments = listOf(CardPayment(1, cardId = 1, amount = Money.rupees(500), date = LocalDate.of(2026, 9, 25), note = ""))

    @Test
    fun `outstanding is spending minus cashback minus payments`() {
        val summary = CardMath.summary(millennia, expenses, payments, today)

        assertEquals(Money.rupees(3_500), summary.totalSpending)
        assertEquals(Money.rupees(150), summary.cashbackEarned)
        assertEquals(Money.rupees(500), summary.totalPaid)
        assertEquals(Money.rupees(2_850), summary.outstanding)
        assertEquals(Money.rupees(97_150), summary.availableLimit)
        assertFalse(summary.isOverLimit)
    }

    @Test
    fun `tracks this month, money spent for others and where it went`() {
        val summary = CardMath.summary(millennia, expenses, payments, today)

        assertEquals(Money.rupees(2_500), summary.monthSpending)
        assertEquals(Money.rupees(900), summary.spentOnBehalfOfOthers)
        assertEquals(listOf(AmountByKey("Amazon", Money.rupees(2_000)), AmountByKey("Swiggy", Money.rupees(1_500))), summary.byMerchant)
        assertEquals(listOf(AmountByKey("Shopping", Money.rupees(2_000)), AmountByKey("Food", Money.rupees(1_500))), summary.byCategory)
    }

    @Test
    fun `statement and due dates follow the card's days`() {
        val summary = CardMath.summary(millennia, expenses, payments, today)

        assertEquals(LocalDate.of(2026, 9, 20), summary.lastStatementDate)
        assertEquals(LocalDate.of(2026, 10, 20), summary.nextStatementDate)
        assertEquals(LocalDate.of(2026, 10, 5), summary.dueDate)
        // Billed on 20 Sep: the ₹900 Swiggy order after cashback, less the ₹500 paid.
        assertEquals(Money.rupees(400), summary.statementBalance)
    }

    @Test
    fun `short months use their last day`() {
        assertEquals(LocalDate.of(2026, 2, 28), CardMath.dayIn(java.time.YearMonth.of(2026, 2), 31))
        assertEquals(LocalDate.of(2026, 2, 28), CardMath.lastOnOrBefore(31, LocalDate.of(2026, 3, 15)))
        assertEquals(LocalDate.of(2026, 3, 31), CardMath.firstAfter(31, LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `unpaid statements show up as upcoming or overdue payments`() {
        val summary = CardMath.summary(millennia, expenses, payments, today)
        val paidUp = CardMath.summary(millennia, expenses, payments + CardPayment(2, 1, Money.rupees(400), LocalDate.of(2026, 10, 1), ""), today)

        assertEquals(
            listOf(UpcomingCardPayment(millennia, LocalDate.of(2026, 10, 5), Money.rupees(400), overdue = true)),
            CardMath.upcomingPayments(listOf(summary), today),
        )
        assertEquals(emptyList<UpcomingCardPayment>(), CardMath.upcomingPayments(listOf(paidUp), today))
        assertEquals(emptyList<UpcomingCardPayment>(), CardMath.upcomingPayments(listOf(summary.copy(card = millennia.copy(active = false))), today))
    }

    @Test
    fun `display name includes the last four digits`() {
        assertEquals("Millennia ••1234", millennia.displayName)
        assertEquals("Millennia", millennia.copy(lastFour = "").displayName)
    }
}
