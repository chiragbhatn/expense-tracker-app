package io.github.chiragbhatn.expensetracker.domain

/**
 * A percentage with up to two decimal places (10%, 1.5%, 2.25%), held as
 * basis points: 1% = 100 bps.
 */
@JvmInline
value class Percentage(val basisPoints: Int) : Comparable<Percentage> {

    val isZero: Boolean get() = basisPoints == 0

    /** This percentage of [amount], rounded half-up to the nearest paisa. */
    fun of(amount: Money): Money {
        require(!amount.isNegative) { "Percentages are only taken of non-negative amounts" }
        return Money((amount.paise * basisPoints + 5_000) / 10_000)
    }

    /** "10%", "1.5%", "2.25%" */
    fun format(): String = "${toInputString()}%"

    /** "10", "1.5", "2.25" */
    fun toInputString(): String {
        val whole = basisPoints / 100
        val fraction = basisPoints % 100
        return when {
            fraction == 0 -> "$whole"
            fraction % 10 == 0 -> "$whole.${fraction / 10}"
            else -> "$whole.${fraction.toString().padStart(2, '0')}"
        }
    }

    override fun compareTo(other: Percentage) = basisPoints.compareTo(other.basisPoints)

    companion object {
        val ZERO = Percentage(0)

        private const val MAX_BASIS_POINTS = 10_000
        private val RATE = Regex("""^(\d{1,3})(?:\.(\d{0,2}))?$""")
        private val PARTIAL_RATE = Regex("""^\d{0,3}(?:\.\d{0,2})?$""")

        /**
         * Parses a cashback rate such as "10", "1.5" or "2.25%". Returns null
         * unless it is above 0 and at most 100, with at most two decimals.
         */
        fun parse(input: String): Percentage? {
            val match = RATE.matchEntire(input.trim().removeSuffix("%").trim()) ?: return null
            val (whole, fraction) = match.destructured
            val basisPoints = whole.toInt() * 100 + fraction.padEnd(2, '0').toInt()
            return if (basisPoints in 1..MAX_BASIS_POINTS) Percentage(basisPoints) else null
        }

        /** Whether [text] is acceptable while the user is still typing a rate. */
        fun isPartialInput(text: String): Boolean = PARTIAL_RATE.matches(text)
    }
}
