package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PercentageTest {

    @Test
    fun `parses cashback rates`() {
        assertEquals(Percentage(1_000), Percentage.parse("10"))
        assertEquals(Percentage(150), Percentage.parse("1.5"))
        assertEquals(Percentage(225), Percentage.parse("2.25"))
        assertEquals(Percentage(1_000), Percentage.parse("10%"))
        assertEquals(Percentage(10_000), Percentage.parse("100"))
    }

    @Test
    fun `rejects rates outside 0 to 100`() {
        assertNull(Percentage.parse("0"))
        assertNull(Percentage.parse("100.01"))
        assertNull(Percentage.parse("1.234"))
        assertNull(Percentage.parse("abc"))
        assertNull(Percentage.parse(""))
    }

    @Test
    fun `formats without trailing zeros`() {
        assertEquals("10%", Percentage(1_000).format())
        assertEquals("1.5%", Percentage(150).format())
        assertEquals("2.25%", Percentage(225).format())
        assertEquals("2.05%", Percentage(205).format())
    }

    @Test
    fun `takes a percentage rounded half-up to the paisa`() {
        assertEquals(Money.rupees(50), Percentage(1_000).of(Money.rupees(500)))
        assertEquals(Money(3_333), Percentage(1_000).of(Money(33_333)))
        assertEquals(Money(1), Percentage(1_000).of(Money(5)))
        assertEquals(Money(500), Percentage(250).of(Money(19_999)))
    }
}
