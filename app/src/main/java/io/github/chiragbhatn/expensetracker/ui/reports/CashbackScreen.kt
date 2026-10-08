package io.github.chiragbhatn.expensetracker.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.CashbackStats
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.AmountLine
import io.github.chiragbhatn.expensetracker.ui.components.BarItem
import io.github.chiragbhatn.expensetracker.ui.components.DetailScreen
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.HorizontalBars
import io.github.chiragbhatn.expensetracker.ui.components.InfoCard
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.MonthSelector
import io.github.chiragbhatn.expensetracker.ui.components.Stat
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.formatMonth
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import io.github.chiragbhatn.expensetracker.ui.today
import java.time.YearMonth

@Composable
fun CashbackScreen(onBack: () -> Unit, onOpenRules: () -> Unit) {
    val data = appData()
    val today = today()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    val colors = LocalAmountColors.current

    DetailScreen(title = "Cashback", onBack = onBack, actions = { TextButton(onClick = onOpenRules) { Text("Rules") } }) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@DetailScreen
        }
        val stats = remember(data, month) { CashbackStats.of(data.expenses, data.cards, today, month) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding, hasFab = false)) {
            item {
                InfoCard(title = null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("Today", stats.today.format(), Modifier.weight(1f), color = colors.positive, tag = "cashback_today")
                        Stat("This month", stats.thisMonth.format(), Modifier.weight(1f), color = colors.positive, tag = "cashback_month")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("This year", stats.thisYear.format(), Modifier.weight(1f), color = colors.positive, tag = "cashback_year")
                        Stat("All time", stats.total.format(), Modifier.weight(1f), color = colors.positive, tag = "cashback_total")
                    }
                }
            }
            item { MonthSelector(month, { monthText = it.toString() }, Modifier.padding(horizontal = 8.dp), latest = YearMonth.from(today)) }
            item {
                InfoCard(title = month.formatMonth()) {
                    AmountLine("Original card spending", stats.monthCardSpending.format())
                    AmountLine("Cashback", "−${stats.monthCardSpending.minus(stats.monthEffectiveCardSpending).format()}", color = colors.positive)
                    HorizontalDivider()
                    AmountLine("Effective card spending", stats.monthEffectiveCardSpending.format(), emphasized = true)
                }
            }
            item {
                InfoCard(title = "By merchant, ${month.formatMonth()}") {
                    if (stats.monthByMerchant.isEmpty()) Hint("No cashback this month.") else HorizontalBars(stats.monthByMerchant.map { BarItem(it.key, it.amount) })
                }
            }
            item {
                InfoCard(title = "By card, all time") {
                    if (stats.byCard.isEmpty()) Hint("No cashback yet.") else HorizontalBars(stats.byCard.map { BarItem(it.key, it.amount) })
                }
            }
        }
    }
}
