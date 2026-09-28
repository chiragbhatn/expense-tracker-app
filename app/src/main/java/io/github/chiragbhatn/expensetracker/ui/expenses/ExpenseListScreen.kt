package io.github.chiragbhatn.expensetracker.ui.expenses

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chiragbhatn.expensetracker.ui.AppViewModelProvider
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.ExpenseListItem
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.dashboard.AddExpenseButton
import io.github.chiragbhatn.expensetracker.ui.formatMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    bottomBar: @Composable () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    viewModel: ExpenseListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val months by viewModel.months.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Expenses") }) },
        bottomBar = bottomBar,
        floatingActionButton = { AddExpenseButton(onAddExpense) },
    ) { padding ->
        LazyColumn(contentPadding = listPadding(padding)) {
            val loaded = months ?: return@LazyColumn
            if (loaded.isEmpty()) {
                item { EmptyState("No expenses yet", "Tap Add expense to record your first one.") }
            }
            loaded.forEach { group ->
                item(key = group.month.toString()) { MonthHeader(group) }
                items(group.expenses, key = { it.id }) { expense ->
                    ExpenseListItem(expense, onClick = { onOpenExpense(expense.id) })
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(group: MonthGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.month.formatMonth(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        val cashback = group.summary.cashbackReceived
        Text(
            text = buildString {
                append(group.summary.effectiveExpenses.format())
                if (cashback.isPositive) append(" after ${cashback.format()} cashback")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
