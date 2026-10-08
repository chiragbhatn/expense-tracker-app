package io.github.chiragbhatn.expensetracker.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.data.CardInput
import io.github.chiragbhatn.expensetracker.domain.CardMath
import io.github.chiragbhatn.expensetracker.domain.CardSummary
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.AmountField
import io.github.chiragbhatn.expensetracker.ui.components.AmountLine
import io.github.chiragbhatn.expensetracker.ui.components.BarItem
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.DateField
import io.github.chiragbhatn.expensetracker.ui.components.DetailScreen
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.ExpenseListItem
import io.github.chiragbhatn.expensetracker.ui.components.FormScreen
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.HorizontalBars
import io.github.chiragbhatn.expensetracker.ui.components.InfoCard
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.NumberField
import io.github.chiragbhatn.expensetracker.ui.components.SectionTitle
import io.github.chiragbhatn.expensetracker.ui.components.TextInput
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.formatShort
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import io.github.chiragbhatn.expensetracker.ui.toast
import io.github.chiragbhatn.expensetracker.ui.today
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun CardsScreen(onBack: () -> Unit, onOpenCard: (Long) -> Unit, onAddCard: () -> Unit) {
    val data = appData()
    val today = today()
    DetailScreen(
        title = "Credit cards",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCard, modifier = Modifier.testTag(TestTags.ADD_CARD)) { Icon(Icons.Filled.Add, contentDescription = "Add card") }
        },
    ) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@DetailScreen
        }
        val summaries = remember(data) { data.cardSummaries(today) }
        val upcoming = remember(summaries) { CardMath.upcomingPayments(summaries, today).associateBy { it.card.id } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            if (summaries.isEmpty()) {
                item { EmptyState("No cards yet", "Add a credit card to track its spending, outstanding amount, limit and bill dates.") }
            }
            items(summaries, key = { it.card.id }) { summary ->
                InfoCard(title = summary.card.displayName + if (summary.card.active) "" else " (inactive)", onClick = { onOpenCard(summary.card.id) }) {
                    if (summary.card.bank.isNotBlank()) Hint(summary.card.bank)
                    CardFigures(summary)
                    upcoming[summary.card.id]?.let { bill ->
                        Text(
                            (if (bill.overdue) "Overdue: " else "Bill due ") + "${bill.amount.format()} on ${bill.dueDate.formatShort()}",
                            color = if (bill.overdue) LocalAmountColors.current.negative else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFigures(summary: CardSummary) {
    AmountLine("Current outstanding", summary.outstanding.format(), emphasized = true)
    AmountLine("Credit limit", summary.card.creditLimit.format())
    AmountLine("Available limit", summary.availableLimit.format())
    if (summary.card.creditLimit.isPositive) LimitMeter(summary)
}

/** How much of the limit is used: the filled part is the outstanding amount. */
@Composable
private fun LimitMeter(summary: CardSummary) {
    val used = (summary.outstanding.paise.toFloat() / summary.card.creditLimit.paise).coerceIn(0f, 1f)
    val fill = if (summary.isOverLimit || used > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Box(
            Modifier
                .fillMaxWidth(used)
                .fillMaxHeight()
                .background(fill),
        )
    }
    Hint("${(used * 100).toInt()}% of the limit used" + if (summary.isOverLimit) " (over the limit)" else "")
}

@Composable
fun CardDetailScreen(cardId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit, onOpenExpense: (Long) -> Unit) {
    val data = appData()
    val today = today()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var paying by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val card = data?.cardsById?.get(cardId)

    DetailScreen(
        title = card?.displayName ?: "Card",
        onBack = onBack,
        actions = {
            if (card != null) {
                IconButton(onClick = { onEdit(cardId) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit card") }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete card") }
            }
        },
    ) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@DetailScreen
        }
        if (card == null) {
            EmptyState("Card not found", "It may have been deleted.", Modifier.padding(padding))
            return@DetailScreen
        }
        val summary = remember(data, card) { CardMath.summary(card, data.expenses, data.cardPayments, today) }
        val payments = data.cardPayments.filter { it.cardId == cardId }
        val expenses = data.expenses.filter { it.cardId == cardId }
        val colors = LocalAmountColors.current
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding, hasFab = false)) {
            item {
                InfoCard(title = null) {
                    CardFigures(summary)
                    HorizontalDivider()
                    AmountLine("Total spending", summary.totalSpending.format())
                    AmountLine("Cashback earned", summary.cashbackEarned.format(), color = colors.positive)
                    AmountLine("Bill payments made", summary.totalPaid.format())
                    AmountLine("Spending this month", summary.monthSpending.format())
                    AmountLine("Spent on behalf of others", summary.spentOnBehalfOfOthers.format())
                }
            }
            item {
                InfoCard(title = "Statement") {
                    AmountLine("Last statement date", summary.lastStatementDate.formatShort())
                    AmountLine("Next statement date", summary.nextStatementDate.formatShort())
                    AmountLine("Payment due date", summary.dueDate.formatShort())
                    AmountLine("Unpaid from statements", summary.statementBalance.format(), emphasized = true)
                    Button(onClick = { paying = true }, modifier = Modifier.fillMaxWidth()) { Text("Record bill payment") }
                }
            }
            if (summary.byMerchant.isNotEmpty()) {
                item { InfoCard(title = "By merchant") { HorizontalBars(summary.byMerchant.take(8).map { BarItem(it.key, it.amount) }) } }
                item { InfoCard(title = "By category") { HorizontalBars(summary.byCategory.take(8).map { BarItem(it.key, it.amount) }) } }
            }
            if (payments.isNotEmpty()) {
                item { SectionTitle("Bill payments") }
                items(payments, key = { "p${it.id}" }) { payment ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(payment.amount.format(), style = MaterialTheme.typography.titleSmall)
                            Hint(payment.date.formatShort() + if (payment.note.isNotBlank()) " · ${payment.note}" else "")
                        }
                        TextButton(onClick = { scope.launch { container.cards.deletePayment(payment.id) } }) { Text("Delete") }
                    }
                }
            }
            item { SectionTitle("Expenses on this card") }
            if (expenses.isEmpty()) item { Hint("None yet.", Modifier.padding(horizontal = 16.dp)) }
            items(expenses.take(50), key = { "e${it.id}" }) { expense ->
                ExpenseListItem(expense, card, onClick = { onOpenExpense(expense.id) })
            }
        }

        if (paying) {
            PaymentDialog(
                suggested = summary.statementBalance.takeIf { it.isPositive } ?: summary.outstanding.takeIf { it.isPositive },
                today = today,
                onSave = { amount, date, note ->
                    paying = false
                    scope.launch { container.cards.addPayment(cardId, amount, date, note) }
                },
                onDismiss = { paying = false },
            )
        }
        if (deleting) {
            ConfirmDialog(
                title = "Delete ${card.displayName}?",
                message = "Its bill payments are deleted. Expenses paid with it stay, without the card link; cashback rules only for this card are removed.",
                confirmLabel = "Delete",
                onConfirm = {
                    deleting = false
                    scope.launch {
                        container.cards.delete(cardId)
                        onBack()
                    }
                },
                onDismiss = { deleting = false },
            )
        }
    }
}

