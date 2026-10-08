package io.github.chiragbhatn.expensetracker.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.data.RecurringInput
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.DefaultCategories
import io.github.chiragbhatn.expensetracker.domain.Frequency
import io.github.chiragbhatn.expensetracker.domain.IntervalUnit
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.RecurrenceSchedule
import io.github.chiragbhatn.expensetracker.domain.RecurringExpense
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.AmountField
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.DateField
import io.github.chiragbhatn.expensetracker.ui.components.DetailScreen
import io.github.chiragbhatn.expensetracker.ui.components.DropdownField
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.FormScreen
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.NumberField
import io.github.chiragbhatn.expensetracker.ui.components.OptionalDateField
import io.github.chiragbhatn.expensetracker.ui.components.TextInput
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.formatShort
import io.github.chiragbhatn.expensetracker.ui.toast
import io.github.chiragbhatn.expensetracker.ui.today
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun RecurringScreen(onBack: () -> Unit, onOpen: (Long?) -> Unit) {
    val data = appData()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    DetailScreen(
        title = "Recurring expenses",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(null) }, modifier = Modifier.testTag(TestTags.ADD_RECURRING)) {
                Icon(Icons.Filled.Add, contentDescription = "Add recurring expense")
            }
        },
    ) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@DetailScreen
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            item {
                Hint(
                    "Each occurrence is recorded as an expense on its date (cashback rules apply). Past dates are never filled in.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (data.recurring.isEmpty()) {
                item { EmptyState("Nothing recurring yet", "Add rent, subscriptions or bills that repeat.") }
            }
            items(data.recurring, key = { it.id }) { item ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(item.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(item.title) },
                    supportingContent = {
                        Text(
                            when {
                                item.isFinished -> "${item.schedule.describe()} · ended"
                                !item.active -> "${item.schedule.describe()} · paused"
                                else -> "${item.schedule.describe()} · next ${item.nextDate.formatShort()}"
                            },
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.amount.format(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 8.dp))
                            Switch(checked = item.active, onCheckedChange = { active -> scope.launch { container.recurring.setActive(item.id, active) } })
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun RecurringFormScreen(recurringId: Long?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val data = appData()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val today = today()

    var loaded by rememberSaveable { mutableStateOf(recurringId == null) }
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Subscription") }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CARD) }
    var cardId by rememberSaveable { mutableStateOf<Long?>(null) }
    var startDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var frequency by rememberSaveable { mutableStateOf(Frequency.MONTHLY) }
    var intervalCount by rememberSaveable { mutableStateOf("1") }
    var intervalUnit by rememberSaveable { mutableStateOf(IntervalUnit.MONTHS) }
    var endDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var reminderDays by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(data, recurringId) {
        if (!loaded && data != null) {
            data.recurring.firstOrNull { it.id == recurringId }?.let { item: RecurringExpense ->
                title = item.title
                amount = item.amount.toInputString()
                category = item.category
                method = item.paymentMethod
                cardId = item.cardId
                startDay = item.schedule.start.toEpochDay()
                frequency = item.schedule.frequency
                intervalCount = item.schedule.intervalCount.toString()
                intervalUnit = item.schedule.intervalUnit
                endDay = item.endDate?.toEpochDay()
                reminderDays = item.reminderDaysBefore?.toString().orEmpty()
                note = item.note
                active = item.active
            }
            loaded = true
        }
    }

    FormScreen(
        title = if (recurringId == null) "Add recurring expense" else "Edit recurring expense",
        onBack = onDone,
        actions = {
            if (recurringId != null) IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        },
    ) {
        if (data == null || !loaded) {
            Loading()
            return@FormScreen
        }
        val parsed = Money.parse(amount)
        TextInput(
            value = title,
            onValueChange = { title = it },
            label = "Merchant or name (e.g. Netflix, Rent)",
            capitalization = KeyboardCapitalization.Words,
            errorText = if (showErrors && title.isBlank()) "Enter a name" else null,
            tag = TestTags.MERCHANT_INPUT,
        )
        AmountField(
            value = amount,
            onValueChange = { amount = it },
            errorText = if (showErrors && parsed?.isPositive != true) "Enter an amount" else null,
            modifier = Modifier.testTag(TestTags.AMOUNT_INPUT),
        )
        val categories = data.categoryNames(CategoryKind.EXPENSE).ifEmpty { DefaultCategories.expense }
        DropdownField("Category", category, if (category in categories) categories else categories + category, { it }, { category = it })
        DropdownField("Paid with", method, PaymentMethod.entries, { it.label }, { method = it })
        if (method == PaymentMethod.CARD && data.cards.isNotEmpty()) {
            DropdownField(
                "Card",
                data.cardsById[cardId],
                listOf(null) + data.cards.filter { it.active || it.id == cardId },
                { it?.displayName ?: "No card selected" },
                { cardId = it?.id },
            )
        }
        DateField(date = LocalDate.ofEpochDay(startDay), onDateChange = { startDay = it.toEpochDay() }, label = "Start date")
        DropdownField("Repeats", frequency, Frequency.entries, { it.label }, { frequency = it })
        if (frequency == Frequency.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(intervalCount, { intervalCount = it }, "Every", Modifier.weight(1f), maxDigits = 3)
                DropdownField("Unit", intervalUnit, IntervalUnit.entries, { it.label }, { intervalUnit = it }, Modifier.weight(1f))
            }
        }
        OptionalDateField(date = endDay?.let(LocalDate::ofEpochDay), onDateChange = { endDay = it?.toEpochDay() }, label = "End date (optional)")
        NumberField(reminderDays, { reminderDays = it }, "Remind me days before (optional)", maxDigits = 2)
        TextInput(value = note, onValueChange = { note = it }, label = "Note (optional)", singleLine = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Active", Modifier.weight(1f))
            Switch(checked = active, onCheckedChange = { active = it })
        }
        val schedule = RecurrenceSchedule(LocalDate.ofEpochDay(startDay), frequency, intervalCount.toIntOrNull()?.coerceAtLeast(1) ?: 1, intervalUnit)
        val next = RecurringExpense.firstPending(schedule, today).date
        Hint("${schedule.describe()}. Next occurrence: ${next.formatShort()}.")
        Button(
            onClick = {
                val end = endDay?.let(LocalDate::ofEpochDay)
                if (title.isBlank() || parsed == null || !parsed.isPositive || (end != null && end.isBefore(LocalDate.ofEpochDay(startDay)))) {
                    showErrors = true
                    if (end != null && end.isBefore(LocalDate.ofEpochDay(startDay))) toast(context, "The end date is before the start date")
                } else {
                    scope.launch {
                        container.recurring.save(
                            recurringId,
                            RecurringInput(
                                title = title,
                                amount = parsed,
                                category = category,
                                paymentMethod = method,
                                cardId = if (method == PaymentMethod.CARD) cardId else null,
                                start = LocalDate.ofEpochDay(startDay),
                                frequency = frequency,
                                intervalCount = intervalCount.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                intervalUnit = intervalUnit,
                                endDate = end,
                                active = active,
                                reminderDaysBefore = reminderDays.toIntOrNull(),
                                note = note,
                            ),
                        )
                        container.recurring.recordDue()
                        onDone()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SAVE),
        ) { Text("Save") }
    }

    if (deleting && recurringId != null) {
        ConfirmDialog(
            title = "Delete this recurring expense?",
            message = "Expenses it already recorded stay.",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = false
                scope.launch {
                    container.recurring.delete(recurringId)
                    onDone()
                }
            },
            onDismiss = { deleting = false },
        )
    }
}
