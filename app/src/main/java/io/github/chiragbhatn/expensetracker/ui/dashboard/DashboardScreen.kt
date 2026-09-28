@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.chiragbhatn.expensetracker.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chiragbhatn.expensetracker.domain.Period
import io.github.chiragbhatn.expensetracker.domain.SpendingSummary
import io.github.chiragbhatn.expensetracker.ui.AppViewModelProvider
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.components.AmountLine
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.ExpenseListItem
import io.github.chiragbhatn.expensetracker.ui.components.SectionTitle
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.formatMonth
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors

@Composable
fun DashboardScreen(
    bottomBar: @Composable () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenUdhaar: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Expense Tracker") }) },
        bottomBar = bottomBar,
        floatingActionButton = { AddExpenseButton(onAddExpense) },
    ) { padding ->
        LazyColumn(contentPadding = listPadding(padding)) {
            item {
                PeriodSelector(
                    period = state.period,
                    canShowNextMonth = state.canShowNextMonth,
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                    onToggleAllTime = viewModel::toggleAllTime,
                )
            }
            item { SpendingCard(state.summary, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            item {
                MoneyToReceiveCard(
                    state = state,
                    onOpenUdhaar = onOpenUdhaar,
                    onOpenPerson = onOpenPerson,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            item { SectionTitle("Recent transactions", Modifier.padding(top = 12.dp)) }
            if (state.recent.isEmpty()) {
                item { EmptyState("No expenses in this period", "Tap Add expense to record one.") }
            }
            items(state.recent, key = { it.id }) { expense ->
                ExpenseListItem(expense, onClick = { onOpenExpense(expense.id) })
            }
        }
    }
}

@Composable
fun AddExpenseButton(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text("Add expense") },
        modifier = Modifier.testTag(TestTags.ADD_EXPENSE),
    )
}

@Composable
private fun PeriodSelector(
    period: Period,
    canShowNextMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleAllTime: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val byMonth = period is Period.Month
        IconButton(onClick = onPrevious, enabled = byMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = when (period) {
                is Period.Month -> period.month.formatMonth()
                Period.AllTime -> "All time"
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = canShowNextMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
        FilterChip(
            selected = !byMonth,
            onClick = onToggleAllTime,
            label = { Text("All time") },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun SpendingCard(summary: SpendingSummary, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Spending", style = MaterialTheme.typography.titleMedium)
            AmountLine(
                label = "Card spending",
                amount = summary.cardSpending.format(),
                amountTag = TestTags.DASHBOARD_CARD_SPENDING,
            )
            if (summary.otherSpending.isPositive) {
                AmountLine(
                    label = "UPI, cash & other",
                    amount = summary.otherSpending.format(),
                    amountTag = TestTags.DASHBOARD_OTHER_SPENDING,
                )
            }
            AmountLine(
                label = "Cashback received",
                amount = summary.cashbackReceived.format(),
                amountTag = TestTags.DASHBOARD_CASHBACK,
                color = LocalAmountColors.current.positive,
            )
            HorizontalDivider()
            AmountLine(
                label = "Effective expenses",
                amount = summary.effectiveExpenses.format(),
                amountTag = TestTags.DASHBOARD_EFFECTIVE,
                emphasized = true,
            )
            Text(
                text = "Effective expenses = amount spent − cashback",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MoneyToReceiveCard(
    state: DashboardUiState,
    onOpenUdhaar: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAmountColors.current
    ElevatedCard(onClick = onOpenUdhaar, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AmountLine(
                label = "Money to receive",
                amount = state.moneyToReceive.format(),
                amountTag = TestTags.DASHBOARD_TO_RECEIVE,
                color = colors.positive,
                emphasized = true,
            )
            if (state.owedToYou.isEmpty()) {
                Text(
                    text = "Nobody owes you money right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.owedToYou.forEach { (person, balance) ->
                AmountLine(
                    label = person.name,
                    amount = balance.format(),
                    modifier = Modifier.clickable { onOpenPerson(person.id) },
                )
            }
            if (state.moneyToGive.isPositive) {
                HorizontalDivider()
                AmountLine(label = "You owe others", amount = state.moneyToGive.format(), color = colors.negative)
            }
        }
    }
}
