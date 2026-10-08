@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.chiragbhatn.expensetracker.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.Search
import io.github.chiragbhatn.expensetracker.domain.SearchResults
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.AmountLine
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.ExpenseListItem
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.IncomeListItem
import io.github.chiragbhatn.expensetracker.ui.components.InfoCard
import io.github.chiragbhatn.expensetracker.ui.components.InitialAvatar
import io.github.chiragbhatn.expensetracker.ui.components.LedgerEntryRow
import io.github.chiragbhatn.expensetracker.ui.components.SectionTitle
import io.github.chiragbhatn.expensetracker.ui.components.balanceColor
import io.github.chiragbhatn.expensetracker.ui.components.shortBalance
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors

data class SearchActions(
    val back: () -> Unit,
    val openPerson: (Long) -> Unit,
    val openExpense: (Long) -> Unit,
    val openIncome: (Long) -> Unit,
    val openCard: (Long) -> Unit,
    val openEntry: (Long, Long) -> Unit,
)

@Composable
fun SearchScreen(actions: SearchActions) {
    val data = appData()
    val container = LocalAppContainer.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val results = remember(data, query) { if (data == null) SearchResults.EMPTY else Search.run(query, data.searchData()) }
    val colors = LocalAmountColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = actions.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("People, merchants, cards, notes…") },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                        },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .testTag(TestTags.SEARCH_INPUT),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (query.isBlank()) {
                item { Hint("Search everything: people, expenses, income, merchants, cards, ledger entries and notes.", Modifier.padding(16.dp)) }
            } else if (results.isEmpty) {
                item { EmptyState("Nothing found", "No matches for \"${query.trim()}\".") }
            }
            if (results.people.isNotEmpty()) item { SectionTitle("People") }
            items(results.people, key = { "p${it.person.id}" }) { match ->
                ListItem(
                    modifier = Modifier.clickable { actions.openPerson(match.person.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { InitialAvatar(match.person.name, photo = match.person.photoPath?.let(container.files::photo)) },
                    headlineContent = { Text(match.person.name) },
                    supportingContent = { Text("${match.expenseCount} shared expenses · ${match.paymentCount} payments") },
                    trailingContent = { Text(shortBalance(match.summary), color = balanceColor(match.summary)) },
                )
            }
            if (results.merchants.isNotEmpty()) item { SectionTitle("Merchants") }
            items(results.merchants, key = { "m${it.merchant}" }) { merchant ->
                InfoCard(title = merchant.merchant) {
                    AmountLine("Total spending", merchant.original.format())
                    AmountLine("Cashback", merchant.cashback.format(), color = colors.positive)
                    AmountLine("After cashback", merchant.effective.format(), emphasized = true)
                    Hint("${merchant.count} expense${if (merchant.count == 1) "" else "s"}")
                    if (merchant.people.isNotEmpty()) Hint("People: ${merchant.people.joinToString { it.name }}")
                    if (merchant.cards.isNotEmpty()) Hint("Cards: ${merchant.cards.joinToString { it.displayName }}")
                }
            }
            if (results.cards.isNotEmpty()) item { SectionTitle("Cards") }
            items(results.cards, key = { "c${it.card.id}" }) { match ->
                ListItem(
                    modifier = Modifier.clickable { actions.openCard(match.card.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(match.card.displayName) },
                    supportingContent = { Text("${match.card.bank} · ${match.count} expenses") },
                    trailingContent = { Text(match.spending.format(), style = MaterialTheme.typography.titleSmall) },
                )
            }
            if (results.expenses.isNotEmpty()) item { SectionTitle("Expenses (${results.expenses.size})") }
            items(results.expenses.take(100), key = { "e${it.id}" }) { expense ->
                ExpenseListItem(expense, expense.cardId?.let { data?.cardsById?.get(it) }, onClick = { actions.openExpense(expense.id) })
            }
            if (results.incomes.isNotEmpty()) item { SectionTitle("Income") }
            items(results.incomes.take(50), key = { "i${it.id}" }) { income -> IncomeListItem(income, onClick = { actions.openIncome(income.id) }) }
            if (results.transactions.isNotEmpty()) item { SectionTitle("Ledger entries") }
            items(results.transactions.take(100), key = { "t${it.entry.id}" }) { match ->
                Column {
                    LedgerEntryRow(
                        entry = match.entry,
                        personName = match.person?.name,
                        onClick = {
                            val expenseId = match.entry.expenseId
                            if (expenseId != null) actions.openExpense(expenseId) else actions.openEntry(match.entry.personId, match.entry.id)
                        },
                    )
                }
            }
        }
    }
}
