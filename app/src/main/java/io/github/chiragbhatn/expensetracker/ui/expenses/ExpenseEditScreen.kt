@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.chiragbhatn.expensetracker.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.ui.AppViewModelProvider
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.components.AmountField
import io.github.chiragbhatn.expensetracker.ui.components.CashbackBreakdownCard
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.DateField
import io.github.chiragbhatn.expensetracker.ui.components.NameDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditScreen(
    onDone: () -> Unit,
    viewModel: ExpenseEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = viewModel.form
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onDone() }

    var addingPerson by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Add expense" else "Edit expense") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete expense")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AmountField(
                value = form.amountText,
                onValueChange = viewModel::onAmountChange,
                modifier = Modifier.testTag(TestTags.AMOUNT_INPUT),
                errorText = state.amountError,
                textStyle = MaterialTheme.typography.headlineSmall,
            )
            MerchantInput(
                merchant = form.merchant,
                errorText = state.merchantError,
                suggestions = state.suggestions,
                onMerchantChange = viewModel::onMerchantChange,
            )
            PaymentMethodSelector(form.paymentMethod, onSelect = viewModel::onPaymentMethodChange)
            PaidForSelector(
                people = state.people,
                selectedId = state.paidFor?.id,
                onSelect = viewModel::onPaidForChange,
                onAddPerson = { addingPerson = true },
            )
            state.preview?.let { preview ->
                CashbackBreakdownCard(
                    posting = preview,
                    quote = state.quote,
                    merchant = form.merchant,
                    paidFor = state.paidFor,
                    onUseCurrentRule = viewModel::useCurrentRule,
                )
            }
            DateField(date = form.date, onDateChange = viewModel::onDateChange)
            OutlinedTextField(
                value = form.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Button(
                onClick = viewModel::save,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SAVE_EXPENSE),
            ) { Text("Save") }
        }
    }

    if (addingPerson) {
        NameDialog(
            title = "Who did you pay for?",
            confirmLabel = "Add",
            onConfirm = { name ->
                viewModel.addPerson(name)
                addingPerson = false
            },
            onDismiss = { addingPerson = false },
        )
    }
    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete expense?",
            message = state.paidFor?.let { "This also removes ${it.name}'s udhaar entry for this payment." }
                ?: "This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmingDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MerchantInput(
    merchant: String,
    errorText: String?,
    suggestions: List<MerchantSuggestion>,
    onMerchantChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = merchant,
            onValueChange = onMerchantChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.MERCHANT_INPUT),
            label = { Text("Merchant") },
            singleLine = true,
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                val rule = suggestion.rule
                FilterChip(
                    selected = nameKey(suggestion.name) == nameKey(merchant),
                    onClick = { onMerchantChange(suggestion.name) },
                    label = {
                        Text(
                            if (rule != null && rule.enabled) {
                                "${suggestion.name} · ${rule.percentage.format()}"
                            } else {
                                suggestion.name
                            },
                        )
                    },
                    modifier = Modifier.testTag(TestTags.merchantSuggestion(suggestion.name)),
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodSelector(selected: PaymentMethod, onSelect: (PaymentMethod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Paid with", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val methods = PaymentMethod.entries
            methods.forEachIndexed { index, method ->
                SegmentedButton(
                    selected = method == selected,
                    onClick = { onSelect(method) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = methods.size),
                    modifier = Modifier.testTag(TestTags.paymentMethod(method)),
                ) { Text(method.label) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaidForSelector(
    people: List<Person>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onAddPerson: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Paid for", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("Myself") },
                modifier = Modifier.testTag(TestTags.paidFor("Myself")),
            )
            people.forEach { person ->
                FilterChip(
                    selected = person.id == selectedId,
                    onClick = { onSelect(person.id) },
                    label = { Text(person.name) },
                    modifier = Modifier.testTag(TestTags.paidFor(person.name)),
                )
            }
            AssistChip(
                onClick = onAddPerson,
                label = { Text("Add person") },
                leadingIcon = {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
                },
                modifier = Modifier.testTag(TestTags.ADD_PERSON),
            )
        }
    }
}
