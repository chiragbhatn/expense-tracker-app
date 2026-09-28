package io.github.chiragbhatn.expensetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.CashbackEligibility
import io.github.chiragbhatn.expensetracker.domain.CashbackQuote
import io.github.chiragbhatn.expensetracker.domain.LedgerPosting
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors

/**
 * Shows both sides of a transaction as the user types:
 *
 *     Original amount      ₹1,000
 *     Cashback (10%)        −₹100
 *     Effective expense      ₹900
 *     Rahul's udhaar       ₹1,000
 */
@Composable
fun CashbackBreakdownCard(
    posting: LedgerPosting,
    quote: CashbackQuote,
    merchant: String,
    paidFor: Person?,
    onUseCurrentRule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amounts = posting.expense
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountLine(
                label = "Original amount",
                amount = amounts.originalAmount.format(),
                amountTag = TestTags.BREAKDOWN_ORIGINAL,
            )
            if (!amounts.cashbackPercentage.isZero) {
                AmountLine(
                    label = "Cashback (${amounts.cashbackPercentage.format()})",
                    amount = (-amounts.cashbackAmount).format(),
                    amountTag = TestTags.BREAKDOWN_CASHBACK,
                    color = LocalAmountColors.current.positive,
                )
            }
            HorizontalDivider()
            AmountLine(
                label = "Effective expense",
                amount = amounts.effectiveAmount.format(),
                amountTag = TestTags.BREAKDOWN_EFFECTIVE,
                emphasized = true,
            )

            if (quote.percentage != quote.rulePercentage) {
                val current = if (quote.rulePercentage.isZero) "none" else quote.rulePercentage.format()
                Text(
                    text = "Saved with ${quote.percentage.format()} cashback. The current rules give $current.",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                )
                TextButton(onClick = onUseCurrentRule) {
                    Text(if (quote.rulePercentage.isZero) "Remove cashback" else "Use ${quote.rulePercentage.format()}")
                }
            } else {
                cashbackNote(quote.eligibility, merchant)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
            }

            val owed = posting.udhaarOwed
            if (paidFor != null && owed != null) {
                HorizontalDivider()
                AmountLine(
                    label = "${paidFor.name}'s udhaar",
                    amount = owed.format(),
                    amountTag = TestTags.BREAKDOWN_UDHAAR,
                    emphasized = true,
                )
                Text(
                    text = "${paidFor.name} owes you the full ${owed.format()}. The cashback stays with you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                )
            }
        }
    }
}

private fun cashbackNote(eligibility: CashbackEligibility, merchant: String): String? = when (eligibility) {
    is CashbackEligibility.Eligible -> null
    is CashbackEligibility.RuleDisabled ->
        "${eligibility.rule.merchant} cashback (${eligibility.rule.percentage.format()}) is turned off in Cashback rules."
    is CashbackEligibility.NotPaidByCard ->
        "${eligibility.rule.merchant} cashback applies to card payments only."
    CashbackEligibility.NoRule ->
        if (merchant.isBlank()) null else "No cashback rule for ${cleanName(merchant)}."
}
