package io.github.chiragbhatn.expensetracker.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/** What could be read from a receipt. Anything not found is null; the user always reviews it before saving. */
data class ReceiptDraft(
    val merchant: String?,
    val amount: Money?,
    val date: LocalDate?,
    val text: String,
) {
    val isEmpty: Boolean get() = merchant == null && amount == null && date == null
}

/**
 * Pulls the merchant, total and date out of text recognised from a receipt
 * photo. Recognition is imperfect, so this only proposes values.
 */
object ReceiptParser {
    private val AMOUNT = Regex("""(?<![\d.,])(?:(?:₹|rs\.?|inr)\s*)?(\d{1,3}(?:,\d{2,3})+|\d+)(?:\.(\d{1,2}))?(?![\d,]*\d)""", RegexOption.IGNORE_CASE)
    private val CURRENCY = Regex("""₹|\brs\.?|\binr\b""", RegexOption.IGNORE_CASE)

    // Strongest first: "grand total" beats a plain "total".
    private val TOTAL_KEYWORDS = listOf(
        "grand total", "net amount", "amount payable", "net payable", "total payable", "total amount",
        "bill amount", "amount due", "balance due", "to pay", "total",
    )
    private val NOT_TOTAL = Regex(
        """\b(sub ?-?total|qty|quantity|items?|savings?|saved|discount|round(ing)? ?off|tip|change|tendered|cgst|sgst|igst|cess)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TAX = Regex("""\b(tax|taxes|gst|vat)\b""", RegexOption.IGNORE_CASE)
    private val INCLUSIVE = Regex("""\bincl""", RegexOption.IGNORE_CASE)

    private val NUMERIC_DATE = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})\b""")
    private val ISO_DATE = Regex("""\b(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})\b""")
    private val MONTH_NAMES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val DAY_MONTH_YEAR = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s\-/.]*([A-Za-z]{3,9})[\s\-/.,']*(\d{2,4})\b""")
    private val MONTH_DAY_YEAR = Regex("""\b([A-Za-z]{3,9})[\s\-.]*(\d{1,2})(?:st|nd|rd|th)?,?[\s\-/.]+(\d{4})\b""")

    private val NOT_MERCHANT = Regex(
        """\b(tax|invoice|receipt|bill|gst|gstin|phone|ph|tel|mob|mobile|date|time|order|cash memo|welcome|address|table|""" +
            """customer|cashier|token|fssai|cin|thank|thanks)\b|www\.|https?:|@""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String, knownMerchants: List<String> = emptyList(), today: LocalDate = LocalDate.now()): ReceiptDraft {
        val lines = text.lines().map { cleanName(it) }.filter { it.isNotEmpty() }
        return ReceiptDraft(
            merchant = findMerchant(lines, knownMerchants),
            amount = findTotal(lines),
            date = findDate(lines, today),
            text = text,
        )
    }

    fun findTotal(lines: List<String>): Money? {
        for (keyword in TOTAL_KEYWORDS) {
            val candidates = lines.indices.flatMap { index ->
                val line = lines[index]
                val lower = line.lowercase(Locale.ROOT)
                val notTheTotal = NOT_TOTAL.containsMatchIn(line) || (TAX.containsMatchIn(line) && !INCLUSIVE.containsMatchIn(line))
                if (keyword !in lower || notTheTotal) return@flatMap emptyList()
                // The amount is usually on the same line, sometimes on the next one.
                amountsIn(lines[index]).ifEmpty { lines.getOrNull(index + 1)?.let(::amountsIn).orEmpty() }
            }
            candidates.maxOrNull()?.let { return it }
        }
        // No total line: the largest amount written with paise or a currency sign.
        return lines.flatMap { line -> amountsIn(line, requireMoneyLike = true) }.maxOrNull()
    }

    private fun amountsIn(line: String, requireMoneyLike: Boolean = false): List<Money> {
        if (looksLikeDateOrPhone(line) && !CURRENCY.containsMatchIn(line)) return emptyList()
        return AMOUNT.findAll(line).mapNotNull { match ->
            val hasPaise = match.groupValues[2].isNotEmpty()
            val hasSymbol = CURRENCY.containsMatchIn(match.value)
            if (requireMoneyLike && !hasPaise && !hasSymbol) return@mapNotNull null
            Money.parse(match.groupValues[1] + if (hasPaise) "." + match.groupValues[2] else "")
                ?.takeIf { it.isPositive }
        }.toList()
    }

    private fun looksLikeDateOrPhone(line: String): Boolean =
        NUMERIC_DATE.containsMatchIn(line) || ISO_DATE.containsMatchIn(line) || Regex("""\d{10}""").containsMatchIn(line.replace(" ", ""))

    fun findDate(lines: List<String>, today: LocalDate): LocalDate? {
        for (line in lines) {
            dateIn(line)?.takeIf { it.year >= 2000 && !it.isAfter(today.plusDays(1)) }?.let { return it }
        }
        return null
    }

    private fun dateIn(line: String): LocalDate? {
        ISO_DATE.find(line)?.let { match ->
            val (year, month, day) = match.destructured
            date(year.toInt(), month.toInt(), day.toInt())?.let { return it }
        }
        NUMERIC_DATE.find(line)?.let { match ->
            val (first, second, yearText) = match.destructured
            val year = fullYear(yearText)
            // Indian receipts write day first; fall back to month first when that is impossible.
            (date(year, second.toInt(), first.toInt()) ?: date(year, first.toInt(), second.toInt()))?.let { return it }
        }
        DAY_MONTH_YEAR.findAll(line).forEach { match ->
            val (day, monthName, yearText) = match.destructured
            val month = monthNumber(monthName) ?: return@forEach
            date(fullYear(yearText), month, day.toInt())?.let { return it }
        }
        MONTH_DAY_YEAR.findAll(line).forEach { match ->
            val (monthName, day, year) = match.destructured
            val month = monthNumber(monthName) ?: return@forEach
            date(year.toInt(), month, day.toInt())?.let { return it }
        }
        return null
    }

    private fun monthNumber(name: String): Int? {
        val lower = name.lowercase(Locale.ROOT)
        if (lower.length > 3 && lower != "sept" && java.time.Month.entries.none { it.name.lowercase(Locale.ROOT).startsWith(lower) }) return null
        val index = MONTH_NAMES.indexOf(lower.take(3))
        return if (index >= 0) index + 1 else null
    }

    private fun fullYear(text: String): Int = text.toInt().let { if (text.length == 2) 2000 + it else it }

    private fun date(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    fun findMerchant(lines: List<String>, knownMerchants: List<String>): String? {
        val text = lines.joinToString("\n").lowercase(Locale.ROOT)
        knownMerchants
            .map(::cleanName)
            .filter { it.length >= 3 }
            .sortedByDescending { it.length }
            .firstOrNull { merchant -> Regex("""\b${Regex.escape(merchant.lowercase(Locale.ROOT))}\b""").containsMatchIn(text) }
            ?.let { return it }
        return lines.take(6).firstOrNull { line ->
            line.count { it.isLetter() } >= 3 &&
                line.count { it.isDigit() } <= line.length / 3 &&
                !NOT_MERCHANT.containsMatchIn(line)
        }?.let(::tidyName)
    }

    /** "DMART  READY." → "Dmart Ready" */
    private fun tidyName(line: String): String {
        val trimmed = cleanName(line.trim { !it.isLetterOrDigit() })
        val shouting = trimmed.none { it.isLowerCase() }
        return if (!shouting) trimmed else trimmed.lowercase(Locale.ROOT).split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }
}
