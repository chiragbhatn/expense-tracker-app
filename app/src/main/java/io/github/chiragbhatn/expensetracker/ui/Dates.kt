package io.github.chiragbhatn.expensetracker.ui

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val shortDateWithYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** "Mon, 28 Sep 2026" */
fun LocalDate.formatLong(): String = format(longDate)

/** "28 Sep", with the year added when it is not this year. */
fun LocalDate.formatShort(): String =
    if (year == LocalDate.now().year) format(shortDate) else format(shortDateWithYear)

/** "September 2026" */
fun YearMonth.formatMonth(): String = format(monthYear)

// Material date pickers work in UTC milliseconds.
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
