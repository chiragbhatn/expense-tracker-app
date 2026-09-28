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
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.ui.AppViewModelProvider
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.listPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashbackRulesScreen(
    bottomBar: @Composable () -> Unit,
    viewModel: CashbackRulesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var ruleToDelete by remember { mutableStateOf<CashbackRule?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cashback rules") }) },
        bottomBar = bottomBar,
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
            val loaded = rules ?: return@LazyColumn
            if (loaded.isEmpty()) {
                item { EmptyState("No cashback rules", "Add a merchant where your card gives cashback.") }
            }
            items(loaded, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
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
                    "₹500 at 10% is ₹500 − ₹50 = ₹450 effective expense. " +
                    "If you paid for someone, they still owe you the full ₹500.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RuleRow(
    rule: CashbackRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        leadingContent = { PercentageBadge(rule) },
        headlineContent = { Text(rule.merchant) },
        supportingContent = {
            Text(if (rule.enabled) "${rule.percentage.format()} cashback · On" else "${rule.percentage.format()} cashback · Off")
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

@Composable
private fun RuleDialog(
    state: RuleEditorState,
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
