@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.chiragbhatn.expensetracker.ui.udhaar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.BalanceState
import io.github.chiragbhatn.expensetracker.domain.LedgerSummary
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.ShareMessages
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.AmountLine
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.DateField
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.InfoCard
import io.github.chiragbhatn.expensetracker.ui.components.InitialAvatar
import io.github.chiragbhatn.expensetracker.ui.components.LedgerEntryRow
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.SectionTitle
import io.github.chiragbhatn.expensetracker.ui.components.TextInput
import io.github.chiragbhatn.expensetracker.ui.components.balanceColor
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.formatShort
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import io.github.chiragbhatn.expensetracker.ui.today
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PersonActions(
    val back: () -> Unit,
    val edit: (Long) -> Unit,
    val addExpense: (Long) -> Unit,
    val addEntry: (Long, LedgerType) -> Unit,
    val openEntry: (Long, Long) -> Unit,
    val openExpense: (Long) -> Unit,
    val shareBalance: (Long) -> Unit,
    val settle: (Long) -> Unit,
)

@Composable
fun PersonScreen(personId: Long, actions: PersonActions) {
    val data = appData()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val today = today()

    var menuOpen by remember { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var addingReminder by rememberSaveable { mutableStateOf(false) }

    val person = data?.peopleById?.get(personId)
    val entries = data?.entriesByPerson?.get(personId).orEmpty()
    val summary = remember(entries) { LedgerSummary.of(entries) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person?.name ?: "") },
                navigationIcon = { IconButton(onClick = actions.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (person != null) {
                        IconButton(onClick = { actions.edit(personId) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${person.name}") }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Add expense for ${person.name}") }, onClick = { menuOpen = false; actions.addExpense(personId) })
                            DropdownMenuItem(text = { Text("Udhaar given") }, onClick = { menuOpen = false; actions.addEntry(personId, LedgerType.UDHAAR_GIVEN) })
                            DropdownMenuItem(text = { Text("Udhaar taken") }, onClick = { menuOpen = false; actions.addEntry(personId, LedgerType.UDHAAR_TAKEN) })
                            DropdownMenuItem(text = { Text("Payment made") }, onClick = { menuOpen = false; actions.addEntry(personId, LedgerType.PAYMENT_MADE) })
                            DropdownMenuItem(text = { Text("Adjustment") }, onClick = { menuOpen = false; actions.addEntry(personId, LedgerType.ADJUSTMENT) })
                            DropdownMenuItem(text = { Text("Set a reminder") }, onClick = { menuOpen = false; addingReminder = true })
                            DropdownMenuItem(text = { Text("Delete ${person.name}") }, onClick = { menuOpen = false; deleting = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@Scaffold
        }
        if (person == null) {
            EmptyState("Person not found", "They may have been deleted.", Modifier.padding(padding))
            return@Scaffold
        }
        val reminders = data.reminders.filter { it.personId == personId }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding, hasFab = false)) {
            item { ProfileHeader(person, person.photoPath?.let(container.files::photo)) }
            item {
                BalanceCard(
                    name = person.name,
                    summary = summary,
                    onShare = { actions.shareBalance(personId) },
                    onRecordPayment = {
                        val type = if (summary.balance.isNegative) LedgerType.PAYMENT_MADE else LedgerType.PAYMENT_RECEIVED
                        actions.addEntry(personId, type)
                    },
                    onSettle = { actions.settle(personId) },
                    onAddUdhaar = { actions.addEntry(personId, LedgerType.UDHAAR_GIVEN) },
                )
            }
            if (reminders.isNotEmpty()) {
                item { SectionTitle("Reminders") }
                items(reminders, key = { "r${it.id}" }) { reminder ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = reminder.done, onCheckedChange = { done -> scope.launch { container.people.setReminderDone(reminder.id, done) } })
                        Column(Modifier.weight(1f)) {
                            Text(reminder.title)
                            Hint("Due ${reminder.dueDate.formatShort()}${if (reminder.note.isNotBlank()) " · ${reminder.note}" else ""}")
                        }
                        TextButton(onClick = { scope.launch { container.people.deleteReminder(reminder.id) } }) { Text("Remove") }
                    }
                }
            }
            item { SectionTitle("Ledger") }
            if (entries.isEmpty()) {
                item { Hint("No transactions yet.", Modifier.padding(horizontal = 16.dp)) }
            }
            items(entries, key = { it.id }) { entry ->
                LedgerEntryRow(entry, onClick = {
                    if (entry.type == LedgerType.EXPENSE_SHARE && entry.expenseId != null) actions.openExpense(entry.expenseId) else actions.openEntry(personId, entry.id)
                })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }

        if (deleting) {
            ConfirmDialog(
                title = "Delete ${person.name}?",
                message = "Their ledger and reminders are deleted. Expenses shared with them stay, as your own expenses.",
                confirmLabel = "Delete",
                onConfirm = {
                    deleting = false
                    scope.launch {
                        container.people.delete(personId)
                        actions.back()
                    }
                },
                onDismiss = { deleting = false },
            )
        }
        if (addingReminder) {
            ReminderDialog(
                defaultTitle = if (summary.receivable.isPositive) "Ask ${person.name} for ${summary.receivable.format()}" else "Follow up with ${person.name}",
                today = today,
                onSave = { title, date, note ->
                    addingReminder = false
                    scope.launch { container.people.addReminder(title, note, date, personId) }
                },
                onDismiss = { addingReminder = false },
            )
        }
    }
}

