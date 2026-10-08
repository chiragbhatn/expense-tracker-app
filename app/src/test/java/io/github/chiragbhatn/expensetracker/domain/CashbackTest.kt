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

    @Test
    fun `a rule for a specific card wins over a rule for any card`() {
        val anyCard = CashbackRule(10, "Amazon", Percentage(100), enabled = true)
        val amazonPay = CashbackRule(11, "Amazon", Percentage(500), enabled = true, cardId = 2)
        val cardRules = listOf(anyCard, amazonPay)

        assertEquals(amazonPay, cardRules.forMerchant("amazon", cardId = 2))
        assertEquals(anyCard, cardRules.forMerchant("amazon", cardId = 3))
        assertEquals(anyCard, cardRules.forMerchant("amazon"))
        assertEquals(Percentage(500), quoteCashback("Amazon", PaymentMethod.CARD, cardRules, saved = null, cardId = 2).percentage)
        assertEquals(Percentage(100), quoteCashback("Amazon", PaymentMethod.CARD, cardRules, saved = null, cardId = 3).percentage)
    }

    @Test
    fun `a merchant with rules only for other cards earns nothing`() {
        val amazonPay = CashbackRule(11, "Amazon", Percentage(500), enabled = true, cardId = 2)

        assertEquals(CashbackEligibility.OtherCard(amazonPay), cashbackEligibility("Amazon", PaymentMethod.CARD, listOf(amazonPay), cardId = 3))
        assertEquals(Percentage.ZERO, quoteCashback("Amazon", PaymentMethod.CARD, listOf(amazonPay), saved = null, cardId = 3).percentage)
    }

    @Test
    fun `changing the card re-applies the rules`() {
        val saved = SavedCashback("Swiggy", PaymentMethod.CARD, Percentage(1_000), cardId = 1)

        assertEquals(Percentage(1_000), quoteCashback("Swiggy", PaymentMethod.CARD, rules, saved, cardId = 1).percentage)
        assertEquals(Percentage(1_000), quoteCashback("Swiggy", PaymentMethod.CARD, listOf(swiggy.copy(percentage = Percentage(500))), saved, cardId = 1).percentage)
        assertEquals(Percentage(500), quoteCashback("Swiggy", PaymentMethod.CARD, listOf(swiggy.copy(percentage = Percentage(500))), saved, cardId = 2).percentage)
    }

    @Test
    fun `rounds cashback half up to the paisa`() {
        assertEquals(Money(1_234), CashbackBreakdown.calculate(Money(12_344), Percentage(1_000)).cashbackAmount)
        assertEquals(Money(1_235), CashbackBreakdown.calculate(Money(12_345), Percentage(1_000)).cashbackAmount)
        assertTrue(CashbackBreakdown.calculate(Money(12_345), Percentage(1_000)).let { it.cashbackAmount + it.effectiveAmount == it.originalAmount })
    }
}
