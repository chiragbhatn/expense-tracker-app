@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.chiragbhatn.expensetracker.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.chiragbhatn.expensetracker.backup.BackupWorkbook
import io.github.chiragbhatn.expensetracker.data.CategoryResult
import io.github.chiragbhatn.expensetracker.domain.Category
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.DefaultCategories
import io.github.chiragbhatn.expensetracker.domain.LedgerSummary
import io.github.chiragbhatn.expensetracker.domain.LegacyShares
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.ShareMessages
import io.github.chiragbhatn.expensetracker.domain.ShareTemplates
import io.github.chiragbhatn.expensetracker.domain.sumMoney
import io.github.chiragbhatn.expensetracker.reminders.ReminderNotifier
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.appSettings
import io.github.chiragbhatn.expensetracker.ui.components.ConfirmDialog
import io.github.chiragbhatn.expensetracker.ui.components.DateField
import io.github.chiragbhatn.expensetracker.ui.components.DetailScreen
import io.github.chiragbhatn.expensetracker.ui.components.DropdownField
import io.github.chiragbhatn.expensetracker.ui.components.EmptyState
import io.github.chiragbhatn.expensetracker.ui.components.FormScreen
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.InfoCard
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.NameDialog
import io.github.chiragbhatn.expensetracker.ui.components.NumberField
import io.github.chiragbhatn.expensetracker.ui.components.SectionTitle
import io.github.chiragbhatn.expensetracker.ui.components.TextInput
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.formatShort
import io.github.chiragbhatn.expensetracker.ui.toast
import io.github.chiragbhatn.expensetracker.ui.today
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val data = appData()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(CategoryKind.EXPENSE) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    fun report(result: CategoryResult) {
        when (result) {
            is CategoryResult.Exists -> toast(context, "${result.name} already exists")
            CategoryResult.Protected -> toast(context, "\"Other\" is always kept")
            CategoryResult.Done -> Unit
        }
    }

    DetailScreen(
        title = "Categories",
        onBack = onBack,
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add category") } },
    ) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@DetailScreen
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            item {
                PrimaryTabRow(selectedTabIndex = kind.ordinal) {
                    CategoryKind.entries.forEach { option ->
                        Tab(selected = kind == option, onClick = { kind = option }, text = { Text(if (option == CategoryKind.EXPENSE) "Expense" else "Income") })
                    }
                }
            }
            val categories = data.categories.filter { it.kind == kind }
            items(categories, key = { it.id }) { category ->
                val used = when (kind) {
                    CategoryKind.EXPENSE -> data.expenses.count { it.category.equals(category.name, ignoreCase = true) }
                    CategoryKind.INCOME -> data.incomes.count { it.category.equals(category.name, ignoreCase = true) }
                }
                ListItem(
                    modifier = Modifier.clickable { renaming = category },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(category.name) },
                    supportingContent = { Text((if (category.isDefault) "Default · " else "Custom · ") + "$used use${if (used == 1) "" else "s"}") },
                    trailingContent = {
                        if (category.name != DefaultCategories.OTHER) {
                            IconButton(onClick = { deleting = category }) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${category.name}") }
                        }
                    },
                )
            }
        }
    }

    if (adding) {
        NameDialog(
            title = "New ${if (kind == CategoryKind.EXPENSE) "expense" else "income"} category",
            confirmLabel = "Add",
            onConfirm = { name ->
                adding = false
                scope.launch { report(container.categories.add(name, kind)) }
            },
            onDismiss = { adding = false },
        )
    }
    renaming?.let { category ->
        NameDialog(
            title = "Rename ${category.name}",
            confirmLabel = "Rename",
            initialName = category.name,
            onConfirm = { name ->
                renaming = null
                scope.launch { report(container.categories.rename(category.id, name)) }
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { category ->
        ConfirmDialog(
            title = "Delete ${category.name}?",
            message = "Anything in this category moves to \"Other\".",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = null
                scope.launch { report(container.categories.delete(category.id)) }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val settings = appSettings()
    val data = appData()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val today = today()
    var adding by rememberSaveable { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(ReminderNotifier.canPost(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> canNotify = granted }
    val reminders = settings.reminders

    fun update(transform: (io.github.chiragbhatn.expensetracker.domain.ReminderSettings) -> io.github.chiragbhatn.expensetracker.domain.ReminderSettings) {
        scope.launch { container.settings.update { it.copy(reminders = transform(it.reminders)) } }
    }

    DetailScreen(
        title = "Reminders",
        onBack = onBack,
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add reminder") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            item {
                InfoCard(title = "Notifications") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Remind me", Modifier.weight(1f))
                        Switch(checked = reminders.enabled, onCheckedChange = { on -> update { it.copy(enabled = on) } })
                    }
                    if (reminders.enabled && !canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Hint("Notifications are not allowed yet.")
                        OutlinedButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                    }
                    var cardDays by rememberSaveable(reminders.cardDaysBefore) { mutableStateOf(reminders.cardDaysBefore.toString()) }
                    var recurringDays by rememberSaveable(reminders.recurringDaysBefore) { mutableStateOf(reminders.recurringDaysBefore.toString()) }
                    var udhaarDays by rememberSaveable(reminders.udhaarAfterDays) { mutableStateOf(reminders.udhaarAfterDays.toString()) }
                    NumberField(
                        value = cardDays,
                        onValueChange = { text ->
                            cardDays = text
                            text.toIntOrNull()?.let { days -> update { it.copy(cardDaysBefore = days) } }
                        },
                        label = "Card bills: days before the due date",
                        maxDigits = 2,
                    )
                    NumberField(
                        value = recurringDays,
                        onValueChange = { text ->
                            recurringDays = text
                            text.toIntOrNull()?.let { days -> update { it.copy(recurringDaysBefore = days) } }
                        },
                        label = "Recurring expenses: days before",
                        maxDigits = 2,
                    )
                    NumberField(
                        value = udhaarDays,
                        onValueChange = { text ->
                            udhaarDays = text
                            text.toIntOrNull()?.let { days -> update { it.copy(udhaarAfterDays = days) } }
                        },
                        label = "Money owed to you: remind after days without payment (0 = off)",
                        maxDigits = 3,
                    )
                    Hint("Cards and recurring expenses can override these days in their own settings. Reminders are checked once a day.")
                    TextButton(onClick = {
                        scope.launch {
                            container.notifier.notifyDue(container.loadData())
                            toast(context, "Checked for due reminders")
                        }
                    }) { Text("Check now") }
                }
            }
            item { SectionTitle("Your reminders") }
            val list = data?.reminders.orEmpty()
            if (list.isEmpty()) item { Hint("None yet. Add one here or from a person's page.", Modifier.padding(horizontal = 16.dp)) }
            items(list, key = { it.id }) { reminder ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reminder.done, onCheckedChange = { done -> scope.launch { container.people.setReminderDone(reminder.id, done) } })
                    Column(Modifier.weight(1f)) {
                        Text(reminder.title)
                        val who = reminder.personId?.let { data?.peopleById?.get(it)?.name }
                        Hint(listOfNotNull("Due ${reminder.dueDate.formatShort()}", who, reminder.note.ifBlank { null }).joinToString(" · "))
                    }
                    IconButton(onClick = { scope.launch { container.people.deleteReminder(reminder.id) } }) { Icon(Icons.Filled.Delete, contentDescription = "Delete reminder") }
                }
            }
        }
    }

    if (adding) {
        var title by rememberSaveable { mutableStateOf("") }
        var note by rememberSaveable { mutableStateOf("") }
        var date by remember { mutableStateOf(today.plusDays(1)) }
        var personId by rememberSaveable { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("New reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(value = title, onValueChange = { title = it }, label = "Remind me to")
                    DateField(date = date, onDateChange = { date = it }, label = "On")
                    DropdownField(
                        label = "About (optional)",
                        selected = personId?.let { data?.peopleById?.get(it) },
                        options = listOf(null) + data?.people.orEmpty(),
                        optionLabel = { it?.name ?: "Nobody in particular" },
                        onSelect = { personId = it?.id },
                    )
                    TextInput(value = note, onValueChange = { note = it }, label = "Note (optional)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    adding = false
                    scope.launch { container.people.addReminder(title, note, date, personId) }
                }, enabled = title.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ShareTemplatesScreen(onBack: () -> Unit) {
    val settings = appSettings()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var owes by rememberSaveable { mutableStateOf<String?>(null) }
    var settled by rememberSaveable { mutableStateOf<String?>(null) }
    var credit by rememberSaveable { mutableStateOf<String?>(null) }
    var youOwe by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.shareTemplates) {
        if (owes == null) {
            owes = settings.shareTemplates.owes
            settled = settings.shareTemplates.settled
            credit = settings.shareTemplates.credit
            youOwe = settings.shareTemplates.youOwe
        }
    }

    FormScreen(title = "Share message wording", onBack = onBack) {
        Hint("Write {name} for the person's name and {amount} for the amount. You can still edit each message before sharing it.")
        TextInput(value = owes.orEmpty(), onValueChange = { owes = it }, label = "When they owe you", singleLine = false)
        Hint("Preview: " + ShareMessages.fill(owes.orEmpty(), "Rahul", Money.rupees(1_500).format()))
        TextInput(value = settled.orEmpty(), onValueChange = { settled = it }, label = "When the account is settled", singleLine = false)
        Hint("Preview: " + ShareMessages.fill(settled.orEmpty(), "Rahul", Money.ZERO.format()))
        TextInput(value = credit.orEmpty(), onValueChange = { credit = it }, label = "When they paid extra (credit)", singleLine = false)
        Hint("Preview: " + ShareMessages.fill(credit.orEmpty(), "Rahul", Money.rupees(500).format()))
        TextInput(value = youOwe.orEmpty(), onValueChange = { youOwe = it }, label = "When you owe them", singleLine = false)
        Hint("Preview: " + ShareMessages.fill(youOwe.orEmpty(), "Rahul", Money.rupees(200).format()))
        Button(
            onClick = {
                scope.launch {
                    container.settings.update {
                        it.copy(
                            shareTemplates = ShareTemplates(
                                owes = owes.orEmpty().ifBlank { ShareTemplates.DEFAULT_OWES },
                                settled = settled.orEmpty().ifBlank { ShareTemplates.DEFAULT_SETTLED },
                                credit = credit.orEmpty().ifBlank { ShareTemplates.DEFAULT_CREDIT },
                                youOwe = youOwe.orEmpty().ifBlank { ShareTemplates.DEFAULT_YOU_OWE },
                            ),
                        )
                    }
                    toast(context, "Saved")
                    onBack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        OutlinedButton(
            onClick = {
                val defaults = ShareTemplates()
                owes = defaults.owes
                settled = defaults.settled
                credit = defaults.credit
                youOwe = defaults.youOwe
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restore the default wording") }
    }
}

@Composable
fun DataScreen(onBack: () -> Unit, onOpenExpense: (Long) -> Unit) {
    val data = appData()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    var backups by remember { mutableStateOf(container.backup.safetyBackups()) }

    DetailScreen(title = "Data management", onBack = onBack) { padding ->
        if (data == null) {
            Loading(Modifier.padding(padding))
            return@DetailScreen
        }
        val fixes = remember(data) { LegacyShares.find(data.expenses) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding, hasFab = false)) {
            item {
                InfoCard(title = "Version 1 expenses to review") {
                    if (fixes.isEmpty()) {
                        Hint("Nothing to review: every shared expense charges people the amount after cashback.")
                    } else {
                        Text(
                            "${fixes.size} expense${if (fixes.size == 1) "" else "s"} from version 1 charge people the full amount before cashback. " +
                                "Version 2 charges the effective amount. Correcting them lowers what people owe by ${fixes.sumMoney { it.difference }.format()} in total.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Hint("Nothing changes unless you apply the corrections. You can also fix them one by one by opening each expense.")
                        Button(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth()) { Text("Review and apply corrections") }
                    }
                }
            }
            items(fixes, key = { it.expense.id }) { fix ->
                ListItem(
                    modifier = Modifier.clickable { onOpenExpense(fix.expense.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("${fix.expense.merchant} · ${fix.expense.date.formatShort()}") },
                    supportingContent = { Text("${fix.person.name}: ${fix.oldShare.format()} → ${fix.newShare.format()}") },
                )
            }
            item { HorizontalDivider() }
            item {
                InfoCard(title = "Safety backups") {
                    Hint("Before every restore or CSV import, the app saves a full backup of your data here (the five most recent are kept).")
                    if (backups.isEmpty()) Hint("None yet.")
                    backups.forEach { file ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                                Hint(Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate().formatShort())
                            }
                            TextButton(onClick = {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Save or send backup"))
                            }) { Text("Share") }
                        }
                    }
                }
            }
            item {
                InfoCard(title = "What's stored") {
                    Text(
                        "${data.expenses.size} expenses · ${data.incomes.size} income · ${data.people.size} people · ${data.entries.size} ledger entries · " +
                            "${data.cards.size} cards · ${data.recurring.size} recurring · ${data.rules.size} cashback rules · ${data.categories.size} categories",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val receivable = data.balances.sumMoney { it.summary.receivable }
                    Hint("People owe you ${receivable.format()} in total.")
                }
            }
        }
    }

    if (confirming && data != null) {
        val fixes = LegacyShares.find(data.expenses)
        ConfirmDialog(
            title = "Apply ${fixes.size} correction${if (fixes.size == 1) "" else "s"}?",
            message = "Each person will owe the amount after cashback instead of the full amount. Original amounts, cashback and dates stay as they are.",
            confirmLabel = "Apply",
            onConfirm = {
                confirming = false
                scope.launch {
                    container.backup.saveSafetyBackup("before-v1-corrections")
                    container.expenses.applyShareFixes(fixes)
                    backups = container.backup.safetyBackups()
                    toast(context, "Corrected ${fixes.size} expense${if (fixes.size == 1) "" else "s"}")
                }
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val data = appData()
    FormScreen(title = "About", onBack = onBack) {
        Text("Expense Tracker", style = MaterialTheme.typography.headlineSmall)
        Hint("Version ${container.appInfo.versionName} (${container.appInfo.versionCode})")
        Text(
            "Your data stays on this phone. Nothing is uploaded: there is no account and no server. " +
                "Use Export full Excel backup to keep a copy or move to a new phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SectionTitle("Formats", Modifier.padding(horizontal = 0.dp))
        Text(
            "Excel backup format ${BackupWorkbook.VERSION} · database schema ${BackupWorkbook.SCHEMA_VERSION}. " +
                "Backups list a format version; this app restores ${BackupWorkbook.MAJOR}.x backups and explains when a backup needs a newer app.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SectionTitle("How amounts work", Modifier.padding(horizontal = 0.dp))
        Text(
            "Cashback = original × percentage. Effective = original − cashback. When an expense is for other people, " +
                "their shares add up to the effective amount, so cashback lowers what they owe too. Amounts are kept in whole paise, never as floating point.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (data != null) {
            val people = data.balances.count { it.summary != LedgerSummary.EMPTY }
            Hint("$people people with ledger entries.")
        }
    }
}
