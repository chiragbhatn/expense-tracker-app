@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.chiragbhatn.expensetracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.CardMath
import io.github.chiragbhatn.expensetracker.domain.MonthlySummary
import io.github.chiragbhatn.expensetracker.domain.Period
import io.github.chiragbhatn.expensetracker.domain.SpendingSummary
import io.github.chiragbhatn.expensetracker.domain.creditHeld
import io.github.chiragbhatn.expensetracker.domain.moneyToPay
import io.github.chiragbhatn.expensetracker.domain.moneyToReceive
import io.github.chiragbhatn.expensetracker.domain.overdue
import io.github.chiragbhatn.expensetracker.domain.sumMoney
import io.github.chiragbhatn.expensetracker.domain.upcoming
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.appSettings
import io.github.chiragbhatn.expensetracker.ui.components.AddActions
import io.github.chiragbhatn.expensetracker.ui.components.AddMenuFab
import io.github.chiragbhatn.expensetracker.ui.components.ExpenseListItem
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.InfoCard
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.MonthSelector
import io.github.chiragbhatn.expensetracker.ui.components.SectionTitle
import io.github.chiragbhatn.expensetracker.ui.components.Stat
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.formatShort
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import io.github.chiragbhatn.expensetracker.ui.today
import java.time.YearMonth

data class HomeActions(
    val add: AddActions,
    val addPerson: () -> Unit,
    val openExpense: (Long) -> Unit,
    val openExpenses: () -> Unit,
    val openUdhaar: () -> Unit,
    val openCards: () -> Unit,
    val openCard: (Long) -> Unit,
    val openRecurring: () -> Unit,
    val openCashback: () -> Unit,
    val openReports: () -> Unit,
    val search: () -> Unit,
)

