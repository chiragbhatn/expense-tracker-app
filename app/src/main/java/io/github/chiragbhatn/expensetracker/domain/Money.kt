package io.github.chiragbhatn.expensetracker.domain

import kotlin.math.absoluteValue

/**
 * An amount of money held as whole paise (hundredths of the currency unit) so
 * that sums, splits and cashback calculations are exact. Floating point is
 * never used for amounts.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

    val isZero: Boolean get() = paise == 0L
    val isPositive: Boolean get() = paise > 0L
    val isNegative: Boolean get() = paise < 0L

    operator fun plus(other: Money) = Money(paise + other.paise)

    operator fun minus(other: Money) = Money(paise - other.paise)

    operator fun unaryMinus() = Money(-paise)

    override fun compareTo(other: Money) = paise.compareTo(other.paise)

    fun abs() = Money(paise.absoluteValue)

    /**
     * "₹1,00,000", "₹33.30", "−₹50": the currency's symbol and digit grouping,
     * with paise only when non-zero.
     */
    fun format(currency: Currency = Currency.display): String {
        val sign = if (paise < 0) "−" else ""
        val abs = paise.absoluteValue
        val whole = if (currency.indianGrouping) groupIndian(abs / 100) else groupThousands(abs / 100)
        val fraction = abs % 100
        val number = if (fraction == 0L) whole else "$whole.${fraction.toString().padStart(2, '0')}"
        return "$sign${currency.symbol}$number"
    }

    /** Plain digits for pre-filling an input field: "1000", "33.30". */
    fun toInputString(): String {
        val rupees = paise / 100
        val fraction = paise % 100
        return if (fraction == 0L) "$rupees" else "$rupees.${fraction.toString().padStart(2, '0')}"
    }

    /** Always two decimals, no symbol or grouping, for files: "1000.00", "-50.25". */
    fun toPlainString(): String {
        val sign = if (paise < 0) "-" else ""
        val abs = paise.absoluteValue
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /**
     * Divides this amount into [parts] amounts that differ by at most one paisa
     * and add up exactly to this amount; the first parts get the leftover paise.
     */
    fun splitEvenly(parts: Int): List<Money> {
        require(parts > 0) { "parts must be positive" }
        require(!isNegative) { "Only non-negative amounts can be split" }
        val base = paise / parts
        val remainder = (paise % parts).toInt()
        return List(parts) { index -> Money(base + if (index < remainder) 1 else 0) }
    }

    companion object {
        val ZERO = Money(0)

        // 99,99,99,999.99: far above any real expense, and small enough that
        // amount × basis points can never overflow a Long.
        private const val MAX_INPUT_PAISE = 99_99_99_999_99L

        private val AMOUNT = Regex("""^(\d*)(?:\.(\d{0,2}))?$""")
        private val PARTIAL_AMOUNT = Regex("""^\d{0,10}(?:\.\d{0,2})?$""")
        private val SYMBOLS = Regex("""[₹$€£]|rs\.?|inr""", RegexOption.IGNORE_CASE)

        fun rupees(rupees: Long) = Money(rupees * 100)

        /**
         * Parses what the user typed ("1000", "1,000.50", "₹750"). Returns null
         * for anything that is not a non-negative amount with at most two decimals.
         */
        fun parse(input: String): Money? {
            val cleaned = input.replace(",", "").replace(SYMBOLS, "").trim()
            val match = AMOUNT.matchEntire(cleaned) ?: return null
            val (whole, fraction) = match.destructured
            if (whole.isEmpty() && fraction.isEmpty()) return null
            if (whole.length > 11) return null
            val paise = whole.ifEmpty { "0" }.toLong() * 100 + fraction.padEnd(2, '0').toLong()
            return if (paise > MAX_INPUT_PAISE) null else Money(paise)
        }

        /** Whether [text] is acceptable while the user is still typing an amount. */
        fun isPartialInput(text: String): Boolean = PARTIAL_AMOUNT.matches(text)
    }
}

inline fun <T> Iterable<T>.sumMoney(selector: (T) -> Money): Money =
    fold(Money.ZERO) { total, item -> total + selector(item) }

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { total, amount -> total + amount }

// Indian grouping: the last three digits, then pairs — 1,23,45,678.
private fun groupIndian(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    val pairs = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "$pairs,${digits.takeLast(3)}"
}

// International grouping: 12,345,678.
private fun groupThousands(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
