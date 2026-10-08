package io.github.chiragbhatn.expensetracker

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.chiragbhatn.expensetracker.backup.AppInfo
import io.github.chiragbhatn.expensetracker.data.AppClock
import io.github.chiragbhatn.expensetracker.data.AppData
import io.github.chiragbhatn.expensetracker.data.AppFiles
import io.github.chiragbhatn.expensetracker.data.BackupRepository
import io.github.chiragbhatn.expensetracker.data.CardRepository
import io.github.chiragbhatn.expensetracker.data.CashbackRuleRepository
import io.github.chiragbhatn.expensetracker.data.CategoryRepository
import io.github.chiragbhatn.expensetracker.data.ExpenseRepository
import io.github.chiragbhatn.expensetracker.data.PeopleRepository
import io.github.chiragbhatn.expensetracker.data.RecurringRepository
import io.github.chiragbhatn.expensetracker.data.SettingsRepository
import io.github.chiragbhatn.expensetracker.data.SystemClock
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.observeAppData
import io.github.chiragbhatn.expensetracker.data.settingsDataStore
import io.github.chiragbhatn.expensetracker.reminders.DailyWorker
import io.github.chiragbhatn.expensetracker.reminders.ReminderNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpenseTrackerApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Recurring expenses that fell due while the app was closed are recorded right away.
        container.scope.launch {
            runCatching { container.recurring.recordDue() }
        }
        runCatching { DailyWorker.schedule(this) }
    }
}

class AppContainer(context: Context, val clock: AppClock = SystemClock, databaseName: String = AppDatabase.NAME) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val files = AppFiles(context.filesDir, context.cacheDir)
    val database = AppDatabase.open(context, databaseName)
    val settings = SettingsRepository(context.settingsDataStore)
    val appInfo = appInfo(context)

    val expenses = ExpenseRepository(database, clock, files)
    val people = PeopleRepository(database, clock, files)
    val cards = CardRepository(database, clock)
    val cashbackRules = CashbackRuleRepository(database, clock)
    val recurring = RecurringRepository(database, clock, expenses)
    val categories = CategoryRepository(database, clock)
    val backup = BackupRepository(database, settings, clock, files, appInfo)
    val notifier = ReminderNotifier(context, settings, clock)

    /** All app data, shared by every screen; null until the database has been read. */
    val data: StateFlow<AppData?> = database.observeAppData().stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun loadData(): AppData = data.filterNotNull().first()

    private fun appInfo(context: Context): AppInfo = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        AppInfo(info.versionName ?: "2.0", code)
    } catch (_: PackageManager.NameNotFoundException) {
        AppInfo("2.0", 0)
    }
}
