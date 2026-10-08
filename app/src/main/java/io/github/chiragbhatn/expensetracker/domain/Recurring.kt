package io.github.chiragbhatn.expensetracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Stored by name, so constants must not be renamed.
enum class Frequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    CUSTOM("Custom"),
}

enum class IntervalUnit(val label: String) {
    DAYS("days"),
    WEEKS("weeks"),
    MONTHS("months"),
    YEARS("years"),
}

/**
 * When a recurring expense happens. Each occurrence is computed from the start
 * date, so a schedule starting on the 31st stays on month ends (31 Jan,
 * 28 Feb, 31 Mar) instead of drifting.
 */
data class RecurrenceSchedule(
    val start: LocalDate,
    val frequency: Frequency,
    /** For [Frequency.CUSTOM]: repeat every [intervalCount] [intervalUnit]. */
    val intervalCount: Int = 1,
    val intervalUnit: IntervalUnit = IntervalUnit.MONTHS,
) {
    /** The [index]-th occurrence; 0 is the start date. */
    fun occurrence(index: Int): LocalDate {
        val n = index.toLong()
        return when (frequency) {
            Frequency.DAILY -> start.plusDays(n)
            Frequency.WEEKLY -> start.plusWeeks(n)
            Frequency.MONTHLY -> start.plusMonths(n)
            Frequency.YEARLY -> start.plusYears(n)
            Frequency.CUSTOM -> {
                val steps = intervalCount.coerceAtLeast(1) * n
                when (intervalUnit) {
                    IntervalUnit.DAYS -> start.plusDays(steps)
                    IntervalUnit.WEEKS -> start.plusWeeks(steps)
                    IntervalUnit.MONTHS -> start.plusMonths(steps)
                    IntervalUnit.YEARS -> start.plusYears(steps)
                }
            }
        }
    }

    /** Index of the first occurrence on or after [date]. */
    fun firstIndexOnOrAfter(date: LocalDate): Int {
        if (!date.isAfter(start)) return 0
        // Jump close using the average step, then walk to the exact occurrence.
        val days = ChronoUnit.DAYS.between(start, date)
        var index = (days / approximateDaysPerStep()).toInt().coerceAtLeast(0)
        while (index > 0 && !occurrence(index - 1).isBefore(date)) index--
        while (occurrence(index).isBefore(date)) index++
        return index
    }

    fun describe(): String = when (frequency) {
        Frequency.CUSTOM -> if (intervalCount <= 1) "Every ${intervalUnit.label.removeSuffix("s")}" else "Every $intervalCount ${intervalUnit.label}"
        else -> frequency.label
    }

    private fun approximateDaysPerStep(): Long {
        val unitDays = when (frequency) {
            Frequency.DAILY -> 1L
            Frequency.WEEKLY -> 7L
            Frequency.MONTHLY -> 31L
            Frequency.YEARLY -> 366L
            Frequency.CUSTOM -> when (intervalUnit) {
                IntervalUnit.DAYS -> 1L
                IntervalUnit.WEEKS -> 7L
                IntervalUnit.MONTHS -> 31L
                IntervalUnit.YEARS -> 366L
            } * intervalCount.coerceAtLeast(1)
        }
        return unitDays
    }
}

data class RecurringExpense(
    val id: Long,
    /** What the expense is for; recorded as the merchant, e.g. "Netflix". */
    val title: String,
    val amount: Money,
    val category: String,
    val paymentMethod: PaymentMethod,
    val cardId: Long?,
    val schedule: RecurrenceSchedule,
    /** The next occurrence that has not been recorded yet. */
    val nextDate: LocalDate,
    /** Position of [nextDate] in the schedule. */
    val occurrenceIndex: Int,
    val endDate: LocalDate?,
    val active: Boolean,
    val note: String,
    /** Days before each occurrence to remind you; null for no reminder. */
    val reminderDaysBefore: Int? = null,
    val uuid: String = "",
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    /** True once the end date has passed. */
    val isFinished: Boolean get() = endDate != null && nextDate.isAfter(endDate)

    /** Occurrences due on or before [today] that have not been recorded, oldest first. */
    fun dueOccurrences(today: LocalDate, limit: Int = 400): List<Occurrence> {
        if (!active) return emptyList()
        val due = mutableListOf<Occurrence>()
        var index = occurrenceIndex
        var date = nextDate
        while (!date.isAfter(today) && (endDate == null || !date.isAfter(endDate)) && due.size < limit) {
            due += Occurrence(index, date)
            index++
            date = schedule.occurrence(index)
        }
        return due
    }

    data class Occurrence(val index: Int, val date: LocalDate)

    companion object {
        /**
         * Where a new or rescheduled recurring expense starts: its first
         * occurrence on or after [today]. Past occurrences are not back-filled.
         */
        fun firstPending(schedule: RecurrenceSchedule, today: LocalDate): Occurrence {
            val index = schedule.firstIndexOnOrAfter(today)
            return Occurrence(index, schedule.occurrence(index))
        }
    }
}

/** Active recurring expenses whose next occurrence is within [withinDays] days, soonest first. */
fun List<RecurringExpense>.upcoming(today: LocalDate, withinDays: Long = 30): List<RecurringExpense> =
    filter { it.active && !it.isFinished && !it.nextDate.isBefore(today) && !it.nextDate.isAfter(today.plusDays(withinDays)) }
        .sortedBy { it.nextDate }
