package io.github.chiragbhatn.expensetracker.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.chiragbhatn.expensetracker.AppContainer
import io.github.chiragbhatn.expensetracker.ExpenseTrackerApp
import io.github.chiragbhatn.expensetracker.data.AppData
import io.github.chiragbhatn.expensetracker.domain.AppSettings
import io.github.chiragbhatn.expensetracker.ui.backup.BackupViewModel
import io.github.chiragbhatn.expensetracker.ui.backup.CsvViewModel
import io.github.chiragbhatn.expensetracker.ui.expenses.ExpenseFormViewModel
import io.github.chiragbhatn.expensetracker.ui.rules.CashbackRulesViewModel
import java.time.LocalDate

/** The app's repositories, available to every screen. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }

/** All app data, or null while it is first being read. */
@Composable
fun appData(): AppData? {
    val data by LocalAppContainer.current.data.collectAsStateWithLifecycle()
    return data
}

@Composable
fun appSettings(): AppSettings {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    return settings
}

@Composable
fun today(): LocalDate {
    val container = LocalAppContainer.current
    return remember { container.clock.today() }
}

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { ExpenseFormViewModel(createSavedStateHandle(), container()) }
        initializer { BackupViewModel(container()) }
        initializer { CsvViewModel(container()) }
        initializer { CashbackRulesViewModel(container()) }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExpenseTrackerApp).container

/** Opens Android's share sheet with [text]; no particular app is required. */
fun shareText(context: Context, text: String, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No app available to share with", Toast.LENGTH_SHORT).show()
    }
}

fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
