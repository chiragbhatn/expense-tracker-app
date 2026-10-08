package io.github.chiragbhatn.expensetracker.domain

/** A person's part of an expense: what they owe you for it. */
data class Share(val personId: Long, val amount: Money)

/**
 * How an expense's effective amount (after cashback) is divided between you
 * and the people it was for. The parts must add up to the effective amount.
 */
data class Split(val myShare: Money, val shares: List<Share>) {
    val allocated: Money get() = myShare + shares.sumMoney { it.amount }

    /** Problems that prevent saving, or an empty list when the split is valid. */
    fun problems(effective: Money): List<SplitProblem> = buildList {
        if (myShare.isNegative || shares.any { it.amount.isNegative }) add(SplitProblem.NegativeShare)
        if (shares.map { it.personId }.toSet().size != shares.size) add(SplitProblem.DuplicatePerson)
        if (allocated != effective) add(SplitProblem.Mismatch(allocated = allocated, effective = effective))
    }

    fun isValidFor(effective: Money) = problems(effective).isEmpty()

    companion object {
        /** The whole expense is yours. */
        fun mine(effective: Money) = Split(myShare = effective, shares = emptyList())

        /** One person owes the whole effective amount — e.g. you paid for Rahul. */
        fun forPerson(personId: Long, effective: Money) = Split(Money.ZERO, listOf(Share(personId, effective)))

        /**
         * Divides [effective] equally between you (if [includeMe]) and [personIds].
         * Leftover paise go to the first participants, so the parts always add up.
         */
        fun equal(effective: Money, includeMe: Boolean, personIds: List<Long>): Split {
            val count = personIds.size + if (includeMe) 1 else 0
            if (count == 0) return mine(effective)
            val parts = effective.splitEvenly(count)
            return if (includeMe) {
                Split(parts.first(), personIds.zip(parts.drop(1)) { id, amount -> Share(id, amount) })
            } else {
                Split(Money.ZERO, personIds.zip(parts) { id, amount -> Share(id, amount) })
            }
        }
    }
}

sealed interface SplitProblem {
    data object NegativeShare : SplitProblem
    data object DuplicatePerson : SplitProblem
    data class Mismatch(val allocated: Money, val effective: Money) : SplitProblem {
        /** Positive when more is still to be allocated, negative when over-allocated. */
        val remaining: Money get() = effective - allocated
    }
}
