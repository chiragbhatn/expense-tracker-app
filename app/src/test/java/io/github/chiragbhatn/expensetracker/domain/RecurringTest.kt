package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringTest {

    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    private fun recurring(schedule: RecurrenceSchedule, next: RecurringExpense.Occurrence, endDate: LocalDate? = null, active: Boolean = true) =
        RecurringExpense(
            id = 1,
            title = "Rent",
            amount = Money.rupees(15_000),
            category = "Rent",
            paymentMethod = PaymentMethod.UPI,
            cardId = null,
            schedule = schedule,
            nextDate = next.date,
            occurrenceIndex = next.index,
            endDate = endDate,
            active = active,
            note = "",
        )

    @Test
    fun `monthly on the 31st stays on month ends`() {
        val schedule = RecurrenceSchedule(date(1, 31), Frequency.MONTHLY)

        assertEquals(listOf(date(1, 31), date(2, 28), date(3, 31), date(4, 30)), (0..3).map(schedule::occurrence))
    }

    @Test
    fun `yearly on 29 February uses the 28th in other years`() {
        val schedule = RecurrenceSchedule(LocalDate.of(2024, 2, 29), Frequency.YEARLY)

        assertEquals(LocalDate.of(2025, 2, 28), schedule.occurrence(1))
        assertEquals(LocalDate.of(2028, 2, 29), schedule.occurrence(4))
    }

    @Test
    fun `daily, weekly and custom intervals`() {
        val start = date(10, 5)

        assertEquals(date(10, 8), RecurrenceSchedule(start, Frequency.DAILY).occurrence(3))
        assertEquals(date(10, 19), RecurrenceSchedule(start, Frequency.WEEKLY).occurrence(2))

        val fortnightly = RecurrenceSchedule(start, Frequency.CUSTOM, intervalCount = 2, intervalUnit = IntervalUnit.WEEKS)
        assertEquals(date(10, 19), fortnightly.occurrence(1))
        assertEquals("Every 2 weeks", fortnightly.describe())

        val quarterly = RecurrenceSchedule(start, Frequency.CUSTOM, intervalCount = 3, intervalUnit = IntervalUnit.MONTHS)
        assertEquals(LocalDate.of(2027, 1, 5), quarterly.occurrence(1))
        assertEquals("Every day", RecurrenceSchedule(start, Frequency.CUSTOM, 1, IntervalUnit.DAYS).describe())
        assertEquals("Monthly", RecurrenceSchedule(start, Frequency.MONTHLY).describe())
    }

    @Test
    fun `finds the first occurrence on or after a date`() {
        val schedule = RecurrenceSchedule(date(1, 31), Frequency.MONTHLY)

        assertEquals(0, schedule.firstIndexOnOrAfter(date(1, 1)))
        assertEquals(1, schedule.firstIndexOnOrAfter(date(2, 28)))
        assertEquals(2, schedule.firstIndexOnOrAfter(date(3, 1)))
        assertEquals(9, schedule.firstIndexOnOrAfter(date(10, 8)))
        assertEquals(500, RecurrenceSchedule(date(1, 1), Frequency.DAILY).firstIndexOnOrAfter(date(1, 1).plusDays(500)))
    }

    @Test
    fun `a new recurring expense starts at its next occurrence without back-filling`() {
        val schedule = RecurrenceSchedule(date(1, 31), Frequency.MONTHLY)

        assertEquals(RecurringExpense.Occurrence(9, date(10, 31)), RecurringExpense.firstPending(schedule, date(10, 8)))
        assertEquals(RecurringExpense.Occurrence(0, date(12, 1)), RecurringExpense.firstPending(RecurrenceSchedule(date(12, 1), Frequency.MONTHLY), date(10, 8)))
    }

    @Test
    fun `due occurrences are the ones not yet recorded`() {
        val schedule = RecurrenceSchedule(date(1, 31), Frequency.MONTHLY)
        val rent = recurring(schedule, RecurringExpense.Occurrence(1, date(2, 28)))

        assertEquals(
            listOf(RecurringExpense.Occurrence(1, date(2, 28)), RecurringExpense.Occurrence(2, date(3, 31))),
            rent.dueOccurrences(date(4, 5)),
        )
        assertEquals(emptyList<RecurringExpense.Occurrence>(), rent.dueOccurrences(date(2, 27)))
        assertEquals(emptyList<RecurringExpense.Occurrence>(), rent.copy(active = false).dueOccurrences(date(4, 5)))
    }

    @Test
    fun `nothing is due after the end date`() {
        val schedule = RecurrenceSchedule(date(1, 31), Frequency.MONTHLY)
        val rent = recurring(schedule, RecurringExpense.Occurrence(1, date(2, 28)), endDate = date(3, 15))

        assertEquals(listOf(RecurringExpense.Occurrence(1, date(2, 28))), rent.dueOccurrences(date(4, 5)))
        assertFalse(rent.isFinished)
        assertTrue(rent.copy(nextDate = date(3, 31), occurrenceIndex = 2).isFinished)
    }

    @Test
    fun `upcoming lists active items due soon, soonest first`() {
        val schedule = RecurrenceSchedule(date(1, 1), Frequency.MONTHLY)
        val soon = recurring(schedule, RecurringExpense.Occurrence(9, date(10, 10))).copy(id = 1)
        val later = recurring(schedule, RecurringExpense.Occurrence(9, date(10, 9))).copy(id = 2)
        val far = recurring(schedule, RecurringExpense.Occurrence(11, date(12, 1))).copy(id = 3)
        val paused = soon.copy(id = 4, active = false)

        assertEquals(listOf(2L, 1L), listOf(soon, later, far, paused).upcoming(date(10, 8), withinDays = 30).map { it.id })
    }
}
