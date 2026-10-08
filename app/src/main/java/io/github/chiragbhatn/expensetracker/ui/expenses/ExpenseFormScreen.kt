@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.chiragbhatn.expensetracker.ui.expenses

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.ReceiptDraft
import io.github.chiragbhatn.expensetracker.domain.ReceiptParser
import io.github.chiragbhatn.expensetracker.ui.AppViewModelProvider
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.AmountField
import io.github.chiragbhatn.expensetracker.ui.components.Banner
import io.github.chiragbhatn.expensetracker.ui.components.CashbackBreakdownCard
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.DateField
import io.github.chiragbhatn.expensetracker.ui.components.DropdownField
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.NameDialog
import io.github.chiragbhatn.expensetracker.ui.components.PercentField
import io.github.chiragbhatn.expensetracker.ui.components.SectionTitle
import io.github.chiragbhatn.expensetracker.ui.components.ShareLine
import io.github.chiragbhatn.expensetracker.ui.components.TextInput
import io.github.chiragbhatn.expensetracker.ui.toast
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExpenseFormScreen(
    onDone: () -> Unit,
    startScan: Boolean = false,
    viewModel: ExpenseFormViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(finished) { if (finished) onDone() }
    LaunchedEffect(error) {
        error?.let {
            toast(context, it)
            viewModel.clearError()
        }
    }

    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var choosingScanSource by rememberSaveable { mutableStateOf(startScan) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val knownMerchants = appData()?.merchants.orEmpty()

    fun recognise(uri: Uri, photo: File?) {
        scanning = true
        scope.launch {
            try {
                val text = ReceiptScanner.readText(context, uri)
                val draft = ReceiptParser.parse(text, knownMerchants)
                if (draft.isEmpty) toast(context, "Couldn't read this receipt. Enter the details yourself.")
                viewModel.onReceiptRead(draft, photo)
            } catch (e: Exception) {
                toast(context, "Couldn't read this receipt (${e.message}).")
            } finally {
                scanning = false
            }
        }
    }

    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pendingPhoto?.let(::File)
        if (taken && file != null) recognise(Uri.fromFile(file), file)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val copy = ReceiptScanner.copyToCache(context, uri)
                recognise(copy?.let(Uri::fromFile) ?: uri, copy)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state?.isNew != false) "Add expense" else "Edit expense") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { choosingScanSource = true }, modifier = Modifier.testTag(TestTags.SCAN_RECEIPT)) {
                        Icon(Icons.Filled.DocumentScanner, contentDescription = "Scan receipt")
                    }
                    if (state?.isNew == false) {
                        IconButton(onClick = { confirmingDelete = true }, modifier = Modifier.testTag(TestTags.DELETE)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete expense")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = state
        if (current == null) {
            Loading(Modifier.padding(padding))
            return@Scaffold
        }
        ExpenseFormContent(
            state = current,
            viewModel = viewModel,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        )
    }

    if (scanning) {
        AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Reading receipt…") }, text = { Text("This happens on your phone; nothing is uploaded.") })
    }

    if (choosingScanSource) {
        AlertDialog(
            onDismissRequest = { choosingScanSource = false },
            title = { Text("Scan a receipt") },
            text = { Text("Take a photo or choose one. You'll check what was read before anything is saved.") },
            confirmButton = {
                TextButton(onClick = {
                    choosingScanSource = false
                    runCatching {
                        val (file, uri) = ReceiptScanner.newPhoto(context)
                        pendingPhoto = file.path
                        takePicture.launch(uri)
                    }.onFailure { toast(context, "No camera app available") }
                }) { Text("Camera") }
            },
            dismissButton = {
                TextButton(onClick = {
                    choosingScanSource = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Gallery") }
            },
        )
    }

    viewModel.receiptDraft?.let { draft ->
        ReceiptReviewDialog(draft = draft, onApply = viewModel::applyReceipt, onDismiss = viewModel::dismissReceipt)
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete this expense?",
            message = "Everyone's share of it is removed from their ledger as well.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmingDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun ExpenseFormContent(state: ExpenseFormUiState, viewModel: ExpenseFormViewModel, modifier: Modifier = Modifier) {
    val form = state.form
    var pickingPeople by rememberSaveable { mutableStateOf(false) }
    var addingPerson by rememberSaveable { mutableStateOf(false) }
    var editingCashback by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (form.receiptPath != null) Banner("Receipt attached. It will be kept with this expense.")

        AmountField(
            value = form.amountText,
            onValueChange = viewModel::onAmountChange,
            label = "Original amount",
            errorText = state.amountError,
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag(TestTags.AMOUNT_INPUT),
        )

        OutlinedTextField(
            value = form.merchant,
            onValueChange = viewModel::onMerchantChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.MERCHANT_INPUT),
            label = { Text("Merchant") },
            singleLine = true,
            isError = state.merchantError != null,
            supportingText = state.merchantError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        if (state.suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.suggestions.forEach { suggestion ->
                    val rule = suggestion.rule
                    AssistChip(
                        onClick = { viewModel.onMerchantChange(suggestion.name) },
                        label = {
                            Text(if (rule != null && rule.enabled) "${suggestion.name} · ${rule.percentage.format()}" else suggestion.name)
                        },
                        colors = if (rule != null && rule.enabled) {
                            AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        } else {
                            AssistChipDefaults.assistChipColors()
                        },
                        modifier = Modifier.testTag(TestTags.merchantSuggestion(suggestion.name)),
                    )
                }
            }
        }

        Text("Paid with", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PaymentMethod.entries.forEachIndexed { index, method ->
                SegmentedButton(
                    selected = form.paymentMethod == method,
                    onClick = { viewModel.onPaymentMethodChange(method) },
                    shape = SegmentedButtonDefaults.itemShape(index, PaymentMethod.entries.size),
                    modifier = Modifier.testTag(TestTags.paymentMethod(method)),
                ) { Text(method.label) }
            }
        }
        if (form.paymentMethod == PaymentMethod.CARD) {
            if (state.cards.isEmpty()) {
                Hint("Add your credit cards in Settings → Credit cards to track spending, bills and cashback per card.")
            } else {
                DropdownField(
                    label = "Card",
                    selected = state.cards.firstOrNull { it.id == form.cardId },
                    options = listOf(null) + state.cards,
                    optionLabel = { it?.displayName ?: "No card selected" },
                    onSelect = { viewModel.onCardChange(it?.id) },
                    tag = TestTags.CARD_FIELD,
                )
            }
        }

        state.amounts?.let { amounts ->
            CashbackBreakdownCard(
                amounts = amounts,
                quote = state.quote,
                merchant = form.merchant,
                shares = form.participants.mapNotNull { p -> state.shareFor(p.personId)?.let { ShareLine(p.name, it) } },
                myShare = state.split?.myShare?.takeIf { form.participants.isNotEmpty() },
                onUseCurrentRule = viewModel::useCurrentRule,
                customPercentage = form.customPercentage != null,
            )
            TextButton(onClick = { editingCashback = true }) {
                Text(if (form.customPercentage != null) "Change cashback" else "Enter cashback for this expense")
            }
        }

        DropdownField(
            label = "Category",
            selected = form.category,
            options = state.categories,
            optionLabel = { it },
            onSelect = viewModel::onCategoryChange,
            tag = TestTags.CATEGORY_FIELD,
        )
        DateField(date = form.date, onDateChange = viewModel::onDateChange)

        SplitSection(
            state = state,
            viewModel = viewModel,
            onPickPeople = { pickingPeople = true },
        )

        TextInput(
            value = form.note,
            onValueChange = viewModel::onNoteChange,
            label = "Note (optional)",
            singleLine = false,
            tag = TestTags.NOTE_INPUT,
        )

        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SAVE_EXPENSE),
        ) { Text("Save expense") }
    }

    if (pickingPeople) {
        PeoplePickerDialog(
            people = state.people,
            selected = form.participants.map { it.personId }.toSet(),
            onDone = { chosen ->
                viewModel.setParticipants(chosen)
                pickingPeople = false
            },
            onAddNew = {
                pickingPeople = false
                addingPerson = true
            },
            onDismiss = { pickingPeople = false },
        )
    }
    if (addingPerson) {
        NameDialog(
            title = "Add a person",
            confirmLabel = "Add",
            onConfirm = { name ->
                viewModel.addNewPerson(name)
                addingPerson = false
            },
            onDismiss = { addingPerson = false },
        )
    }
    if (editingCashback) {
        CashbackDialog(
            initial = form.customPercentage ?: state.percentage,
            ruleBased = form.customPercentage == null,
            onSave = { percentage ->
                viewModel.onCustomPercentage(percentage)
                editingCashback = false
            },
            onUseRules = {
                viewModel.onCustomPercentage(null)
                editingCashback = false
            },
            onDismiss = { editingCashback = false },
        )
    }
}

