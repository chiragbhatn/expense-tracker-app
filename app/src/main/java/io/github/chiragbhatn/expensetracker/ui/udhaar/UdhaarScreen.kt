package io.github.chiragbhatn.expensetracker.ui.udhaar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chiragbhatn.expensetracker.domain.PersonBalance
import io.github.chiragbhatn.expensetracker.ui.AppViewModelProvider
import io.github.chiragbhatn.expensetracker.ui.components.AmountLine
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.InitialAvatar
import io.github.chiragbhatn.expensetracker.ui.components.NameDialog
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UdhaarScreen(
    bottomBar: @Composable () -> Unit,
    onOpenPerson: (Long) -> Unit,
    viewModel: UdhaarViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addingPerson by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.addedPerson.collect { onOpenPerson(it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Udhaar") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingPerson = true },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Add person") },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = listPadding(padding)) {
            val loaded = state ?: return@LazyColumn
            item { TotalsCard(loaded, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            if (loaded.balances.isEmpty()) {
                item {
                    EmptyState(
                        title = "No udhaar yet",
                        message = "When you pay for someone, choose them under “Paid for” and they will show up here.",
                    )
                }
            }
            items(loaded.balances, key = { it.person.id }) { balance ->
                PersonBalanceRow(balance, onClick = { onOpenPerson(balance.person.id) })
            }
        }
    }

    if (addingPerson) {
        NameDialog(
            title = "Add person",
            confirmLabel = "Add",
            onConfirm = { name ->
                viewModel.addPerson(name)
                addingPerson = false
            },
            onDismiss = { addingPerson = false },
        )
    }
}

@Composable
private fun TotalsCard(state: UdhaarUiState, modifier: Modifier = Modifier) {
    val colors = LocalAmountColors.current
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountLine(
                label = "Money to receive",
                amount = state.moneyToReceive.format(),
                color = colors.positive,
                emphasized = true,
            )
            AmountLine(label = "You owe others", amount = state.moneyToGive.format(), color = colors.negative)
            Text(
                text = "When you pay for someone by card they owe you the full amount; your card's cashback stays with you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PersonBalanceRow(balance: PersonBalance, onClick: () -> Unit) {
    val colors = LocalAmountColors.current
    val amount = balance.balance
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { InitialAvatar(balance.person.name) },
        headlineContent = { Text(balance.person.name) },
        supportingContent = {
            Text(
                when {
                    amount.isPositive -> "Owes you"
                    amount.isNegative -> "You owe"
                    else -> "Settled up"
                },
            )
        },
        trailingContent = {
            Text(
                text = amount.abs().format(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    amount.isPositive -> colors.positive
                    amount.isNegative -> colors.negative
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
}
