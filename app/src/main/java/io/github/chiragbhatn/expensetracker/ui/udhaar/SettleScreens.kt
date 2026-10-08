package io.github.chiragbhatn.expensetracker.ui.udhaar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.LedgerSummary
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.Settlement
import io.github.chiragbhatn.expensetracker.domain.SettlementKind
import io.github.chiragbhatn.expensetracker.domain.ShareKind
import io.github.chiragbhatn.expensetracker.domain.ShareMessages
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.appSettings
import io.github.chiragbhatn.expensetracker.ui.components.AmountField
import io.github.chiragbhatn.expensetracker.ui.components.DateField
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.FormScreen
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.TextInput
import io.github.chiragbhatn.expensetracker.ui.components.balanceColor
import io.github.chiragbhatn.expensetracker.ui.shareText
import io.github.chiragbhatn.expensetracker.ui.toast
import io.github.chiragbhatn.expensetracker.ui.today
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Share Balance: choose the current balance, a detailed statement or this
 * month's summary, edit the wording if needed, then send it with any app.
 */
@Composable
fun ShareBalanceScreen(personId: Long, onBack: () -> Unit, onEditWording: () -> Unit) {
    val data = appData()
    val settings = appSettings()
    val context = LocalContext.current
    val today = today()
    val person = data?.peopleById?.get(personId)
    var kind by rememberSaveable { mutableStateOf(ShareKind.CURRENT_BALANCE) }

    FormScreen(title = "Share balance", onBack = onBack) {
        if (data == null) {
            Loading()
            return@FormScreen
        }
        if (person == null) {
            EmptyState("Person not found", "They may have been deleted.")
            return@FormScreen
        }
        val entries = data.entriesByPerson[personId].orEmpty()
        val summary = remember(entries) { LedgerSummary.of(entries) }
        val templates = settings.shareTemplates
        // A new choice (or new default wording) starts a fresh message; edits stay until then.
        var text by rememberSaveable(kind, templates, summary) {
            mutableStateOf(
                when (kind) {
                    ShareKind.CURRENT_BALANCE -> ShareMessages.currentBalance(person.name, summary, templates)
                    ShareKind.DETAILED_STATEMENT -> ShareMessages.detailedStatement(person.name, entries, today)
                    ShareKind.MONTHLY_SUMMARY -> ShareMessages.monthlySummary(person.name, entries, YearMonth.from(today))
                },
            )
        }

        Text(ShareMessages.headline(person.name, summary), style = MaterialTheme.typography.titleLarge, color = balanceColor(summary))
        Column(Modifier.selectableGroup()) {
            ShareKind.entries.forEach { option ->
                OptionRow(option.label, selected = kind == option, tag = "share_kind_${option.name.lowercase()}") { kind = option }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Message (you can edit it)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SHARE_MESSAGE),
            minLines = 4,
        )
        Button(
            onClick = {
                shareText(context, text, subject = "Balance with ${person.name}")
                onBack()
            },
            enabled = text.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SHARE_SEND),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Text("Share", Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = onEditWording) { Text("Change the default wording") }
    }
}

/**
 * Settles up with a person: the full balance, or another amount, which is a
 * partial settlement when it is less and leaves them with credit when it is more.
 */
@Composable
fun SettleScreen(personId: Long, onDone: () -> Unit) {
    val data = appData()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val today = today()
    val person = data?.peopleById?.get(personId)

    var partial by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var dateDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var note by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    FormScreen(title = person?.let { "Settle with ${it.name}" } ?: "Settle", onBack = onDone) {
        if (data == null) {
            Loading()
            return@FormScreen
        }
        if (person == null) {
            EmptyState("Person not found", "They may have been deleted.")
            return@FormScreen
        }
        val entries = data.entriesByPerson[personId].orEmpty()
        val summary = remember(entries) { LedgerSummary.of(entries) }
        val full = Settlement.fullAmount(summary)
        val theyPay = Settlement.directionFor(summary) == UdhaarDirection.GOT
        val amount = if (partial) Money.parse(amountText) else full
        val preview = amount?.takeIf { it.isPositive }?.let { Settlement.preview(summary, it) }

        Text(ShareMessages.headline(person.name, summary), style = MaterialTheme.typography.titleLarge, color = balanceColor(summary))
        Hint(if (theyPay) "${person.name} pays you." else "You pay ${person.name}.")
        Column(Modifier.selectableGroup()) {
            OptionRow("Full amount ${full.format()}", selected = !partial, tag = TestTags.SETTLE_FULL) { partial = false }
            OptionRow("Another amount (partial or extra)", selected = partial, tag = TestTags.SETTLE_PARTIAL) { partial = true }
        }
        if (partial) {
            AmountField(value = amountText, onValueChange = { amountText = it }, label = "Amount", modifier = Modifier.testTag(TestTags.SETTLE_AMOUNT))
        }
        DateField(date = LocalDate.ofEpochDay(dateDay), onDateChange = { dateDay = it.toEpochDay() })
        TextInput(value = note, onValueChange = { note = it }, label = "Note (optional)", singleLine = false, tag = TestTags.NOTE_INPUT)
        if (amount != null && preview != null) {
            val text = buildString {
                append(
                    when (Settlement.kind(summary, amount)) {
                        SettlementKind.FULL -> "Full settlement. "
                        SettlementKind.PARTIAL -> "Partial settlement. "
                        SettlementKind.EXTRA -> "Extra payment. "
                    },
                )
                append("After this: outstanding ${preview.receivable.format()}")
                if (preview.credit.isPositive) append(", ${person.name} has ${preview.credit.format()} credit")
                if (preview.payable.isPositive) append(", you still owe ${preview.payable.format()}")
                append(".")
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(TestTags.SETTLE_PREVIEW))
        }
        Button(
            onClick = {
                amount?.let { chosen ->
                    saving = true
                    scope.launch {
                        try {
                            val result = container.people.settle(personId, chosen, LocalDate.ofEpochDay(dateDay), note)
                            toast(
                                context,
                                if (result.kind == SettlementKind.EXTRA && result.after.credit.isPositive) {
                                    "${person.name} has ${result.after.credit.format()} credit."
                                } else {
                                    ShareMessages.headline(person.name, result.after)
                                },
                            )
                            onDone()
                        } finally {
                            saving = false
                        }
                    }
                }
            },
            enabled = !saving && amount != null && amount.isPositive,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SETTLE_CONFIRM),
        ) { Text("Settle") }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, tag: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(tag)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}
