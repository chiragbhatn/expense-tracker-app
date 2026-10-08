package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReportsTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val october = YearMonth.of(2026, 10)
    private val rahul = Person(1, "Rahul")
    private val amit = Person(2, "Amit")
    private val card = CreditCard(1, "Millennia", "HDFC", "1234", Money.rupees(50_000), 20, 5, "", true)

    private fun expense(
        id: Long,
        merchant: String,
        rupees: Long,
        bps: Int = 0,
        date: LocalDate = today,
        method: PaymentMethod = PaymentMethod.CARD,
        category: String = "Food",
        cardId: Long? = if (method == PaymentMethod.CARD) 1 else null,
        shares: List<PersonShare> = emptyList(),
    ) = Expense(id, merchant, method, CashbackBreakdown.calculate(Money.rupees(rupees), Percentage(bps)), shares, date, "", category, cardId)

    private val expenses = listOf(
        expense(1, "Swiggy", 200, 1_000, LocalDate.of(2026, 10, 3), shares = listOf(PersonShare(rahul, Money.rupees(180)))),
        expense(2, "DMart", 1_000, method = PaymentMethod.UPI, date = LocalDate.of(2026, 10, 4), category = "Groceries"),
        expense(3, "swiggy", 500, 1_000, LocalDate.of(2026, 9, 12)),
    )
    private val incomes = listOf(
        Income(1, Money.rupees(50_000), "Salary", "Salary", LocalDate.of(2026, 10, 1), ""),
        Income(2, Money.rupees(40_000), "Salary", "Salary", LocalDate.of(2026, 9, 1), ""),
    )
    private val entries = listOf(
        UdhaarEntry(1, rahul.id, UdhaarDirection.GAVE, Money.rupees(180), LocalDate.of(2026, 10, 3), "", 1, "Swiggy", LedgerType.EXPENSE_SHARE),
        UdhaarEntry(2, amit.id, UdhaarDirection.GOT, Money.rupees(500), LocalDate.of(2026, 10, 5), "", null, null, LedgerType.UDHAAR_TAKEN),
    )

    @Test
    fun `monthly summary counts cashback once and nets what is owed`() {
        val summary = MonthlySummary.of(Period.Month(october), expenses, incomes, listOf(rahul, amit), entries, today)

        assertEquals(Money.rupees(50_000), summary.income)
        assertEquals(Money.rupees(1_200), summary.originalExpenses)
        assertEquals(Money.rupees(20), summary.cashback)
        assertEquals(Money.rupees(1_180), summary.effectiveExpenses)
        assertEquals(summary.originalExpenses - summary.cashback, summary.effectiveExpenses)
        assertEquals(Money.rupees(1_000), summary.myShareOfExpenses)
        assertEquals(Money.rupees(180), summary.receivable)
        assertEquals(Money.rupees(500), summary.payable)
        assertEquals(Money.rupees(48_820), summary.cashFlow)
        assertEquals(Money.rupees(48_500), summary.netPosition)
    }

    @Test
    fun `previous months use balances as they were at the month end`() {
        val september = MonthlySummary.of(Period.Month(october.minusMonths(1)), expenses, incomes, listOf(rahul, amit), entries, today)

        assertEquals(Money.rupees(40_000), september.income)
        assertEquals(Money.rupees(450), september.effectiveExpenses)
        assertEquals(Money.ZERO, september.receivable)
        assertEquals(Money.ZERO, september.payable)
    }

    @Test
    fun `trend has one point per month, oldest first`() {
        val trend = Reports.trend(expenses, incomes, october, months = 3)

        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), october), trend.map { it.month })
        assertEquals(listOf(Money.ZERO, Money.rupees(500), Money.rupees(1_200)), trend.map { it.originalExpenses })
        assertEquals(listOf(Money.ZERO, Money.rupees(50), Money.rupees(20)), trend.map { it.cashback })
        assertEquals(listOf(Money.ZERO, Money.rupees(40_000), Money.rupees(50_000)), trend.map { it.income })
    }

    @Test
    fun `categories and merchants`() {
        val categories = Reports.byCategory(expenses)
        assertEquals(listOf("Groceries", "Food"), categories.map { it.category })
        assertEquals(listOf(Money.rupees(1_000), Money.rupees(630)), categories.map { it.effective })
        assertEquals(1.0, categories.sumOf { it.fraction }, 1e-9)

        val merchants = Reports.byMerchant(expenses)
        assertEquals(2, merchants.size)
        val swiggy = merchants.first { nameKey(it.merchant) == "swiggy" }
        assertEquals(Money.rupees(700), swiggy.original)
        assertEquals(Money.rupees(70), swiggy.cashback)
        assertEquals(Money.rupees(630), swiggy.effective)
        assertEquals(2, swiggy.count)

        assertEquals(listOf(AmountByKey("Millennia ••1234", Money.rupees(700))), Reports.byCard(expenses, listOf(card)))
    }

    @Test
    fun `cashback by period, merchant and card`() {
        val many = expenses + listOf(
            expense(4, "Zomato", 1_000, 500, LocalDate.of(2026, 10, 6)),
            expense(5, "Amazon", 2_000, 500, LocalDate.of(2026, 10, 7)),
            expense(6, "Myntra", 400, 500, today),
            expense(7, "Flipkart", 100, 1_000, today, cardId = null),
        )

        val stats = CashbackStats.of(many, listOf(card), today)

        assertEquals(Money.rupees(30), stats.today)
        assertEquals(Money.rupees(200), stats.thisMonth)
        assertEquals(Money.rupees(250), stats.thisYear)
        assertEquals(Money.rupees(250), stats.total)
        assertEquals(
            listOf(
                AmountByKey("Amazon", Money.rupees(100)),
                AmountByKey("Zomato", Money.rupees(50)),
                AmountByKey("Swiggy", Money.rupees(20)),
                AmountByKey("Other", Money.rupees(30)),
            ),
            stats.monthByMerchant,
        )
        assertEquals(listOf(AmountByKey("Millennia ••1234", Money.rupees(240)), AmountByKey("No card selected", Money.rupees(10))), stats.byCard)
        assertEquals(Money.rupees(3_700), stats.monthCardSpending)
        assertEquals(Money.rupees(3_500), stats.monthEffectiveCardSpending)
    }
}
