package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RemindersTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val settings = ReminderSettings(enabled = true, cardDaysBefore = 3, recurringDaysBefore = 1, udhaarAfterDays = 30)
    private val card = CreditCard(1, "Millennia", "HDFC", "1234", Money.rupees(50_000), 20, 10, "", true, uuid = "card-1")
    private val rahul = Person(1, "Rahul", uuid = "rahul")

    private fun netflix(next: LocalDate, reminderDays: Int? = null) = RecurringExpense(
        id = 1,
        title = "Netflix",
        amount = Money.rupees(649),
        category = "Subscription",
        paymentMethod = PaymentMethod.CARD,
        cardId = 1,
        schedule = RecurrenceSchedule(LocalDate.of(2026, 1, next.dayOfMonth), Frequency.MONTHLY),
        nextDate = next,
        occurrenceIndex = next.monthValue - 1,
        endDate = null,
        active = true,
        note = "",
        reminderDaysBefore = reminderDays,
        uuid = "netflix",
    )

    private fun due(
        cards: List<UpcomingCardPayment> = emptyList(),
        recurring: List<RecurringExpense> = emptyList(),
        people: List<PersonBalance> = emptyList(),
        custom: List<Reminder> = emptyList(),
        settings: ReminderSettings = this.settings,
    ) = ReminderPlanner.due(today, settings, cards, recurring, people, custom)

    @Test
    fun `card bills are reminded a few days before they are due`() {
        val dueSoon = UpcomingCardPayment(card, LocalDate.of(2026, 10, 10), Money.rupees(400), overdue = false)
        val reminder = due(cards = listOf(dueSoon)).single()

        assertEquals(ReminderKind.CARD_DUE, reminder.kind)
        assertEquals("card:card-1:2026-10-10", reminder.key)
        assertEquals("Millennia ••1234 bill due on 10 Oct", reminder.title)
        assertEquals("₹400 due on 10 Oct", reminder.text)

        val later = dueSoon.copy(dueDate = LocalDate.of(2026, 10, 15))
        assertTrue(due(cards = listOf(later)).isEmpty())
        assertEquals(1, due(cards = listOf(later.copy(card = card.copy(reminderDaysBefore = 7)))).size)
        assertEquals("Millennia ••1234 bill is overdue", due(cards = listOf(dueSoon.copy(dueDate = LocalDate.of(2026, 10, 5), overdue = true))).single().title)
    }

    @Test
    fun `recurring expenses are reminded before they are recorded`() {
        val tomorrow = due(recurring = listOf(netflix(LocalDate.of(2026, 10, 9)))).single()
        assertEquals("Netflix tomorrow", tomorrow.title)
        assertEquals("₹649 will be recorded on 9 Oct", tomorrow.text)

        assertTrue(due(recurring = listOf(netflix(LocalDate.of(2026, 10, 12)))).isEmpty())
        assertEquals(1, due(recurring = listOf(netflix(LocalDate.of(2026, 10, 12), reminderDays = 5))).size)
    }

    @Test
    fun `money owed for a long time is reminded`() {
        val owed = PersonBalance(
            rahul,
            LedgerSummary(totalDue = Money.rupees(1_500), totalPaid = Money.ZERO, borrowed = Money.ZERO),
            lastActivity = LocalDate.of(2026, 8, 1),
        )
        val reminder = due(people = listOf(owed)).single()

        assertEquals("Rahul owes you ₹1,500", reminder.title)
        assertEquals("No payment for 68 days", reminder.text)
        assertTrue(due(people = listOf(owed), settings = settings.copy(udhaarAfterDays = 0)).isEmpty())
        assertTrue(due(people = listOf(owed.copy(lastActivity = LocalDate.of(2026, 10, 1)))).isEmpty())
    }

    @Test
    fun `your own reminders are due on their date until done`() {
        val reminder = Reminder(1, "Ask Rahul for trip money", "", today, personId = 1, done = false, uuid = "r1")

        assertEquals("Due 8 Oct", due(custom = listOf(reminder)).single().text)
        assertTrue(due(custom = listOf(reminder.copy(done = true))).isEmpty())
        assertTrue(due(custom = listOf(reminder.copy(dueDate = today.plusDays(1)))).isEmpty())
    }

    @Test
    fun `nothing when reminders are turned off`() {
        val dueSoon = UpcomingCardPayment(card, LocalDate.of(2026, 10, 10), Money.rupees(400), overdue = false)

        assertTrue(due(cards = listOf(dueSoon), settings = settings.copy(enabled = false)).isEmpty())
    }
}
