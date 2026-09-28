package io.github.chiragbhatn.expensetracker.domain

/** A user-configurable merchant cashback rule, e.g. Swiggy 10% (on). */
data class CashbackRule(
    val id: Long,
    val merchant: String,
    val percentage: Percentage,
    val enabled: Boolean,
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

fun List<CashbackRule>.forMerchant(merchant: String): CashbackRule? {
    val key = nameKey(merchant)
    return if (key.isEmpty()) null else firstOrNull { nameKey(it.merchant) == key }
}

/** Whether a transaction earns cashback and, if not, why. */
sealed interface CashbackEligibility {
    data class Eligible(val rule: CashbackRule) : CashbackEligibility
    data class RuleDisabled(val rule: CashbackRule) : CashbackEligibility
    data class NotPaidByCard(val rule: CashbackRule) : CashbackEligibility
    data object NoRule : CashbackEligibility
}

fun cashbackEligibility(
    merchant: String,
    paymentMethod: PaymentMethod,
    rules: List<CashbackRule>,
): CashbackEligibility {
    val rule = rules.forMerchant(merchant) ?: return CashbackEligibility.NoRule
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
 * with, so changing a rule never rewrites history, unless its merchant or
 * payment method changes.
 */
fun quoteCashback(
    merchant: String,
    paymentMethod: PaymentMethod,
    rules: List<CashbackRule>,
    saved: SavedCashback?,
): CashbackQuote {
    val eligibility = cashbackEligibility(merchant, paymentMethod, rules)
    val rulePercentage = (eligibility as? CashbackEligibility.Eligible)?.rule?.percentage ?: Percentage.ZERO
    val keepSaved = saved != null &&
        saved.paymentMethod == paymentMethod &&
        nameKey(saved.merchant) == nameKey(merchant)
    return CashbackQuote(
        eligibility = eligibility,
        percentage = if (keepSaved) saved!!.percentage else rulePercentage,
        rulePercentage = rulePercentage,
    )
}
