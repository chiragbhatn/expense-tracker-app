@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.chiragbhatn.expensetracker.ui.udhaar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.BalanceState
import io.github.chiragbhatn.expensetracker.domain.creditHeld
import io.github.chiragbhatn.expensetracker.domain.moneyToPay
import io.github.chiragbhatn.expensetracker.domain.moneyToReceive
import io.github.chiragbhatn.expensetracker.domain.overdue
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.appSettings
import io.github.chiragbhatn.expensetracker.ui.components.AddActions
import io.github.chiragbhatn.expensetracker.ui.components.AddMenuFab
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.InfoCard
import io.github.chiragbhatn.expensetracker.ui.components.InitialAvatar
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.Stat
import io.github.chiragbhatn.expensetracker.ui.components.balanceColor
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.components.shortBalance
import io.github.chiragbhatn.expensetracker.ui.formatShort
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import io.github.chiragbhatn.expensetracker.ui.today

private enum class PeopleFilter(val label: String) { ALL("All"), OWE_YOU("Owe you"), YOU_OWE("You owe / credit"), SETTLED("Settled") }

@Composable
fun UdhaarScreen(
    bottomBar: @Composable () -> Unit,
    addActions: AddActions,
    onAddPerson: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onSearch: () -> Unit,
) {
    val data = appData()
    val settings = appSettings()
    val today = today()
    val container = LocalAppContainer.current
    var filter by rememberSaveable { mutableStateOf(PeopleFilter.ALL) }
    val colors = LocalAmountColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Udhaar") },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                    IconButton(onClick = onAddPerson, modifier = Modifier.testTag(TestTags.ADD_PERSON)) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Add person")
                    }
                },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = { AddMenuFab(addActions) },
    ) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@Scaffold
        }
        val balances = data.balances
        val overdueIds = balances.overdue(today, settings.reminders.udhaarAfterDays.coerceAtLeast(1).toLong()).map { it.person.id }.toSet()
        val shown = balances.filter { balance ->
            when (filter) {
                PeopleFilter.ALL -> true
                PeopleFilter.OWE_YOU -> balance.summary.state == BalanceState.OWES_YOU
                PeopleFilter.YOU_OWE -> balance.summary.state == BalanceState.YOU_OWE || balance.summary.state == BalanceState.HAS_CREDIT
                PeopleFilter.SETTLED -> balance.summary.state == BalanceState.SETTLED
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            item {
                InfoCard(title = null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("To receive", balances.moneyToReceive().format(), Modifier.weight(1f), color = colors.positive, tag = TestTags.DASHBOARD_TO_RECEIVE)
                        Stat("To pay", balances.moneyToPay().format(), Modifier.weight(1f), color = colors.negative)
                        Stat("Overdue", overdueIds.size.toString(), Modifier.weight(1f))
                    }
                    val credit = balances.creditHeld()
                    if (credit.isPositive) Hint("Credit people hold with you: ${credit.format()}, adjusted against their next expenses.")
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeopleFilter.entries.forEach { option ->
                        FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
                    }
                }
            }
            if (balances.isEmpty()) {
                item { EmptyState("No people yet", "Add someone to track what they owe you, or split an expense with them.") }
            }
            items(shown, key = { it.person.id }) { balance ->
                val person = balance.person
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPerson(person.id) }
                        .testTag(TestTags.person(person.name)),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { InitialAvatar(person.name, photo = person.photoPath?.let(container.files::photo)) },
                    headlineContent = { Text(person.name) },
                    supportingContent = {
                        val last = balance.lastActivity?.let { "Last activity ${it.formatShort()}" } ?: "No transactions yet"
                        Text(if (person.id in overdueIds) "Overdue · $last" else last, color = if (person.id in overdueIds) colors.negative else Color.Unspecified)
                    },
                    trailingContent = {
                        Text(shortBalance(balance.summary), color = balanceColor(balance.summary), style = MaterialTheme.typography.titleSmall)
                    },
                )
            }
        }
    }
}
