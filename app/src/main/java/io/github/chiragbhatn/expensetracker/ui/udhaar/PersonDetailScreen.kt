package io.github.chiragbhatn.expensetracker.ui.udhaar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.domain.UdhaarEntry
import io.github.chiragbhatn.expensetracker.ui.AppViewModelProvider
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.components.AmountField
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.DateField
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.NameDialog
import io.github.chiragbhatn.expensetracker.ui.components.SectionTitle
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.formatShort
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    viewModel: PersonDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val renameError by viewModel.renameError.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { if (deleted) onBack() }

    var entryDirection by rememberSaveable { mutableStateOf<UdhaarDirection?>(null) }
    var entryAmount by rememberSaveable { mutableStateOf("") }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<UdhaarEntry?>(null) }

    val loaded = state
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loaded?.person?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { renaming = true }, enabled = loaded != null) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = { confirmingDelete = true }, enabled = loaded != null) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete person")
                    }
                },
            )
        },
    ) { padding ->
        if (loaded == null) return@Scaffold
        LazyColumn(contentPadding = listPadding(padding, hasFab = false)) {
            item {
                BalanceCard(
                    state = loaded,
                    onGave = {
                        entryAmount = ""
                        entryDirection = UdhaarDirection.GAVE
                    },
                    onGot = {
                        entryAmount = ""
                        entryDirection = UdhaarDirection.GOT
                    },
                    onSettle = {
                        entryAmount = loaded.balance.abs().toInputString()
                        entryDirection = if (loaded.balance.isPositive) UdhaarDirection.GOT else UdhaarDirection.GAVE
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item { SectionTitle("History", Modifier.padding(top = 8.dp)) }
            if (loaded.entries.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing recorded yet",
                        message = "Use You gave or You got to record money given or received.",
                    )
                }
            }
            items(loaded.entries, key = { it.id }) { entry ->
                EntryRow(
                    entry = entry,
                    onClick = {
                        val expenseId = entry.expenseId
                        if (expenseId != null) onOpenExpense(expenseId) else entryToDelete = entry
                    },
                )
            }
        }
    }

    val person = loaded?.person ?: return
    entryDirection?.let { direction ->
        UdhaarEntryDialog(
            title = if (direction == UdhaarDirection.GAVE) "You gave ${person.name}" else "You got from ${person.name}",
            initialAmount = entryAmount,
            onConfirm = { amount, date, note ->
                viewModel.addEntry(direction, amount, date, note)
                entryDirection = null
            },
            onDismiss = { entryDirection = null },
        )
    }
    if (renaming) {
        NameDialog(
            title = "Rename",
            confirmLabel = "Save",
            initialName = person.name,
            errorText = renameError,
            onConfirm = { name -> viewModel.rename(name) { renaming = false } },
            onDismiss = {
                renaming = false
                viewModel.clearRenameError()
            },
        )
    }
    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete ${person.name}?",
            message = "Their udhaar history will be deleted. Expenses you paid for them stay in your expenses.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmingDelete = false
                viewModel.deletePerson()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
    entryToDelete?.let { entry ->
        ConfirmDialog(
            title = "Delete this entry?",
            message = "${if (entry.direction == UdhaarDirection.GAVE) "You gave" else "You got"} " +
                "${entry.amount.format()} on ${entry.date.formatShort()}.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteEntry(entry)
                entryToDelete = null
            },
            onDismiss = { entryToDelete = null },
        )
    }
}

@Composable
private fun BalanceCard(
    state: PersonDetailUiState,
    onGave: () -> Unit,
    onGot: () -> Unit,
    onSettle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAmountColors.current
    val name = state.person.name
    val balance = state.balance
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = when {
                    balance.isPositive -> "$name owes you"
                    balance.isNegative -> "You owe $name"
                    else -> "All settled up"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = balance.abs().format(),
                modifier = Modifier.testTag(TestTags.PERSON_BALANCE),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    balance.isPositive -> colors.positive
                    balance.isNegative -> colors.negative
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onGave, modifier = Modifier.weight(1f)) { Text("You gave") }
                Button(onClick = onGot, modifier = Modifier.weight(1f)) { Text("You got") }
            }
            if (!balance.isZero) {
                TextButton(onClick = onSettle) { Text("Settle up ${balance.abs().format()}") }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: UdhaarEntry, onClick: () -> Unit) {
    val colors = LocalAmountColors.current
    val gave = entry.direction == UdhaarDirection.GAVE
    val color = if (gave) colors.negative else colors.positive
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = if (gave) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = color,
            )
        },
        headlineContent = {
            Text(
                when {
                    entry.expenseMerchant != null -> "Paid on ${entry.expenseMerchant}"
                    gave -> "You gave"
                    else -> "You got"
                },
            )
        },
        supportingContent = {
            Text(listOf(entry.date.formatShort(), entry.note).filter { it.isNotBlank() }.joinToString(" · "))
        },
        trailingContent = {
            Text(
                text = entry.amount.format(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        },
    )
}

@Composable
private fun UdhaarEntryDialog(
    title: String,
    initialAmount: String,
    onConfirm: (Money, LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(initialAmount) }
    var note by rememberSaveable { mutableStateOf("") }
    var epochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val amount = Money.parse(amountText)?.takeIf { it.isPositive }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier.testTag(TestTags.AMOUNT_INPUT),
                )
                DateField(date = LocalDate.ofEpochDay(epochDay), onDateChange = { epochDay = it.toEpochDay() })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let { onConfirm(it, LocalDate.ofEpochDay(epochDay), note) } },
                enabled = amount != null,
                modifier = Modifier.testTag(TestTags.DIALOG_CONFIRM),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
