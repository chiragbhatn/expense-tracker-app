package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashbackTest {

    private val swiggy = CashbackRule(1, "Swiggy", Percentage(1_000), enabled = true)
    private val zomato = CashbackRule(2, "Zomato", Percentage(1_000), enabled = false)
    private val amazon = CashbackRule(3, "Amazon", Percentage(500), enabled = false)
    private val rules = listOf(swiggy, zomato, amazon)

    @Test
    fun `cashback is subtracted from the original amount, never added`() {
        val breakdown = CashbackBreakdown.calculate(Money.rupees(500), Percentage(1_000))

        assertEquals(Money.rupees(500), breakdown.originalAmount)
        assertEquals(Percentage(1_000), breakdown.cashbackPercentage)
        assertEquals(Money.rupees(50), breakdown.cashbackAmount)
        assertEquals(Money.rupees(450), breakdown.effectiveAmount)
    }

    @Test
    fun `matches the worked examples`() {
        val thousand = CashbackBreakdown.calculate(Money.rupees(1_000), Percentage(1_000))
        assertEquals(Money.rupees(100), thousand.cashbackAmount)
        assertEquals(Money.rupees(900), thousand.effectiveAmount)

        val sevenFifty = CashbackBreakdown.calculate(Money.rupees(750), Percentage(1_000))
        assertEquals(Money.rupees(75), sevenFifty.cashbackAmount)
        assertEquals(Money.rupees(675), sevenFifty.effectiveAmount)
    }

    @Test
    fun `no cashback leaves the effective amount unchanged`() {
        val breakdown = CashbackBreakdown.calculate(Money.rupees(750), Percentage.ZERO)

        assertFalse(breakdown.hasCashback)
        assertEquals(Money.rupees(750), breakdown.effectiveAmount)
    }

    @Test
    fun `an enabled rule applies to card payments at that merchant`() {
        assertEquals(
            CashbackEligibility.Eligible(swiggy),
            cashbackEligibility("Swiggy", PaymentMethod.CARD, rules),
        )
        assertEquals(
            CashbackEligibility.Eligible(swiggy),
            cashbackEligibility("  swiggy ", PaymentMethod.CARD, rules),
        )
    }

    @Test
    fun `explains why a transaction earns no cashback`() {
        assertEquals(
            CashbackEligibility.RuleDisabled(zomato),
            cashbackEligibility("Zomato", PaymentMethod.CARD, rules),
        )
        assertEquals(
            CashbackEligibility.NotPaidByCard(swiggy),
            cashbackEligibility("Swiggy", PaymentMethod.UPI, rules),
        )
        assertEquals(CashbackEligibility.NoRule, cashbackEligibility("Swiggy Instamart", PaymentMethod.CARD, rules))
        assertEquals(CashbackEligibility.NoRule, cashbackEligibility("", PaymentMethod.CARD, rules))
    }

    @Test
    fun `a new transaction follows the current rule`() {
        val quote = quoteCashback("Swiggy", PaymentMethod.CARD, rules, saved = null)

        assertEquals(Percentage(1_000), quote.percentage)
        assertEquals(quote.percentage, quote.rulePercentage)
    }

    @Test
    fun `an edited transaction keeps the percentage it was saved with`() {
        val raised = listOf(swiggy.copy(percentage = Percentage(1_200)))
        val saved = SavedCashback("Swiggy", PaymentMethod.CARD, Percentage(1_000))

        val quote = quoteCashback("swiggy", PaymentMethod.CARD, raised, saved)

        assertEquals(Percentage(1_000), quote.percentage)
        assertEquals(Percentage(1_200), quote.rulePercentage)
    }

    @Test
    fun `changing merchant or payment method re-applies the rules`() {
        val saved = SavedCashback("Swiggy", PaymentMethod.CARD, Percentage(1_000))

        assertEquals(Percentage.ZERO, quoteCashback("Zomato", PaymentMethod.CARD, rules, saved).percentage)
        assertEquals(Percentage.ZERO, quoteCashback("Swiggy", PaymentMethod.CASH, rules, saved).percentage)
        assertEquals(Percentage(1_000), quoteCashback("Swiggy", PaymentMethod.CARD, rules, saved).percentage)
    }
}
