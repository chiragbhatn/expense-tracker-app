package io.github.chiragbhatn.expensetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.chiragbhatn.expensetracker.domain.Expense
import io.github.chiragbhatn.expensetracker.ui.formatShort
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors

/** A transaction row: merchant, date and payment details, cashback breakdown and effective amount. */
@Composable
fun ExpenseListItem(expense: Expense, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val amounts = expense.amounts
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = { InitialAvatar(expense.merchant) },
        headlineContent = { Text(expense.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    text = buildString {
                        append(expense.date.formatShort())
                        append(" · ")
                        append(expense.paymentMethod.label)
                        expense.paidFor?.let { append(" · for ").append(it.name) }
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (amounts.hasCashback) {
                    Text(
                        text = "${amounts.originalAmount.format()} − ${amounts.cashbackAmount.format()} " +
                            "cashback (${amounts.cashbackPercentage.format()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAmountColors.current.positive,
                    )
                }
            }
        },
        trailingContent = {
            Text(
                text = amounts.effectiveAmount.format(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
}