@Composable
private fun ProfileHeader(person: Person, photo: java.io.File?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InitialAvatar(person.name, photo = photo, size = 72.dp)
        Text(person.name, style = MaterialTheme.typography.headlineSmall)
        val contact = listOf(person.phone, person.email).filter { it.isNotBlank() }.joinToString(" · ")
        if (contact.isNotEmpty()) Text(contact, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (person.address.isNotBlank()) Hint(person.address)
        if (person.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                person.tags.forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
            }
        }
        if (person.notes.isNotBlank()) Hint(person.notes)
    }
}

@Composable
private fun BalanceCard(
    name: String,
    summary: LedgerSummary,
    onShare: () -> Unit,
    onRecordPayment: () -> Unit,
    onSettle: () -> Unit,
    onAddUdhaar: () -> Unit,
) {
    val colors = LocalAmountColors.current
    InfoCard(title = null) {
        Text(
            ShareMessages.headline(name, summary),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = balanceColor(summary),
            modifier = Modifier.testTag(TestTags.PERSON_HEADLINE),
        )
        AmountLine("Total they owe", summary.totalDue.format(), amountTag = TestTags.TOTAL_DUE)
        AmountLine("Total they have paid", summary.totalPaid.format(), amountTag = TestTags.TOTAL_PAID)
        HorizontalDivider()
        AmountLine(
            label = "Current balance",
            amount = when (summary.state) {
                BalanceState.OWES_YOU -> summary.receivable.format()
                BalanceState.SETTLED -> Money.ZERO.format()
                BalanceState.HAS_CREDIT -> "${summary.credit.format()} credit"
                BalanceState.YOU_OWE -> "−${summary.payable.format()}"
            },
            amountTag = TestTags.PERSON_BALANCE,
            emphasized = true,
            color = balanceColor(summary),
        )
        if (summary.payable.isPositive) Hint("Payable: you owe $name ${summary.payable.format()}.", color = colors.negative)
        if (summary.credit.isPositive) Hint("Credit: $name paid ${summary.credit.format()} extra; it will be adjusted against their next expense.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onShare, modifier = Modifier.weight(1f).testTag(TestTags.SHARE_BALANCE)) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text("Share Balance", Modifier.padding(start = 6.dp))
            }
            FilledTonalButton(onClick = onRecordPayment, modifier = Modifier.weight(1f).testTag(TestTags.RECORD_PAYMENT)) {
                Text("Record Payment")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSettle, modifier = Modifier.weight(1f).testTag(TestTags.SETTLE), enabled = !summary.balance.isZero) { Text("Settle") }
            OutlinedButton(onClick = onAddUdhaar, modifier = Modifier.weight(1f).testTag(TestTags.ADD_UDHAAR)) { Text("Add udhaar") }
        }
    }
}

@Composable
private fun ReminderDialog(defaultTitle: String, today: LocalDate, onSave: (String, LocalDate, String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(defaultTitle) }
    var note by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf(today.plusDays(1)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(value = title, onValueChange = { title = it }, label = "Remind me to")
                DateField(date = date, onDateChange = { date = it }, label = "On")
                TextInput(value = note, onValueChange = { note = it }, label = "Note (optional)")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, date, note) }, enabled = title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
