package io.github.chiragbhatn.expensetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.BalanceState
import io.github.chiragbhatn.expensetracker.domain.CreditCard
import io.github.chiragbhatn.expensetracker.domain.Expense
import io.github.chiragbhatn.expensetracker.domain.Income
import io.github.chiragbhatn.expensetracker.domain.LedgerSummary
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.ShareMessages
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.domain.UdhaarEntry
import io.github.chiragbhatn.expensetracker.ui.formatMonth
import io.github.chiragbhatn.expensetracker.ui.formatShort
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import java.time.YearMonth

/** "‹ October 2026 ›" */
@Composable
fun MonthSelector(month: YearMonth, onMonthChange: (YearMonth) -> Unit, modifier: Modifier = Modifier, latest: YearMonth = YearMonth.now()) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            month.formatMonth(),
            modifier = Modifier.weight(1f).testTag("month_label"),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, enabled = month < latest) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/** One expense: merchant, how it was paid, who shares it, and its original and effective amounts. */
@Composable
fun ExpenseListItem(expense: Expense, card: CreditCard?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val amounts = expense.amounts
    val method = when {
        expense.paymentMethod == PaymentMethod.CARD && card != null -> card.displayName
        else -> expense.paymentMethod.label
    }
    val shared = expense.paidFor.joinToString { it.name }
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { InitialAvatar(expense.merchant) },
        headlineContent = { Text(expense.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    "${expense.date.formatShort()} · ${expense.category} · $method",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (shared.isNotEmpty()) {
                    Text("Shared with $shared", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(amounts.effectiveAmount.format(), style = MaterialTheme.typography.titleMedium)
                if (amounts.hasCashback) {
                    Text(
                        amounts.originalAmount.format(),
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${amounts.cashbackAmount.format()} back",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAmountColors.current.positive,
                    )
                }
            }
        },
    )
}

@Composable
fun IncomeListItem(income: Income, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { InitialAvatar(income.source.ifBlank { income.category }) },
        headlineContent = { Text(income.source.ifBlank { income.category }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${income.date.formatShort()} · ${income.category}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Text("+${income.amount.format()}", style = MaterialTheme.typography.titleMedium, color = LocalAmountColors.current.positive)
        },
    )
}

/** The colour that goes with a person's balance state. */
@Composable
fun balanceColor(summary: LedgerSummary): Color {
    val colors = LocalAmountColors.current
    return when (summary.state) {
        BalanceState.OWES_YOU -> colors.positive
        BalanceState.SETTLED -> MaterialTheme.colorScheme.onSurfaceVariant
        BalanceState.HAS_CREDIT -> colors.credit
        BalanceState.YOU_OWE -> colors.negative
    }
}

/** "Owes you ₹1,500", "Settled", "Has ₹500 credit", "You owe ₹200" – short form for lists. */
fun shortBalance(summary: LedgerSummary): String = when (summary.state) {
    BalanceState.OWES_YOU -> "Owes you ${summary.receivable.format()}"
    BalanceState.SETTLED -> "Settled"
    BalanceState.HAS_CREDIT -> "Has ${summary.credit.format()} credit"
    BalanceState.YOU_OWE -> "You owe ${summary.payable.format()}"
}

/** One ledger entry with its type, date and signed amount. */
@Composable
fun LedgerEntryRow(entry: UdhaarEntry, onClick: () -> Unit, modifier: Modifier = Modifier, personName: String? = null) {
    val colors = LocalAmountColors.current
    val gave = entry.direction == UdhaarDirection.GAVE
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(listOfNotNull(personName, ShareMessages.describe(entry)).joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOf(entry.date.formatShort(), if (gave) "adds to what they owe" else "reduces what they owe").joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Text(
                if (gave) "+${entry.amount.format()}" else (-entry.amount).format(),
                style = MaterialTheme.typography.titleMedium,
                color = if (gave) colors.positive else colors.negative,
                fontWeight = FontWeight.Medium,
            )
        },
    )
}

/** A label above a large amount, for summary rows. */
@Composable
fun AmountSummary(items: List<Pair<String, Money>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (label, amount) -> Stat(label, amount.format(), Modifier.weight(1f)) }
    }
}
