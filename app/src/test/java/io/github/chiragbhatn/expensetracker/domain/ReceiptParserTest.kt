package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {

    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun `supermarket bill with taxes uses the grand total`() {
        val text = """
            DMART READY
            Avenue Supermarts Ltd
            GSTIN: 27AABCA1234F1Z5
            Date: 05/10/2026 Time: 18:42
            Item        Qty   Amount
            Atta 5kg     1    245.00
            Milk 1L      2    120.00
            Sub Total         365.00
            CGST 2.5%           9.13
            SGST 2.5%           9.13
            Grand Total       383.26
            Paid by Card
        """.trimIndent()

        val draft = ReceiptParser.parse(text, today = today)

        assertEquals("Dmart Ready", draft.merchant)
        assertEquals(Money(38_326), draft.amount)
        assertEquals(LocalDate.of(2026, 10, 5), draft.date)
    }

    @Test
    fun `restaurant bill with rupee signs and a month name`() {
        val text = """
            Hotel Saravana Bhavan
            Bill No: 1042
            08-Oct-2026 13:05
            Masala Dosa x2 ₹180
            Filter Coffee x2 ₹80
            Total ₹260
            Thank you!
        """.trimIndent()

        val draft = ReceiptParser.parse(text, today = today)

        assertEquals("Hotel Saravana Bhavan", draft.merchant)
        assertEquals(Money.rupees(260), draft.amount)
        assertEquals(LocalDate.of(2026, 10, 8), draft.date)
    }

    @Test
    fun `known merchants are recognised and item totals ignored`() {
        val text = """
            Order from SWIGGY
            Order ID 123456789
            Item total 450.00
            Delivery fee 30.00
            To Pay 480.00
            Oct 7, 2026
        """.trimIndent()

        val draft = ReceiptParser.parse(text, knownMerchants = listOf("Zomato", "Swiggy"), today = today)

        assertEquals("Swiggy", draft.merchant)
        assertEquals(Money.rupees(480), draft.amount)
        assertEquals(LocalDate.of(2026, 10, 7), draft.date)
    }

    @Test
    fun `totals written on the next line and Indian digit grouping`() {
        val lines = listOf("Electronics World", "Total Amount Payable", "Rs. 1,24,999.00", "Ph: 98765 43210")

        assertEquals(Money(1_24_999_00), ReceiptParser.findTotal(lines))
    }

    @Test
    fun `tax totals are not the bill total`() {
        val lines = listOf("Total GST 18.26", "Total (incl. GST) 383.26")

        assertEquals(Money(38_326), ReceiptParser.findTotal(lines))
    }

    @Test
    fun `without a total line the largest amount wins`() {
        val lines = listOf("ABC Store", "2 x Pens 20.00", "1 x Book 150.50", "Call 9876543210")

        assertEquals(Money(15_050), ReceiptParser.findTotal(lines))
    }

    @Test
    fun `dates in other common formats`() {
        assertEquals(LocalDate.of(2026, 9, 30), ReceiptParser.findDate(listOf("2026-09-30 10:00"), today))
        assertEquals(LocalDate.of(2026, 9, 3), ReceiptParser.findDate(listOf("Dated 3rd September 2026"), today))
        assertEquals(LocalDate.of(2026, 1, 2), ReceiptParser.findDate(listOf("02.01.26"), today))
        // Day-first is impossible here, so it is read month-first.
        assertEquals(LocalDate.of(2026, 9, 25), ReceiptParser.findDate(listOf("09/25/2026"), today))
        // Future dates are recognition mistakes.
        assertNull(ReceiptParser.findDate(listOf("01/12/2026"), today))
    }

    @Test
    fun `unreadable text proposes nothing`() {
        val draft = ReceiptParser.parse("~~ ## ..", today = today)

        assertTrue(draft.isEmpty)
    }
}
