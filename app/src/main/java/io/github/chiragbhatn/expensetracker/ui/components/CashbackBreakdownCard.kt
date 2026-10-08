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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.CashbackBreakdown
import io.github.chiragbhatn.expensetracker.domain.CashbackEligibility
import io.github.chiragbhatn.expensetracker.domain.CashbackQuote
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors

/** What someone owes for an expense, for display. */
data class ShareLine(val name: String, val amount: Money)

/**
 * Shows the transaction as the user types:
 *
 *     Original amount      ₹200
 *     Cashback (10%)       −₹20
 *     Effective expense     ₹180
 *     Rahul owes you        ₹180
 */
@Composable
fun CashbackBreakdownCard(
    amounts: CashbackBreakdown,
    quote: CashbackQuote?,
    merchant: String,
    shares: List<ShareLine>,
    myShare: Money?,
    onUseCurrentRule: () -> Unit,
    modifier: Modifier = Modifier,
    customPercentage: Boolean = false,
) {
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountLine(label = "Original amount", amount = amounts.originalAmount.format(), amountTag = TestTags.BREAKDOWN_ORIGINAL)
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

            when {
                customPercentage -> Text("Cashback entered by you for this expense.", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                quote != null && quote.percentage != quote.rulePercentage -> {
                    val current = if (quote.rulePercentage.isZero) "none" else quote.rulePercentage.format()
                    Text(
                        text = "Saved with ${quote.percentage.format()} cashback. The current rules give $current.",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                    )
                    TextButton(onClick = onUseCurrentRule) {
                        Text(if (quote.rulePercentage.isZero) "Remove cashback" else "Use ${quote.rulePercentage.format()}")
                    }
                }
                quote != null -> cashbackNote(quote.eligibility, merchant)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
            }

            if (shares.isNotEmpty()) {
                HorizontalDivider()
                shares.forEach { share ->
                    AmountLine(
                        label = "${share.name} owes you",
                        amount = share.amount.format(),
                        modifier = Modifier.testTag(TestTags.shareLine(share.name)),
                        amountTag = TestTags.shareAmount(share.name),
                    )
                }
                if (myShare != null && myShare.isPositive) AmountLine(label = "Your share", amount = myShare.format())
                if (amounts.hasCashback) {
                    Text(
                        text = "Cashback lowers everyone's share: shares add up to the effective ${amounts.effectiveAmount.format()}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                    )
                }
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
    is CashbackEligibility.OtherCard ->
        "${eligibility.rule.merchant} cashback applies to a different card."
    CashbackEligibility.NoRule ->
        if (merchant.isBlank()) null else "No cashback rule for ${cleanName(merchant)}."
}
