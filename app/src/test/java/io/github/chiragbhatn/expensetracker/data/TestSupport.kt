package io.github.chiragbhatn.expensetracker.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.chiragbhatn.expensetracker.backup.AppInfo
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/** A clock the test moves by hand. */
class TestClock(var date: LocalDate = LocalDate.of(2026, 10, 8)) : AppClock {
    private var tick = 0L

    // Strictly increasing, so "newer" comparisons between saves are deterministic.
    override fun now(): Long = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 36_000_000 + (++tick)
    override fun today(): LocalDate = date
}

/** An in-memory app: database, repositories and settings, isolated from other tests. */
class TestApp(name: String = "app", val clock: TestClock = TestClock()) : AutoCloseable {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root = File(context.filesDir, "test-$name-${System.nanoTime()}").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .addCallback(AppDatabase.SeedDefaults)
        .allowMainThreadQueries()
        .build()
    val files = AppFiles(File(root, "files"), File(root, "cache"))
    val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(root, "settings.preferences_pb") })
    val expenses = ExpenseRepository(database, clock, files)
    val people = PeopleRepository(database, clock, files)
    val cards = CardRepository(database, clock)
    val rules = CashbackRuleRepository(database, clock)
    val recurring = RecurringRepository(database, clock, expenses)
    val categories = CategoryRepository(database, clock)
    val backup = BackupRepository(database, settings, clock, files, AppInfo("2.0-test", 2))

    suspend fun data(): AppData = database.observeAppData().first()

    override fun close() {
        database.close()
        scope.cancel()
        root.deleteRecursively()
    }
}
