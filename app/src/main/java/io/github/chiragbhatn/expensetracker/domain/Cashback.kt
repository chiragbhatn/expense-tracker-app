package io.github.chiragbhatn.expensetracker.domain

/** A user-configurable merchant cashback rule, e.g. Swiggy 10% (on). */
data class CashbackRule(
    val id: Long,
    val merchant: String,
    val percentage: Percentage,
    val enabled: Boolean,
    /** Limits the rule to one credit card; null means any card. */
    val cardId: Long? = null,
    val uuid: String = "",
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

/**
 * The expense side of a transaction. Cashback is subtracted from the amount
 * charged, never added:
 *
 *     cashback  = original × percentage / 100
 *     effective = original − cashback
 */
data class CashbackBreakdown(
    val originalAmount: Money,
    val cashbackPercentage: Percentage,
    val cashbackAmount: Money,
    val effectiveAmount: Money,
) {
    val hasCashback: Boolean get() = cashbackAmount.isPositive

    companion object {
        fun calculate(originalAmount: Money, cashbackPercentage: Percentage): CashbackBreakdown {
            val cashback = cashbackPercentage.of(originalAmount)
            return CashbackBreakdown(
                originalAmount = originalAmount,
                cashbackPercentage = cashbackPercentage,
                cashbackAmount = cashback,
                effectiveAmount = originalAmount - cashback,
            )
        }
    }
}

/** The rule for [merchant] paid with [cardId]: a rule for that card wins over one for any card. */
fun List<CashbackRule>.forMerchant(merchant: String, cardId: Long? = null): CashbackRule? {
    val matching = matchingMerchant(merchant)
    return matching.firstOrNull { it.cardId != null && it.cardId == cardId } ?: matching.firstOrNull { it.cardId == null }
}

private fun List<CashbackRule>.matchingMerchant(merchant: String): List<CashbackRule> {
    val key = nameKey(merchant)
    return if (key.isEmpty()) emptyList() else filter { nameKey(it.merchant) == key }
}

/** Whether a transaction earns cashback and, if not, why. */
sealed interface CashbackEligibility {
    data class Eligible(val rule: CashbackRule) : CashbackEligibility
    data class RuleDisabled(val rule: CashbackRule) : CashbackEligibility
    data class NotPaidByCard(val rule: CashbackRule) : CashbackEligibility

    /** The merchant only has rules for other cards. */
    data class OtherCard(val rule: CashbackRule) : CashbackEligibility
    data object NoRule : CashbackEligibility
}

fun cashbackEligibility(
    merchant: String,
    paymentMethod: PaymentMethod,
    rules: List<CashbackRule>,
    cardId: Long? = null,
): CashbackEligibility {
    val matching = rules.matchingMerchant(merchant)
    if (matching.isEmpty()) return CashbackEligibility.NoRule
    val rule = rules.forMerchant(merchant, cardId) ?: return CashbackEligibility.OtherCard(matching.first())
    return when {
        !rule.enabled -> CashbackEligibility.RuleDisabled(rule)
        !paymentMethod.earnsCashback -> CashbackEligibility.NotPaidByCard(rule)
        else -> CashbackEligibility.Eligible(rule)
    }
}

/** The cashback an existing transaction was saved with. */
data class SavedCashback(
    val merchant: String,
    val paymentMethod: PaymentMethod,
    val percentage: Percentage,
    val cardId: Long? = null,
)

data class CashbackQuote(
    val eligibility: CashbackEligibility,
    /** The percentage applied to this transaction. */
    val percentage: Percentage,
    /** What the current rules give. Differs from [percentage] only for a transaction saved under an older rule. */
    val rulePercentage: Percentage,
)

/**
 * Picks the cashback percentage for the expense form. New transactions follow
 * the current rules. An edited transaction keeps the percentage it was saved
 * with, so changing a rule never rewrites history, unless its merchant,
 * payment method or card changes.
 */
fun quoteCashback(
    merchant: String,
    paymentMethod: PaymentMethod,
    rules: List<CashbackRule>,
    saved: SavedCashback?,
    cardId: Long? = null,
): CashbackQuote {
    val eligibility = cashbackEligibility(merchant, paymentMethod, rules, cardId)
    val rulePercentage = (eligibility as? CashbackEligibility.Eligible)?.rule?.percentage ?: Percentage.ZERO
    val keepSaved = saved != null &&
        saved.paymentMethod == paymentMethod &&
        saved.cardId == cardId &&
        nameKey(saved.merchant) == nameKey(merchant)
    return CashbackQuote(
        eligibility = eligibility,
        percentage = if (keepSaved) saved!!.percentage else rulePercentage,
        rulePercentage = rulePercentage,
    )
}
