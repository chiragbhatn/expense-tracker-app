package io.github.chiragbhatn.expensetracker.ui.expenses

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.DefaultCategories
import io.github.chiragbhatn.expensetracker.domain.IncomeInput
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.AmountField
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.DateField
import io.github.chiragbhatn.expensetracker.ui.components.DropdownField
import io.github.chiragbhatn.expensetracker.ui.components.FormScreen
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.TextInput
import io.github.chiragbhatn.expensetracker.ui.today
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun IncomeFormScreen(incomeId: Long?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val data = appData()
    val scope = rememberCoroutineScope()
    val today = today()

    var loaded by rememberSaveable { mutableStateOf(incomeId == null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Salary") }
    var dateDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var note by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(data, incomeId) {
        if (!loaded && data != null) {
            data.incomes.firstOrNull { it.id == incomeId }?.let { income ->
                amount = income.amount.toInputString()
                source = income.source
                category = income.category
                dateDay = income.date.toEpochDay()
                note = income.note
            }
            loaded = true
        }
    }

    FormScreen(
        title = if (incomeId == null) "Add income" else "Edit income",
        onBack = onDone,
        actions = {
            if (incomeId != null) {
                IconButton(onClick = { confirmingDelete = true }, modifier = Modifier.testTag(TestTags.DELETE)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete income")
                }
            }
        },
    ) {
        if (data == null || !loaded) {
            Loading()
            return@FormScreen
        }
        val parsed = Money.parse(amount)
        AmountField(
            value = amount,
            onValueChange = { amount = it },
            errorText = if (showErrors && parsed?.isPositive != true) "Enter an amount" else null,
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag(TestTags.AMOUNT_INPUT),
        )
        TextInput(value = source, onValueChange = { source = it }, label = "From (e.g. employer, client)", capitalization = KeyboardCapitalization.Words, tag = TestTags.MERCHANT_INPUT)
        val categories = data.categoryNames(CategoryKind.INCOME).ifEmpty { DefaultCategories.income }
        DropdownField(
            label = "Category",
            selected = category,
            options = if (category in categories) categories else categories + category,
            optionLabel = { it },
            onSelect = { category = it },
            tag = TestTags.CATEGORY_FIELD,
        )
        DateField(date = LocalDate.ofEpochDay(dateDay), onDateChange = { dateDay = it.toEpochDay() })
        TextInput(value = note, onValueChange = { note = it }, label = "Note (optional)", singleLine = false, tag = TestTags.NOTE_INPUT)
        Button(
            onClick = {
                if (parsed == null || !parsed.isPositive) {
                    showErrors = true
                } else {
                    scope.launch {
                        container.expenses.saveIncome(incomeId, IncomeInput(parsed, source, category, LocalDate.ofEpochDay(dateDay), note))
                        onDone()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SAVE),
        ) { Text("Save income") }
    }

    if (confirmingDelete && incomeId != null) {
        ConfirmDialog(
            title = "Delete this income?",
            message = "It will no longer count towards your monthly summary.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmingDelete = false
                scope.launch {
                    container.expenses.deleteIncome(incomeId)
                    onDone()
                }
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}