@Composable
private fun SplitSection(state: ExpenseFormUiState, viewModel: ExpenseFormViewModel, onPickPeople: () -> Unit) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Who is it for?", Modifier.padding(horizontal = 0.dp))
        if (form.participants.isEmpty()) {
            Hint("Just you. Add people to charge them a share; their share comes out of the amount after cashback.")
        }
        OutlinedButton(onClick = onPickPeople, modifier = Modifier.testTag(TestTags.SPLIT_ADD_PEOPLE)) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (form.participants.isEmpty()) "Add people" else "Change people", Modifier.padding(start = 8.dp))
        }
        if (form.participants.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Include my share", modifier = Modifier.weight(1f))
                Switch(
                    checked = form.includeMe,
                    onCheckedChange = viewModel::onIncludeMeChange,
                    modifier = Modifier.testTag(TestTags.SPLIT_INCLUDE_ME),
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = form.splitMode == SplitMode.EQUAL,
                    onClick = { viewModel.onSplitModeChange(SplitMode.EQUAL) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag(TestTags.SPLIT_MODE_EQUAL),
                ) { Text("Equally") }
                SegmentedButton(
                    selected = form.splitMode == SplitMode.CUSTOM,
                    onClick = { viewModel.onSplitModeChange(SplitMode.CUSTOM) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag(TestTags.SPLIT_MODE_CUSTOM),
                ) { Text("Custom amounts") }
            }
            if (form.includeMe) {
                ShareRow(
                    name = "You",
                    custom = form.splitMode == SplitMode.CUSTOM,
                    amountText = form.myAmountText,
                    computed = state.split?.myShare,
                    onChange = viewModel::onMyShareChange,
                    onRemove = null,
                )
            }
            form.participants.forEach { participant ->
                ShareRow(
                    name = participant.name,
                    custom = form.splitMode == SplitMode.CUSTOM,
                    amountText = participant.amountText,
                    computed = state.shareFor(participant.personId),
                    onChange = { viewModel.onShareChange(participant.personId, it) },
                    onRemove = { viewModel.removeParticipant(participant.personId) },
                )
            }
            state.splitError?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(TestTags.SPLIT_ERROR),
                )
                if (state.isLegacyShare) {
                    Hint("This expense was saved by version 1, which charged the full amount. Version 2 charges the amount after cashback.")
                }
                TextButton(onClick = viewModel::splitEqually) { Text("Split the effective amount equally") }
            }
        }
    }
}