@Composable
fun HomeScreen(bottomBar: @Composable () -> Unit, actions: HomeActions) {
    val data = appData()
    val settings = appSettings()
    val today = today()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    val colors = LocalAmountColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Tracker") },
                actions = { IconButton(onClick = actions.search) { Icon(Icons.Filled.Search, contentDescription = "Search") } },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = { AddMenuFab(actions.add) },
    ) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@Scaffold
        }
        val summary = remember(data, month) { MonthlySummary.of(Period.Month(month), data.expenses, data.incomes, data.people, data.entries, today) }
        val spending = remember(data, month) { SpendingSummary.of(data.expenses.filter { YearMonth.from(it.date) == month }) }
        val balances = data.balances
        val overdue = remember(balances) { balances.overdue(today, settings.reminders.udhaarAfterDays.coerceAtLeast(1).toLong()) }
        val cardSummaries = remember(data) { data.cardSummaries(today).filter { it.card.active } }
        val upcomingBills = remember(cardSummaries) { CardMath.upcomingPayments(cardSummaries, today) }
        val upcomingRecurring = remember(data) { data.recurring.upcoming(today, withinDays = 30) }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            item { MonthSelector(month, { monthText = it.toString() }, Modifier.padding(horizontal = 8.dp), latest = YearMonth.from(today)) }
            item {
                InfoCard(title = null) {
                    Stat(
                        "Balance this month (income − spending after cashback)",
                        summary.cashFlow.format(),
                        tag = TestTags.DASHBOARD_BALANCE,
                        large = true,
                        color = if (summary.cashFlow.isNegative) colors.negative else MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("Income", summary.income.format(), Modifier.weight(1f), tag = TestTags.DASHBOARD_INCOME)
                        Stat("Expenses", summary.originalExpenses.format(), Modifier.weight(1f), tag = TestTags.DASHBOARD_EXPENSES)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("Cashback", summary.cashback.format(), Modifier.weight(1f), color = colors.positive, tag = TestTags.DASHBOARD_CASHBACK)
                        Stat("Effective expenses", summary.effectiveExpenses.format(), Modifier.weight(1f), tag = TestTags.DASHBOARD_EFFECTIVE)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("Card spending", spending.cardSpending.format(), Modifier.weight(1f), tag = TestTags.DASHBOARD_CARD_SPENDING)
                        Stat("UPI, cash & other", spending.otherSpending.format(), Modifier.weight(1f), tag = TestTags.DASHBOARD_OTHER_SPENDING)
                    }
                    TextButton(onClick = actions.openReports) { Text("Monthly summary and reports") }
                }
            }
            item { QuickActions(actions) }
            item {
                InfoCard(title = "Udhaar", onClick = actions.openUdhaar) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("Money to receive", balances.moneyToReceive().format(), Modifier.weight(1f), color = colors.positive, tag = TestTags.DASHBOARD_TO_RECEIVE)
                        Stat("Money to pay", balances.moneyToPay().format(), Modifier.weight(1f), color = colors.negative, tag = TestTags.DASHBOARD_TO_PAY)
                    }
                    val credit = balances.creditHeld()
                    if (credit.isPositive) Hint("Credit people hold with you: ${credit.format()}, adjusted against their next expenses.")
                    if (overdue.isNotEmpty()) {
                        Text(
                            "Overdue: ${overdue.size} ${if (overdue.size == 1) "person owes" else "people owe"} ${overdue.sumMoney { it.summary.receivable }.format()} " +
                                "with no payment for over ${settings.reminders.udhaarAfterDays.coerceAtLeast(1)} days",
                            color = colors.negative,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Hint("Nothing overdue.")
                    }
                }
            }
            item {
                InfoCard(title = "Credit cards", onClick = actions.openCards) {
                    if (cardSummaries.isEmpty()) {
                        Hint("Add your cards to see outstanding amounts, limits and bill dates.")
                    } else {
                        Stat("Current card outstanding", cardSummaries.sumMoney { maxOf(it.outstanding, io.github.chiragbhatn.expensetracker.domain.Money.ZERO) }.format(), tag = TestTags.DASHBOARD_CARD_OUTSTANDING)
                        val next = upcomingBills.firstOrNull()
                        if (next != null) {
                            Text(
                                (if (next.overdue) "Overdue: " else "Upcoming payment: ") +
                                    "${next.card.displayName} ${next.amount.format()} due ${next.dueDate.formatShort()}",
                                color = if (next.overdue) colors.negative else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Hint("No card bills due.")
                        }
                    }
                }
            }
            item {
                InfoCard(title = "Upcoming recurring expenses", onClick = actions.openRecurring) {
                    if (upcomingRecurring.isEmpty()) {
                        Hint("Nothing in the next 30 days.")
                    } else {
                        upcomingRecurring.take(3).forEach { item ->
                            Row(Modifier.fillMaxWidth()) {
                                Text("${item.title} · ${item.nextDate.formatShort()}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(item.amount.format(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item {
                InfoCard(title = "Cashback", onClick = actions.openCashback) {
                    Hint("See cashback by day, month, year, merchant and card.")
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Recent expenses", Modifier.weight(1f))
                    TextButton(onClick = actions.openExpenses) { Text("See all") }
                }
            }
            val recent = data.expenses.take(5)
            if (recent.isEmpty()) {
                item { Hint("No expenses yet. Tap + to add one.", Modifier.padding(horizontal = 16.dp)) }
            }
            items(recent, key = { it.id }) { expense ->
                ExpenseListItem(expense, expense.cardId?.let(data.cardsById::get), onClick = { actions.openExpense(expense.id) })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun QuickActions(actions: HomeActions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        QuickAction("Expense", Icons.Filled.ShoppingCart, actions.add.expense, "quick_expense")
        QuickAction("Income", Icons.Filled.Payments, actions.add.income, "quick_income")
        QuickAction("Udhaar", Icons.AutoMirrored.Filled.CallMade, actions.add.udhaar, "quick_udhaar")
        QuickAction("Payment", Icons.AutoMirrored.Filled.CallReceived, actions.add.payment, "quick_payment")
        QuickAction("Person", Icons.Filled.PersonAdd, actions.addPerson, "quick_person")
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit, tag: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.testTag(tag)) { Icon(icon, contentDescription = null) }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}
