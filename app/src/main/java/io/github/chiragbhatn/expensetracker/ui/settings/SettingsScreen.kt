@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.chiragbhatn.expensetracker.ui.settings

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import io.github.chiragbhatn.expensetracker.domain.Currency
import io.github.chiragbhatn.expensetracker.domain.ThemeMode
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appSettings
import io.github.chiragbhatn.expensetracker.ui.components.ChoiceDialog
import io.github.chiragbhatn.expensetracker.ui.components.NavRow
import io.github.chiragbhatn.expensetracker.ui.components.SectionTitle
import io.github.chiragbhatn.expensetracker.ui.components.listPadding
import io.github.chiragbhatn.expensetracker.ui.toast
import kotlinx.coroutines.launch

data class SettingsActions(
    val cards: () -> Unit,
    val rules: () -> Unit,
    val categories: () -> Unit,
    val recurring: () -> Unit,
    val reminders: () -> Unit,
    val shareWording: () -> Unit,
    val backup: () -> Unit,
    val csv: () -> Unit,
    val data: () -> Unit,
    val about: () -> Unit,
)

@Composable
fun SettingsScreen(bottomBar: @Composable () -> Unit, actions: SettingsActions) {
    val settings = appSettings()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    var choosingCurrency by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, bottomBar = bottomBar) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding, hasFab = false)) {
            item { SectionTitle("Appearance") }
            item {
                NavRow(
                    "Theme",
                    subtitle = settings.themeMode.label,
                    icon = Icons.Filled.DarkMode,
                    modifier = Modifier.testTag(TestTags.THEME_SETTING),
                    onClick = { choosingTheme = true },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    NavRow(
                        "Wallpaper colours",
                        subtitle = "Use colours from your wallpaper instead of the app's",
                        icon = Icons.Filled.Palette,
                        trailing = { Switch(checked = settings.dynamicColor, onCheckedChange = { on -> scope.launch { container.settings.update { it.copy(dynamicColor = on) } } }) },
                        onClick = { scope.launch { container.settings.update { it.copy(dynamicColor = !it.dynamicColor) } } },
                    )
                }
            }
            item {
                NavRow(
                    "Currency",
                    subtitle = "${settings.currency.label} · amounts are shown in this currency, never converted",
                    icon = Icons.Filled.AttachMoney,
                    onClick = { choosingCurrency = true },
                )
            }
            item { HorizontalDivider() }
            item { SectionTitle("Security") }
            item {
                fun toggleLock(on: Boolean) {
                    if (on && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        toast(context, "App lock needs Android 9 or newer.")
                        return
                    }
                    if (on) {
                        val result = BiometricManager.from(context).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                        )
                        if (result != BiometricManager.BIOMETRIC_SUCCESS) {
                            toast(context, "Set a screen lock (PIN, pattern, password or fingerprint) on your phone first.")
                            return
                        }
                    }
                    scope.launch { container.settings.update { it.copy(appLock = on) } }
                }
                NavRow(
                    "App lock",
                    subtitle = "Ask for your fingerprint, face or screen lock when the app opens",
                    icon = Icons.Filled.Lock,
                    trailing = { Switch(checked = settings.appLock, onCheckedChange = ::toggleLock, modifier = Modifier.testTag("app_lock_switch")) },
                    onClick = { toggleLock(!settings.appLock) },
                )
            }
            item { HorizontalDivider() }
            item { SectionTitle("Money") }
            item { NavRow("Credit cards", subtitle = "Limits, statements, bills and card cashback", icon = Icons.Filled.CreditCard, modifier = Modifier.testTag(TestTags.settingsRow("Credit cards")), onClick = actions.cards) }
            item { NavRow("Cashback rules", subtitle = "Merchant cashback percentages", icon = Icons.Filled.Percent, modifier = Modifier.testTag(TestTags.settingsRow("Cashback rules")), onClick = actions.rules) }
            item { NavRow("Categories", subtitle = "Expense and income categories", icon = Icons.Filled.Category, modifier = Modifier.testTag(TestTags.settingsRow("Categories")), onClick = actions.categories) }
            item { NavRow("Recurring expenses", subtitle = "Rent, subscriptions and bills", icon = Icons.Filled.Repeat, modifier = Modifier.testTag(TestTags.settingsRow("Recurring expenses")), onClick = actions.recurring) }
            item { NavRow("Reminder settings", subtitle = "Card bills, recurring expenses, money owed", icon = Icons.Filled.Notifications, modifier = Modifier.testTag(TestTags.settingsRow("Reminder settings")), onClick = actions.reminders) }
            item { NavRow("Share message wording", subtitle = "What Share Balance sends", icon = Icons.Filled.Share, modifier = Modifier.testTag(TestTags.settingsRow("Share message wording")), onClick = actions.shareWording) }
            item { HorizontalDivider() }
            item { SectionTitle("Backup and data") }
            item { NavRow("Export full Excel backup", subtitle = "Backup: everything in one .xlsx file, for safekeeping or a new phone", icon = Icons.Filled.Backup, modifier = Modifier.testTag(TestTags.EXPORT_BACKUP), onClick = actions.backup) }
            item { NavRow("Import full Excel backup", subtitle = "Restore: checks the file, shows a preview, then replaces or merges", icon = Icons.Filled.Restore, modifier = Modifier.testTag(TestTags.IMPORT_BACKUP), onClick = actions.backup) }
            item { NavRow("Export CSV", subtitle = "Expenses, income, people, udhaar or payments", icon = Icons.Filled.Upload, modifier = Modifier.testTag(TestTags.EXPORT_CSV), onClick = actions.csv) }
            item { NavRow("Import CSV", subtitle = "Preview, fix errors and handle duplicates before importing", icon = Icons.Filled.Download, modifier = Modifier.testTag(TestTags.IMPORT_CSV), onClick = actions.csv) }
            item { NavRow("Data management", subtitle = "Version 1 expenses to review, safety backups", icon = Icons.Filled.Storage, modifier = Modifier.testTag(TestTags.settingsRow("Data management")), onClick = actions.data) }
            item { HorizontalDivider() }
            item { NavRow("About", subtitle = "Version and how your data is stored", icon = Icons.Filled.Info, onClick = actions.about) }
            item { NavRow("Spreadsheet formats", subtitle = "Backup format ${io.github.chiragbhatn.expensetracker.backup.BackupWorkbook.VERSION}", icon = Icons.Filled.TableChart, onClick = actions.about) }
        }
    }

    if (choosingTheme) {
        ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { it.label },
            onSelect = { mode ->
                choosingTheme = false
                scope.launch { container.settings.update { it.copy(themeMode = mode) } }
            },
            onDismiss = { choosingTheme = false },
        )
    }
    if (choosingCurrency) {
        ChoiceDialog(
            title = "Currency",
            options = Currency.entries,
            selected = settings.currency,
            label = { it.label },
            onSelect = { currency ->
                choosingCurrency = false
                scope.launch { container.settings.update { it.copy(currency = currency) } }
            },
            onDismiss = { choosingCurrency = false },
        )
    }
}