@Composable
private fun ShareRow(
    name: String,
    custom: Boolean,
    amountText: String,
    computed: Money?,
    onChange: (String) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (custom) {
            AmountField(
                value = amountText,
                onValueChange = onChange,
                label = name,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.shareInput(name)),
            )
        } else {
            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(computed?.format() ?: "–", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(TestTags.shareInput(name)))
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove $name") }
        }
    }
}

@Composable
private fun PeoplePickerDialog(
    people: List<Person>,
    selected: Set<Long>,
    onDone: (List<Person>) -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who is it for?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (people.isEmpty()) Hint("No people yet. Add someone to start.")
                people.forEach { person ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { chosen = if (person.id in chosen) chosen - person.id else chosen + person.id }
                            .testTag(TestTags.pickPerson(person.name)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = person.id in chosen, onCheckedChange = null)
                        Text(person.name, Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onAddNew, modifier = Modifier.testTag(TestTags.ADD_PERSON)) { Text("Add a new person") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(people.filter { it.id in chosen }) }, modifier = Modifier.testTag(TestTags.DIALOG_CONFIRM)) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CashbackDialog(initial: Percentage, ruleBased: Boolean, onSave: (Percentage) -> Unit, onUseRules: () -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (initial.isZero) "" else initial.toInputString()) }
    val parsed = if (text.isBlank()) Percentage.ZERO else Percentage.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cashback for this expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PercentField(value = text, onValueChange = { text = it }, errorText = if (parsed == null) "Enter 0 to 100" else null)
                Hint("Only this expense changes. Cashback rules set the default for each merchant.")
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text("Use") }
        },
        dismissButton = {
            TextButton(onClick = if (ruleBased) onDismiss else onUseRules) { Text(if (ruleBased) "Cancel" else "Use the rules") }
        },
    )
}

@Composable
private fun ReceiptReviewDialog(draft: ReceiptDraft, onApply: (String?, Money?, java.time.LocalDate?) -> Unit, onDismiss: () -> Unit) {
    var merchant by rememberSaveable { mutableStateOf(draft.merchant.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(draft.amount?.toInputString().orEmpty()) }
    var date by remember { mutableStateOf(draft.date ?: java.time.LocalDate.now()) }
    var showText by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check what was read") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint("Receipts are read on your phone and may be misread. Correct anything that's wrong; nothing is saved until you save the expense.")
                TextInput(value = merchant, onValueChange = { merchant = it }, label = "Merchant", capitalization = KeyboardCapitalization.Words)
                AmountField(value = amount, onValueChange = { amount = it }, label = "Total")
                DateField(date = date, onDateChange = { date = it })
                TextButton(onClick = { showText = !showText }) { Text(if (showText) "Hide recognised text" else "Show recognised text") }
                if (showText) Text(draft.text.ifBlank { "(no text found)" }, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(merchant.ifBlank { null }, Money.parse(amount), date) }, modifier = Modifier.testTag(TestTags.DIALOG_CONFIRM)) {
                Text("Use these")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
    )
}
