package io.github.chiragbhatn.expensetracker.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chiragbhatn.expensetracker.backup.BackupData
import io.github.chiragbhatn.expensetracker.backup.BackupReadResult
import io.github.chiragbhatn.expensetracker.backup.BackupWorkbook
import io.github.chiragbhatn.expensetracker.backup.CheckStep
import io.github.chiragbhatn.expensetracker.backup.CsvKind
import io.github.chiragbhatn.expensetracker.backup.DuplicateChoice
import io.github.chiragbhatn.expensetracker.data.RestoreMode
import io.github.chiragbhatn.expensetracker.ui.AppViewModelProvider
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.components.Banner
import io.github.chiragbhatn.expensetracker.ui.components.ChoiceChips
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.FormScreen
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.SectionTitle
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import java.io.File
import java.text.NumberFormat
import java.util.Locale

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** The name of a picked file, when the provider tells it. */
private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

/** Shares a file from the app's cache through the share sheet. */
private fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Save or send").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun count(n: Int) = NumberFormat.getIntegerInstance(Locale("en", "IN")).format(n)

@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val export by viewModel.export.collectAsStateWithLifecycle()
    val restore by viewModel.restore.collectAsStateWithLifecycle()

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri != null) viewModel.exportTo({ context.contentResolver.openOutputStream(uri) }, "Backup saved.")
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.check(displayName(context, uri)) { context.contentResolver.openInputStream(uri) }
    }

    FormScreen(title = "Excel backup", onBack = onBack) {
        SectionTitle("Export full backup", Modifier.padding(horizontal = 0.dp))
        Text(
            "Creates one Excel (.xlsx) file with everything: people, expenses, income, udhaar, payments, settlements, cards, " +
                "cashback rules, recurring expenses, categories, reminders and settings. Use it to keep a copy or to move to a new phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { createFile.launch(viewModel.suggestedFileName) }, modifier = Modifier.weight(1f).testTag(TestTags.EXPORT_BACKUP)) { Text("Save file") }
            OutlinedButton(
                onClick = {
                    val file = File(container.files.shared, viewModel.suggestedFileName)
                    viewModel.exportTo({ file.outputStream() }, "Backup ready to share.") { shareFile(context, file, XLSX_MIME) }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Share") }
        }
        when (val state = export) {
            ExportState.Idle -> Unit
            ExportState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text("Writing backup…")
            }
            is ExportState.Done -> Banner(state.message)
            is ExportState.Failed -> Banner(state.message, isError = true)
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("Import full backup", Modifier.padding(horizontal = 0.dp))
        Text(
            "Choose a backup file. It is checked first and you see what it contains; nothing changes until you confirm.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = { openFile.launch(arrayOf(XLSX_MIME, "application/octet-stream", "application/zip", "*/*")) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.IMPORT_BACKUP),
        ) { Text("Choose backup file") }

        when (val state = restore) {
            RestoreState.Idle -> Unit
            RestoreState.Reading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text("Checking backup…")
            }
            is RestoreState.Checked -> CheckedBackup(state, onRestore = viewModel::restore, onCancel = viewModel::resetRestore)
            RestoreState.Restoring -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text("Restoring…")
            }
            is RestoreState.Done -> {
                val summary = state.summary
                Banner(
                    when (summary.mode) {
                        RestoreMode.REPLACE -> "Backup restored. The app now holds exactly what was in the backup."
                        RestoreMode.MERGE -> "Backup merged: ${summary.merge?.added ?: 0} records added, ${summary.merge?.updated ?: 0} updated. Nothing was deleted."
                    },
                )
                summary.safetyBackup?.let { Hint("Your previous data was saved first as ${it.name} (Settings → Data management).") }
                OutlinedButton(onClick = viewModel::resetRestore) { Text("Done") }
            }
            is RestoreState.Failed -> Banner(state.message, isError = true)
        }
    }
}

@Composable
private fun CheckedBackup(state: RestoreState.Checked, onRestore: (RestoreMode) -> Unit, onCancel: () -> Unit) {
    val result = state.result
    val colors = LocalAmountColors.current
    var mode by rememberSaveable { mutableStateOf(RestoreMode.MERGE) }
    var confirmingReplace by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Backup validation", style = MaterialTheme.typography.titleMedium)
        state.fileName?.let { Hint(it) }
        result.metadata?.let { meta ->
            Hint("Format ${meta.formatVersion} · app ${meta.appVersion.ifBlank { "?" }} · exported ${meta.exportDate ?: "?"} · ${meta.currency}")
        }
        CheckStep.entries.forEach { step ->
            val failed = result.errors.filter { it.step == step }
            val ran = failed.isNotEmpty() || result.isValid || result.errors.none { it.step.ordinal < step.ordinal }
            if (ran) {
                Text(
                    (if (failed.isEmpty()) "✓ " else "✗ ") + step.label,
                    color = if (failed.isEmpty()) colors.positive else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        result.data?.let { data -> Counts(data) }
        if (result.errors.isNotEmpty()) {
            Banner("This backup can't be restored. Nothing has been changed.", isError = true)
            result.errors.take(30).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (result.errors.size > 30) Hint("…and ${result.errors.size - 30} more.")
        }
        if (result.warnings.isNotEmpty()) {
            Text("Warnings", style = MaterialTheme.typography.titleSmall)
            result.warnings.take(20).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            if (result.warnings.size > 20) Hint("…and ${result.warnings.size - 20} more.")
        }

        if (result.isValid) {
            if (state.hasExistingData) {
                Banner("Existing data detected.")
                ModeOption("Merge backup with existing data", "Adds what's new and updates records that changed. Nothing is deleted.", mode == RestoreMode.MERGE) { mode = RestoreMode.MERGE }
                state.merge?.let { merge ->
                    val changes = merge.stats.filterValues { it.added + it.updated > 0 }
                    if (changes.isEmpty()) {
                        Hint("Everything in this backup is already on this phone.")
                    } else {
                        changes.forEach { (sheet, stats) -> Hint("$sheet: ${stats.added} new, ${stats.updated} updated") }
                    }
                }
                ModeOption("Replace existing data", "Deletes what's on this phone and restores the backup exactly. A safety backup is saved first.", mode == RestoreMode.REPLACE) { mode = RestoreMode.REPLACE }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        val chosen = if (state.hasExistingData) mode else RestoreMode.REPLACE
                        if (chosen == RestoreMode.REPLACE && state.hasExistingData) confirmingReplace = true else onRestore(chosen)
                    },
                    modifier = Modifier.weight(1f).testTag(TestTags.RESTORE_CONFIRM),
                ) { Text("Restore backup") }
            }
        } else {
            OutlinedButton(onClick = onCancel) { Text("Close") }
        }
    }

    if (confirmingReplace) {
        ConfirmDialog(
            title = "Replace everything?",
            message = "All current data on this phone will be replaced by the backup. A safety backup of your current data is saved first.",
            confirmLabel = "Replace",
            onConfirm = {
                confirmingReplace = false
                onRestore(RestoreMode.REPLACE)
            },
            onDismiss = { confirmingReplace = false },
        )
    }
}

@Composable
private fun Counts(data: BackupData) {
    val lines = listOf(
        "expenses" to data.expenses.size,
        "people" to data.people.size,
        "income records" to data.incomes.size,
        "cards" to data.cards.size,
        "ledger transactions" to data.otherLedger.size,
        "payments" to data.payments.size,
        "settlements" to data.settlements.size,
        "card bill payments" to data.cardPayments.size,
        "recurring expenses" to data.recurring.size,
        "cashback rules" to data.cashbackRules.size,
        "categories" to data.categories.size,
        "reminders" to data.reminders.size,
    )
    Column {
        lines.forEach { (label, n) -> Text("✓ ${count(n)} $label", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
        if (data.attachments.isNotEmpty()) Hint("${data.attachments.size} attachment records (the files themselves are not in the backup).")
    }
}

@Composable
private fun ModeOption(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Hint(subtitle)
        }
    }
}

@Composable
fun CsvScreen(onBack: () -> Unit, viewModel: CsvViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(CsvKind.EXPENSES) }
    var choice by rememberSaveable { mutableStateOf(DuplicateChoice.SKIP) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportTo(kind, { context.contentResolver.openOutputStream(uri) }, "${kind.label} exported.")
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.check(kind, displayName(context, uri)) { context.contentResolver.openInputStream(uri) }
    }

    FormScreen(title = "CSV import and export", onBack = onBack) {
        Text("What", style = MaterialTheme.typography.labelLarge)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CsvKind.entries.chunked(3).forEach { row ->
                ChoiceChips(options = row, selected = kind, label = { it.label }, onSelect = {
                    kind = it
                    viewModel.reset()
                })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { createFile.launch(viewModel.fileName(kind)) }, modifier = Modifier.weight(1f).testTag(TestTags.EXPORT_CSV)) { Text("Export") }
            OutlinedButton(
                onClick = {
                    val file = File(container.files.shared, viewModel.fileName(kind))
                    viewModel.exportTo(kind, { file.outputStream() }, "${kind.label} ready to share.") { shareFile(context, file, "text/csv") }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Share") }
        }
        OutlinedButton(
            onClick = { openFile.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.IMPORT_CSV),
        ) { Text("Import ${kind.label.lowercase()} from CSV") }
        Hint(csvHelp(kind))

        when (val current = state) {
            CsvState.Idle -> Unit
            CsvState.Working -> CircularProgressIndicator()
            is CsvState.Message -> Banner(current.text, isError = current.isError)
            is CsvState.Imported -> {
                val result = current.result
                Banner(
                    "Imported: ${result.added} added, ${result.updated} updated, ${result.skipped} skipped" +
                        (if (result.peopleAdded > 0) ", ${result.peopleAdded} new people" else "") +
                        (if (result.categoriesAdded > 0) ", ${result.categoriesAdded} new categories" else "") + ".",
                )
                OutlinedButton(onClick = viewModel::reset) { Text("Done") }
            }
            is CsvState.Preview -> {
                val preview = current.preview
                Text("Preview" + (current.fileName?.let { " of $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                if (preview.fileErrors.isNotEmpty()) {
                    preview.fileErrors.forEach { Banner(it, isError = true) }
                } else {
                    Text("✓ ${preview.valid.size} valid rows", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "✗ ${preview.invalid.size} rows with errors (they will not be imported)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (preview.invalid.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                    Text("${preview.duplicates.size} possible duplicates", style = MaterialTheme.typography.bodyMedium)
                    preview.invalid.take(30).forEach { row ->
                        Text("Line ${row.line} (${row.summary}): ${row.errors.joinToString(" ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (preview.invalid.size > 30) Hint("…and ${preview.invalid.size - 30} more rows with errors.")
                    val warnings = preview.valid.flatMap { row -> row.warnings.map { "Line ${row.line}: $it" } }
                    warnings.take(15).forEach { Hint(it) }
                    if (preview.duplicates.isNotEmpty()) {
                        Text("Duplicates", style = MaterialTheme.typography.titleSmall)
                        preview.duplicates.take(15).forEach { row -> Hint("Line ${row.line} (${row.summary}): ${row.duplicate?.reason}") }
                        Text("What should happen to duplicates?", style = MaterialTheme.typography.bodyMedium)
                        ChoiceChips(options = DuplicateChoice.entries, selected = choice, label = { it.label }, onSelect = { choice = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::reset, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(onClick = { viewModel.import(choice) }, enabled = preview.canImport, modifier = Modifier.weight(1f).testTag("csv_import_confirm")) {
                            Text("Import ${preview.valid.size} rows")
                        }
                    }
                    Hint("A safety backup of your current data is saved before importing.")
                }
            }
        }
    }
}

private fun csvHelp(kind: CsvKind): String = when (kind) {
    CsvKind.EXPENSES -> "Columns: date, merchant, original_amount (required); category, payment_method, card_name, card_last_four, cashback_percentage, " +
        "cashback_amount, effective_amount, shared_with (e.g. Rahul=180; Amit=200), note, id. Dates as YYYY-MM-DD or DD/MM/YYYY."
    CsvKind.INCOME -> "Columns: date, amount (required); source, category, note, id."
    CsvKind.PEOPLE -> "Columns: name (required); phone, email, address, notes, tags, id. Balances are export-only."
    CsvKind.UDHAAR -> "Columns: date, person, type, amount (required); direction, note, id. Types: ${BackupWorkbook.LEDGER} types such as UDHAAR_GIVEN, UDHAAR_TAKEN, ADJUSTMENT. Expense shares come from the expenses file."
    CsvKind.PAYMENTS -> "Columns: date, person, type, amount (required); direction, note, id. Types: PAYMENT_RECEIVED, PAYMENT_MADE, SETTLEMENT."
}
