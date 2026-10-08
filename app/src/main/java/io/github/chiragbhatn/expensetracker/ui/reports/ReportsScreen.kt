@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.chiragbhatn.expensetracker.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.BalanceState
import io.github.chiragbhatn.expensetracker.domain.MonthlySummary
import io.github.chiragbhatn.expensetracker.domain.Period
import io.github.chiragbhatn.expensetracker.domain.Reports
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.AmountLine
import io.github.chiragbhatn.expensetracker.ui.components.BarItem
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.HorizontalBars
import io.github.chiragbhatn.expensetracker.ui.components.InfoCard
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.MonthColumns
import io.github.chiragbhatn.expensetracker.ui.components.MonthSelector
import io.github.chiragbhatn.expensetracker.ui.components.MonthTable
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import io.github.chiragbhatn.expensetracker.ui.today
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun ReportsScreen(
    bottomBar: @Composable () -> Unit,
    onOpenCashback: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onOpenCards: () -> Unit,
) {
    val data = appData()
    val today = today()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    var showTable by rememberSaveable { mutableStateOf(false) }
    val colors = LocalAmountColors.current

    Scaffold(topBar = { TopAppBar(title = { Text("Reports") }) }, bottomBar = bottomBar) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@Scaffold
        }
        val summary = remember(data, month) { MonthlySummary.of(Period.Month(month), data.expenses, data.incomes, data.people, data.entries, today) }
        val inMonth = remember(data, month) { data.expenses.filter { YearMonth.from(it.date) == month } }
        val trend = remember(data, month) { Reports.trend(data.expenses, data.incomes, month, 6) }
        val categories = remember(inMonth) { Reports.byCategory(inMonth) }
        val merchants = remember(inMonth) { Reports.byMerchant(inMonth) }
        val cards = remember(inMonth, data.cards) { Reports.byCard(inMonth, data.cards) }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding, hasFab = false)) {
            item { MonthSelector(month, { monthText = it.toString() }, Modifier.padding(horizontal = 8.dp), latest = YearMonth.from(today)) }
            item {
                InfoCard(title = "Monthly summary") {
                    AmountLine("Income", summary.income.format(), amountTag = "summary_income")
                    AmountLine("Original expenses", summary.originalExpenses.format(), amountTag = "summary_original")
                    AmountLine("Cashback", "−${summary.cashback.format()}", amountTag = "summary_cashback", color = colors.positive)
                    AmountLine("Effective expenses", summary.effectiveExpenses.format(), amountTag = "summary_effective", emphasized = true)
                    HorizontalDivider()
                    AmountLine("Money to receive", summary.receivable.format(), amountTag = "summary_receivable")
                    AmountLine("Money to pay", summary.payable.format(), amountTag = "summary_payable")
                    HorizontalDivider()
                    AmountLine(
                        "Net position",
                        summary.netPosition.format(),
                        amountTag = "summary_net",
                        emphasized = true,
                        color = if (summary.netPosition.isNegative) colors.negative else colors.positive,
                    )
                    Hint("Net position = income − effective expenses + money to receive − money to pay. Cashback is counted once, in effective expenses.")
                }
            }
            item {
                InfoCard(title = "Spending and income, last 6 months") {
                    MonthColumns(trend, selected = month, onSelect = { monthText = it.toString() })
                    TextButton(onClick = { showTable = !showTable }) { Text(if (showTable) "Hide table" else "Show as table") }
                    if (showTable) MonthTable(trend, selected = month)
                }
            }
            item {
                InfoCard(title = "By category (after cashback)") {
                    if (categories.isEmpty()) {
                        Hint("No expenses this month.")
                    } else {
                        HorizontalBars(
                            categories.map { BarItem(it.category, it.effective, "${(it.fraction * 100).roundToInt()}% · ${it.count} expense${if (it.count == 1) "" else "s"}") },
                            Modifier.testTag("report_categories"),
                        )
                    }
                }
            }
            item {
                InfoCard(title = "By merchant") {
                    if (merchants.isEmpty()) {
                        Hint("No expenses this month.")
                    } else {
                        val top = merchants.take(8)
                        HorizontalBars(
                            top.map { m ->
                                BarItem(
                                    m.merchant,
                                    m.original,
                                    if (m.cashback.isPositive) "Cashback ${m.cashback.format()} · after cashback ${m.effective.format()}" else "${m.count} expense${if (m.count == 1) "" else "s"}",
                                )
                            },
                        )
                        if (merchants.size > top.size) Hint("${merchants.size - top.size} more merchants not shown.")
                    }
                }
            }
            item {
                InfoCard(title = "Cashback", onClick = onOpenCashback) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AmountLine("This month", summary.cashback.format(), color = colors.positive)
                    }
                    Hint("Open for cashback by merchant, card, day and year.")
                }
            }
            item {
                InfoCard(title = "Card spending", onClick = onOpenCards) {
                    if (cards.isEmpty()) Hint("No card expenses linked to a card this month.") else HorizontalBars(cards.map { BarItem(it.key, it.amount) })
                }
            }
            item {
                InfoCard(title = "Money to receive and pay") {
                    val owing = data.balances.filter { it.summary.state != BalanceState.SETTLED }.sortedByDescending { it.balance.abs() }
                    if (owing.isEmpty()) {
                        Hint("Everyone is settled.")
                    } else {
                        HorizontalBars(
                            owing.take(8).map { balance ->
                                val label = when (balance.summary.state) {
                                    BalanceState.OWES_YOU -> "${balance.person.name} owes you"
                                    BalanceState.YOU_OWE -> "You owe ${balance.person.name}"
                                    else -> "${balance.person.name} (credit)"
                                }
                                BarItem(label, balance.balance.abs(), onClick = { onOpenPerson(balance.person.id) })
                            },
                        )
                    }
                }
            }
        }
    }
}
