package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses what users type`() {
        assertEquals(Money(50_000), Money.parse("500"))
        assertEquals(Money(1_00_000), Money.parse("1,000"))
        assertEquals(Money(75_000), Money.parse(" ₹750 "))
        assertEquals(Money(3_330), Money.parse("33.3"))
        assertEquals(Money(3_330), Money.parse("33.30"))
        assertEquals(Money(50), Money.parse(".5"))
        assertEquals(Money(1_200), Money.parse("12."))
        assertEquals(Money.ZERO, Money.parse("0"))
    }

    @Test
    fun `rejects invalid amounts`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("."))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1000000000000"))
    }

    @Test
    fun `formats with Indian digit grouping`() {
        assertEquals("₹0", Money.ZERO.format())
        assertEquals("₹500", Money.rupees(500).format())
        assertEquals("₹1,000", Money.rupees(1_000).format())
        assertEquals("₹1,00,000", Money.rupees(1_00_000).format())
        assertEquals("₹1,23,45,678", Money.rupees(1_23_45_678).format())
        assertEquals("₹33.30", Money(3_330).format())
        assertEquals("₹0.05", Money(5).format())
        assertEquals("−₹50", (-Money.rupees(50)).format())
    }

    @Test
    fun `round-trips through the input field`() {
        assertEquals("1000", Money.rupees(1_000).toInputString())
        assertEquals("33.30", Money(3_330).toInputString())
        assertEquals(Money(3_330), Money.parse(Money(3_330).toInputString()))
    }

    @Test
    fun `accepts partial input while typing`() {
        assertTrue(Money.isPartialInput(""))
        assertTrue(Money.isPartialInput("12."))
        assertTrue(Money.isPartialInput("12.34"))
        assertFalse(Money.isPartialInput("12.345"))
        assertFalse(Money.isPartialInput("1a"))
        assertFalse(Money.isPartialInput("1.2.3"))
    }

    @Test
    fun `formats other currencies with international grouping`() {
        assertEquals("$1,234,567.50", Money(123_456_750).format(Currency.USD))
        assertEquals("€1,000", Money.rupees(1_000).format(Currency.EUR))
        assertEquals("AED 99", Money.rupees(99).format(Currency.AED))
        assertEquals(Currency.GBP, Currency.fromCode(" gbp "))
        assertEquals(Currency.INR, Currency.fromCode("XYZ"))
    }

    @Test
    fun `plain strings for files always have two decimals`() {
        assertEquals("1000.00", Money.rupees(1_000).toPlainString())
        assertEquals("0.05", Money(5).toPlainString())
        assertEquals("-50.25", Money(-5_025).toPlainString())
    }

    @Test
    fun `splits evenly without losing a paisa`() {
        assertEquals(listOf(Money(334), Money(333), Money(333)), Money(1_000).splitEvenly(3))
        assertEquals(List(3) { Money.rupees(300) }, Money.rupees(900).splitEvenly(3))
        assertEquals(Money(1_000), Money(1_000).splitEvenly(7).sum())
    }
}
