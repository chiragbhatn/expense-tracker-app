package io.github.chiragbhatn.expensetracker.ui.udhaar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.chiragbhatn.expensetracker.domain.LedgerSummary
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.ShareMessages
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.AmountField
import io.github.chiragbhatn.expensetracker.ui.components.ChoiceChips
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.DateField
import io.github.chiragbhatn.expensetracker.ui.components.DropdownField
import io.github.chiragbhatn.expensetracker.ui.components.FormScreen
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.NameDialog
import io.github.chiragbhatn.expensetracker.ui.components.TextInput
import io.github.chiragbhatn.expensetracker.ui.today
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun explain(type: LedgerType, direction: UdhaarDirection, name: String): String = when (type) {
    LedgerType.UDHAAR_GIVEN -> "You lent money to $name. They owe you more."
    LedgerType.UDHAAR_TAKEN -> "$name lent you money. You owe them; it is payable."
    LedgerType.PAYMENT_RECEIVED -> "$name paid you. It reduces what they owe; anything beyond that becomes their credit."
    LedgerType.PAYMENT_MADE -> "You paid $name. It reduces what you owe them."
    LedgerType.SETTLEMENT -> "Settles the balance with $name."
    LedgerType.ADJUSTMENT -> if (direction == UdhaarDirection.GAVE) "Increases what $name owes you." else "Decreases what $name owes you."
    LedgerType.EXPENSE_SHARE -> "Their share of an expense."
}

/**
 * Records udhaar or a payment with a person. [personId] may be null, in
 * which case the person is chosen here. [entryId] edits an existing entry.
 */
@Composable
fun EntryFormScreen(personId: Long?, initialType: LedgerType, entryId: Long?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val data = appData()
    val scope = rememberCoroutineScope()
    val today = today()

    var loaded by rememberSaveable { mutableStateOf(entryId == null) }
    var selectedPerson by rememberSaveable { mutableStateOf(personId) }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var direction by rememberSaveable { mutableStateOf(initialType.fixedDirection ?: UdhaarDirection.GAVE) }
    var amount by rememberSaveable { mutableStateOf("") }
    var dateDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var note by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var addingPerson by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(data, entryId) {
        if (!loaded && data != null) {
            data.entries.firstOrNull { it.id == entryId }?.let { entry ->
                selectedPerson = entry.personId
                type = entry.type
                direction = entry.direction
                amount = entry.amount.toInputString()
                dateDay = entry.date.toEpochDay()
                note = entry.note
            }
            loaded = true
        }
    }

    val title = when {
        entryId != null -> "Edit ${type.label.lowercase()}"
        type == LedgerType.PAYMENT_RECEIVED || type == LedgerType.PAYMENT_MADE -> "Record payment"
        else -> "Add udhaar"
    }
    FormScreen(
        title = title,
        onBack = onDone,
        actions = {
            if (entryId != null) {
                IconButton(onClick = { confirmingDelete = true }, modifier = Modifier.testTag(TestTags.DELETE)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                }
            }
        },
    ) {
        if (data == null || !loaded) {
            Loading()
            return@FormScreen
        }
        val person = selectedPerson?.let(data.peopleById::get)
        DropdownField(
            label = "Person",
            selected = person,
            options = listOf(null) + data.people,
            optionLabel = { it?.name ?: "Choose a person" },
            onSelect = { selectedPerson = it?.id },
            tag = "entry_person",
        )
        TextButton(onClick = { addingPerson = true }) { Text("Add a new person") }
        if (person != null) {
            val summary = LedgerSummary.of(data.entriesByPerson[person.id].orEmpty())
            Hint("Now: ${ShareMessages.headline(person.name, summary)}")
        }

        val types = if (entryId == null) LedgerType.manual else LedgerType.manual + LedgerType.SETTLEMENT
        DropdownField(
            label = "Type",
            selected = type,
            options = types,
            optionLabel = { it.label },
            onSelect = {
                type = it
                it.fixedDirection?.let { fixed -> direction = fixed }
            },
            tag = "entry_type",
        )
        if (type.fixedDirection == null) {
            ChoiceChips(
                options = UdhaarDirection.entries,
                selected = direction,
                label = { if (it == UdhaarDirection.GAVE) "They owe more" else "They owe less" },
                onSelect = { direction = it },
            )
        }
        Hint(explain(type, direction, person?.name ?: "them"))

        val parsed = Money.parse(amount)
        AmountField(
            value = amount,
            onValueChange = { amount = it },
            errorText = if (showErrors && parsed?.isPositive != true) "Enter an amount" else null,
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag(TestTags.AMOUNT_INPUT),
        )
        DateField(date = LocalDate.ofEpochDay(dateDay), onDateChange = { dateDay = it.toEpochDay() })
        TextInput(value = note, onValueChange = { note = it }, label = "Note (optional)", singleLine = false, tag = TestTags.NOTE_INPUT)
        if (showErrors && person == null) Text("Choose a person", color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                val target = person
                if (target == null || parsed == null || !parsed.isPositive) {
                    showErrors = true
                } else {
                    scope.launch {
                        val date = LocalDate.ofEpochDay(dateDay)
                        if (entryId == null) {
                            container.people.addEntry(target.id, type, direction, parsed, date, note)
                        } else {
                            container.people.updateEntry(entryId, type, direction, parsed, date, note)
                        }
                        onDone()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SAVE),
        ) { Text("Save") }
    }

    if (addingPerson) {
        NameDialog(
            title = "Add a person",
            confirmLabel = "Add",
            onConfirm = { name ->
                addingPerson = false
                scope.launch { selectedPerson = container.people.addPerson(name) }
            },
            onDismiss = { addingPerson = false },
        )
    }
    if (confirmingDelete && entryId != null) {
        ConfirmDialog(
            title = "Delete this entry?",
            message = "The balance will be recalculated without it.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmingDelete = false
                scope.launch {
                    container.people.deleteEntry(entryId)
                    onDone()
                }
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}
