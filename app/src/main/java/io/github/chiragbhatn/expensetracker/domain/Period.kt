package io.github.chiragbhatn.expensetracker.domain

import java.time.YearMonth

sealed interface Period {
    data class Month(val month: YearMonth) : Period
    data object AllTime : Period

    /** Inclusive range of epoch days covered by this period. */
    val epochDays: LongRange
        get() = when (this) {
            is Month -> month.atDay(1).toEpochDay()..month.atEndOfMonth().toEpochDay()
            AllTime -> Long.MIN_VALUE..Long.MAX_VALUE
        }
}
