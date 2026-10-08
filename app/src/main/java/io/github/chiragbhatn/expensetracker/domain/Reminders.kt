package io.github.chiragbhatn.expensetracker.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A reminder you set yourself, optionally about a person: "Ask Rahul for the trip money". */
data class Reminder(
    val id: Long,
    val title: String,
    val note: String,
    val dueDate: LocalDate,
    val personId: Long?,
    val done: Boolean,
    val uuid: String = "",
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

data class ReminderSettings(
    /** Whether the daily check may post notifications at all. */
    val enabled: Boolean = true,
    /** Days before a card bill's due date, for cards without their own setting. */
    val cardDaysBefore: Int = 3,
    /** Days before a recurring expense, for ones without their own setting. */
    val recurringDaysBefore: Int = 1,
    /** Remind about money owed to you after this many days without activity; 0 turns it off. */
    val udhaarAfterDays: Int = 30,
)

enum class ReminderKind { CARD_DUE, RECURRING, UDHAAR, CUSTOM }

/** A notification that is due. [key] identifies it so each one is shown only once. */
data class DueReminder(val key: String, val kind: ReminderKind, val title: String, val text: String, val date: LocalDate)

object ReminderPlanner {
    private val day = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun due(
        today: LocalDate,
        settings: ReminderSettings,
        cardPayments: List<UpcomingCardPayment>,
        recurring: List<RecurringExpense>,
        people: List<PersonBalance>,
        custom: List<Reminder>,
    ): List<DueReminder> {
        if (!settings.enabled) return emptyList()
        val reminders = mutableListOf<DueReminder>()

        cardPayments.forEach { payment ->
            val daysBefore = (payment.card.reminderDaysBefore ?: settings.cardDaysBefore).coerceAtLeast(0)
            if (payment.overdue || !today.isBefore(payment.dueDate.minusDays(daysBefore.toLong()))) {
                reminders += DueReminder(
                    key = "card:${payment.card.uuid.ifEmpty { payment.card.id.toString() }}:${payment.dueDate}",
                    kind = ReminderKind.CARD_DUE,
                    title = if (payment.overdue) "${payment.card.displayName} bill is overdue" else "${payment.card.displayName} bill due ${whenText(payment.dueDate, today)}",
                    text = "${payment.amount.format()} due on ${payment.dueDate.format(day)}",
                    date = payment.dueDate,
                )
            }
        }

        recurring.filter { it.active && !it.isFinished }.forEach { item ->
            val daysBefore = (item.reminderDaysBefore ?: settings.recurringDaysBefore).coerceAtLeast(0)
            val next = item.nextDate
            if (!next.isBefore(today) && !today.isBefore(next.minusDays(daysBefore.toLong()))) {
                reminders += DueReminder(
                    key = "recurring:${item.uuid.ifEmpty { item.id.toString() }}:$next",
                    kind = ReminderKind.RECURRING,
                    title = "${item.title} ${whenText(next, today)}",
                    text = "${item.amount.format()} will be recorded on ${next.format(day)}",
                    date = next,
                )
            }
        }

        if (settings.udhaarAfterDays > 0) {
            people.overdue(today, settings.udhaarAfterDays.toLong()).forEach { balance ->
                val last = balance.lastActivity ?: return@forEach
                reminders += DueReminder(
                    key = "udhaar:${balance.person.uuid.ifEmpty { balance.person.id.toString() }}:$last",
                    kind = ReminderKind.UDHAAR,
                    title = "${balance.person.name} owes you ${balance.summary.receivable.format()}",
                    text = "No payment for ${ChronoUnit.DAYS.between(last, today)} days",
                    date = today,
                )
            }
        }

        custom.filter { !it.done && !it.dueDate.isAfter(today) }.forEach { reminder ->
            reminders += DueReminder(
                key = "custom:${reminder.uuid.ifEmpty { reminder.id.toString() }}",
                kind = ReminderKind.CUSTOM,
                title = reminder.title,
                text = reminder.note.ifBlank { "Due ${reminder.dueDate.format(day)}" },
                date = reminder.dueDate,
            )
        }
        return reminders.sortedBy { it.date }
    }

    private fun whenText(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "today"
        1L -> "tomorrow"
        else -> "on ${date.format(day)}"
    }
}