@Composable
private fun PaymentDialog(suggested: Money?, today: LocalDate, onSave: (Money, LocalDate, String) -> Unit, onDismiss: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf(suggested?.toInputString().orEmpty()) }
    var date by remember { mutableStateOf(today) }
    var note by rememberSaveable { mutableStateOf("") }
    val parsed = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record bill payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(value = amount, onValueChange = { amount = it })
                DateField(date = date, onDateChange = { date = it })
                TextInput(value = note, onValueChange = { note = it }, label = "Note (optional)")
                Hint("A bill payment lowers the card's outstanding amount. It is not an expense.")
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let { onSave(it, date, note) } }, enabled = parsed?.isPositive == true) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CardFormScreen(cardId: Long?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val data = appData()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var loaded by rememberSaveable { mutableStateOf(cardId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var bank by rememberSaveable { mutableStateOf("") }
    var lastFour by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf("") }
    var statementDay by rememberSaveable { mutableStateOf("") }
    var dueDay by rememberSaveable { mutableStateOf("") }
    var reminderDays by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(data, cardId) {
        if (!loaded && data != null) {
            data.cardsById[cardId]?.let { card ->
                name = card.name
                bank = card.bank
                lastFour = card.lastFour
                limit = card.creditLimit.toInputString()
                statementDay = card.statementDay.toString()
                dueDay = card.dueDay.toString()
                reminderDays = card.reminderDaysBefore?.toString().orEmpty()
                notes = card.notes
                active = card.active
            }
            loaded = true
        }
    }

    FormScreen(title = if (cardId == null) "Add card" else "Edit card", onBack = onDone) {
        if (!loaded) {
            Loading()
            return@FormScreen
        }
        val statement = statementDay.toIntOrNull()
        val due = dueDay.toIntOrNull()
        TextInput(
            value = name,
            onValueChange = { name = it },
            label = "Card name",
            capitalization = KeyboardCapitalization.Words,
            errorText = if (showErrors && name.isBlank()) "Enter a name" else null,
            tag = TestTags.NAME_INPUT,
        )
        TextInput(value = bank, onValueChange = { bank = it }, label = "Bank", capitalization = KeyboardCapitalization.Words)
        NumberField(
            value = lastFour,
            onValueChange = { lastFour = it },
            label = "Last four digits",
            maxDigits = 4,
            errorText = if (showErrors && lastFour.isNotEmpty() && lastFour.length != 4) "Enter 4 digits" else null,
        )
        AmountField(value = limit, onValueChange = { limit = it }, label = "Credit limit")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                value = statementDay,
                onValueChange = { statementDay = it },
                label = "Statement day",
                maxDigits = 2,
                modifier = Modifier.weight(1f),
                errorText = if (showErrors && (statement == null || statement !in 1..31)) "1–31" else null,
            )
            NumberField(
                value = dueDay,
                onValueChange = { dueDay = it },
                label = "Due day",
                maxDigits = 2,
                modifier = Modifier.weight(1f),
                errorText = if (showErrors && (due == null || due !in 1..31)) "1–31" else null,
            )
        }
        Hint("The day of the month the statement is generated and the bill is due. Short months use their last day.")
        NumberField(value = reminderDays, onValueChange = { reminderDays = it }, label = "Remind me days before due (optional)", maxDigits = 2)
        TextInput(value = notes, onValueChange = { notes = it }, label = "Notes", singleLine = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Active", Modifier.weight(1f))
            Switch(checked = active, onCheckedChange = { active = it })
        }
        Button(
            onClick = {
                val parsedLimit = if (limit.isBlank()) Money.ZERO else Money.parse(limit)
                if (name.isBlank() || statement == null || statement !in 1..31 || due == null || due !in 1..31 || parsedLimit == null ||
                    (lastFour.isNotEmpty() && lastFour.length != 4)
                ) {
                    showErrors = true
                } else {
                    scope.launch {
                        try {
                            container.cards.save(cardId, CardInput(name, bank, lastFour, parsedLimit, statement, due, notes, active, reminderDays.toIntOrNull()))
                            onDone()
                        } catch (e: IllegalArgumentException) {
                            toast(context, e.message ?: "Check the card details")
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SAVE),
        ) { Text("Save card") }
        if (cardId != null) OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}
