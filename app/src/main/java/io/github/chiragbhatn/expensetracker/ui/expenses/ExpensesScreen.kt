@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.chiragbhatn.expensetracker.ui.expenses

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.SpendingSummary
import io.github.chiragbhatn.expensetracker.domain.sumMoney
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.AddActions
import io.github.chiragbhatn.expensetracker.ui.components.AddMenuFab
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.ExpenseListItem
import io.github.chiragbhatn.expensetracker.ui.components.IncomeListItem
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.MonthSelector
import io.github.chiragbhatn.expensetracker.ui.components.Stat
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import io.github.chiragbhatn.expensetracker.ui.today
import java.time.YearMonth

@Composable
fun ExpensesScreen(
    bottomBar: @Composable () -> Unit,
    addActions: AddActions,
    onOpenExpense: (Long) -> Unit,
    onOpenIncome: (Long) -> Unit,
    onSearch: () -> Unit,
) {
    val data = appData()
    val today = today()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    var showIncome by rememberSaveable { mutableStateOf(false) }
    var method by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                actions = { IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search") } },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = { AddMenuFab(addActions) },
    ) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@Scaffold
        }
        val monthExpenses = remember(data, month) { data.expenses.filter { YearMonth.from(it.date) == month } }
        val monthIncome = remember(data, month) { data.incomes.filter { YearMonth.from(it.date) == month } }
        val filtered = monthExpenses.filter { (method == null || it.paymentMethod.name == method) && (category == null || it.category == category) }
        val summary = SpendingSummary.of(filtered)
        val colors = LocalAmountColors.current

        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            item { MonthSelector(month, { monthText = it.toString() }, Modifier.padding(horizontal = 8.dp), latest = YearMonth.from(today)) }
            item {
                PrimaryTabRow(selectedTabIndex = if (showIncome) 1 else 0) {
                    Tab(selected = !showIncome, onClick = { showIncome = false }, text = { Text("Expenses (${monthExpenses.size})") })
                    Tab(selected = showIncome, onClick = { showIncome = true }, text = { Text("Income (${monthIncome.size})") })
                }
            }
            if (!showIncome) {
                item {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("Spent", summary.originalExpenses.format(), Modifier.weight(1f))
                        Stat("Cashback", summary.cashbackReceived.format(), Modifier.weight(1f), color = colors.positive)
                        Stat("After cashback", summary.effectiveExpenses.format(), Modifier.weight(1f))
                    }
                }
                item {
                    Column {
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(selected = method == null, onClick = { method = null }, label = { Text("All") })
                            PaymentMethod.entries.forEach { m ->
                                FilterChip(selected = method == m.name, onClick = { method = if (method == m.name) null else m.name }, label = { Text(m.label) })
                            }
                        }
                        val categories = monthExpenses.map { it.category }.distinct().sorted()
                        if (categories.size > 1) {
                            Row(
                                Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                categories.forEach { c ->
                                    FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) })
                                }
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item { EmptyState("No expenses", "Tap + to add an expense for this month.") }
                }
                items(filtered, key = { it.id }) { expense ->
                    ExpenseListItem(expense, expense.cardId?.let(data.cardsById::get), onClick = { onOpenExpense(expense.id) })
                }
            } else {
                item {
                    Row(Modifier.padding(16.dp)) {
                        Stat("Income this month", monthIncome.sumMoney { it.amount }.format(), Modifier.weight(1f), color = colors.positive)
                    }
                }
                if (monthIncome.isEmpty()) {
                    item { EmptyState("No income", "Tap + and choose Income to record money coming in.") }
                }
                items(monthIncome, key = { it.id }) { income -> IncomeListItem(income, onClick = { onOpenIncome(income.id) }) }
            }
        }
    }
}
