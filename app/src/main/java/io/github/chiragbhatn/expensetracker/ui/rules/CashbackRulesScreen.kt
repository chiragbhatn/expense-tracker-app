@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.chiragbhatn.expensetracker.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chiragbhatn.expensetracker.domain.CashbackRule
import io.github.chiragbhatn.expensetracker.domain.CreditCard
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.ui.AppViewModelProvider
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.DropdownField
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CashbackRulesScreen(
    onBack: () -> Unit,
    viewModel: CashbackRulesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val loadedData by viewModel.rules.collectAsStateWithLifecycle()
    var ruleToDelete by remember { mutableStateOf<CashbackRule?>(null) }
    val cards = loadedData?.cards.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cashback rules") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::startAdding,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add merchant") },
                modifier = Modifier.testTag(TestTags.ADD_RULE),
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = listPadding(padding)) {
            item { HowItWorksCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            val loaded = loadedData?.rules ?: return@LazyColumn
            if (loaded.isEmpty()) {
                item { EmptyState("No cashback rules", "Add a merchant where your card gives cashback.") }
            }
            items(loaded, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
                    card = rule.cardId?.let { id -> cards.firstOrNull { it.id == id } },
                    onToggle = { viewModel.setEnabled(rule, it) },
                    onEdit = { viewModel.startEditing(rule) },
                    onDelete = { ruleToDelete = rule },
                )
            }
        }
    }

    viewModel.editor?.let { state ->
        RuleDialog(
            state = state,
            cards = cards,
            onChange = viewModel::onEditorChange,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
        )
    }
    ruleToDelete?.let { rule ->
        ConfirmDialog(
            title = "Delete ${rule.merchant} rule?",
            message = "Expenses already saved keep the cashback they were recorded with.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(rule)
                ruleToDelete = null
            },
            onDismiss = { ruleToDelete = null },
        )
    }
}

@Composable
private fun HowItWorksCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("How cashback works", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Card payments at an enabled merchant get the cashback subtracted: " +
                    "₹200 at 10% is ₹200 − ₹20 = ₹180 effective expense. " +
                    "If the expense was for someone else, they owe you ₹180 too: cashback lowers their share as well. " +
                    "A rule can apply to any card or only to one card.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RuleRow(
    rule: CashbackRule,
    card: CreditCard?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        leadingContent = { PercentageBadge(rule) },
        headlineContent = { Text(rule.merchant) },
        supportingContent = {
            val scope = if (rule.cardId == null) "any card" else card?.displayName ?: "a deleted card"
            val state = if (rule.enabled) "On" else "Off"
            Column {
                Text("${rule.percentage.format()} cashback on $scope · $state")
                if (rule.updatedAtMillis > 0) {
                    Text(
                        "Added ${ruleDate(rule.createdAtMillis)} · updated ${ruleDate(rule.updatedAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag(TestTags.ruleSwitch(rule.merchant)),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${rule.merchant} rule")
                }
            }
        },
    )
}

@Composable
private fun PercentageBadge(rule: CashbackRule) {
    val container = if (rule.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (rule.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rule.percentage.format(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}

private val ruleDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun ruleDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(ruleDateFormat)

@Composable
private fun RuleDialog(
    state: RuleEditorState,
    cards: List<CreditCard>,
    onChange: (RuleEditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.ruleId == null) "Add cashback rule" else "Edit cashback rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.merchant,
                    onValueChange = { onChange(state.copy(merchant = it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.RULE_MERCHANT_INPUT),
                    label = { Text("Merchant") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = state.percentage,
                    onValueChange = { if (Percentage.isPartialInput(it)) onChange(state.copy(percentage = it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.RULE_PERCENTAGE_INPUT),
                    label = { Text("Cashback") },
                    suffix = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
                if (cards.isNotEmpty()) {
                    DropdownField(
                        label = "Applies to",
                        selected = cards.firstOrNull { it.id == state.cardId },
                        options = listOf(null) + cards,
                        optionLabel = { it?.displayName ?: "Any card" },
                        onSelect = { onChange(state.copy(cardId = it?.id)) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", Modifier.weight(1f))
                    Switch(checked = state.enabled, onCheckedChange = { onChange(state.copy(enabled = it)) })
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, modifier = Modifier.testTag(TestTags.DIALOG_CONFIRM)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
