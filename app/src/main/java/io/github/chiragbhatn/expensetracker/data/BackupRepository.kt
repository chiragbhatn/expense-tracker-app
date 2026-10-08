package io.github.chiragbhatn.expensetracker.data

import androidx.room.withTransaction
import io.github.chiragbhatn.expensetracker.backup.AppInfo
import io.github.chiragbhatn.expensetracker.backup.AttachmentRecord
import io.github.chiragbhatn.expensetracker.backup.BackupData
import io.github.chiragbhatn.expensetracker.backup.BackupMerger
import io.github.chiragbhatn.expensetracker.backup.BackupReadResult
import io.github.chiragbhatn.expensetracker.backup.BackupWorkbook
import io.github.chiragbhatn.expensetracker.backup.CardPaymentRecord
import io.github.chiragbhatn.expensetracker.backup.CardRecord
import io.github.chiragbhatn.expensetracker.backup.CashbackRuleRecord
import io.github.chiragbhatn.expensetracker.backup.CategoryRecord
import io.github.chiragbhatn.expensetracker.backup.Csv
import io.github.chiragbhatn.expensetracker.backup.CsvImportResult
import io.github.chiragbhatn.expensetracker.backup.CsvKind
import io.github.chiragbhatn.expensetracker.backup.CsvPreview
import io.github.chiragbhatn.expensetracker.backup.CsvTransfer
import io.github.chiragbhatn.expensetracker.backup.DuplicateChoice
import io.github.chiragbhatn.expensetracker.backup.ExpenseRecord
import io.github.chiragbhatn.expensetracker.backup.IncomeRecord
import io.github.chiragbhatn.expensetracker.backup.LedgerRecord
import io.github.chiragbhatn.expensetracker.backup.MergeResult
import io.github.chiragbhatn.expensetracker.backup.PersonRecord
import io.github.chiragbhatn.expensetracker.backup.RecurringRecord
import io.github.chiragbhatn.expensetracker.backup.ReminderRecord
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.AttachmentEntity
import io.github.chiragbhatn.expensetracker.data.db.CardPaymentEntity
import io.github.chiragbhatn.expensetracker.data.db.CashbackRuleEntity
import io.github.chiragbhatn.expensetracker.data.db.CategoryEntity
import io.github.chiragbhatn.expensetracker.data.db.CreditCardEntity
import io.github.chiragbhatn.expensetracker.data.db.ExpenseEntity
import io.github.chiragbhatn.expensetracker.data.db.IncomeEntity
import io.github.chiragbhatn.expensetracker.data.db.PersonEntity
import io.github.chiragbhatn.expensetracker.data.db.RecurringExpenseEntity
import io.github.chiragbhatn.expensetracker.data.db.ReminderEntity
import io.github.chiragbhatn.expensetracker.data.db.UdhaarEntryEntity
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.nameKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

enum class RestoreMode { REPLACE, MERGE }

data class RestoreSummary(val mode: RestoreMode, val safetyBackup: File?, val merge: MergeResult?)

/**
 * Moves the app's data in and out: the Excel full backup, restores (replace
 * or merge) and CSV files. Every restore and import first saves a safety
 * backup of the current data, then rewrites the database in one transaction,
 * so it either completes or changes nothing.
 */
class BackupRepository(
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val files: AppFiles,
    private val app: AppInfo,
) {
    /** Everything in the app as backup records, with this device's row ids. */
    suspend fun snapshot(): BackupData = withContext(Dispatchers.IO) {
        database.withTransaction {
            val people = database.personDao().getAll()
            val cards = database.cardDao().getAll()
            val expenses = database.expenseDao().getAll()
            val recurring = database.recurringDao().getAll()
            val personUuid = people.associate { it.id to it.uuid }
            val cardUuid = cards.associate { it.id to it.uuid }
            val expenseUuid = expenses.associate { it.id to it.uuid }
            val recurringUuid = recurring.associate { it.id to it.uuid }
            BackupData(
                settings = settings.current().toMap(),
                people = people.map { p ->
                    PersonRecord(p.uuid, p.name, p.phone, p.email, p.address, p.notes, splitTags(p.tags), p.photoPath, p.createdAt, p.updatedAt, p.id)
                },
                categories = database.categoryDao().getAll().map { c -> CategoryRecord(c.uuid, c.name, c.kind, c.isDefault, c.createdAt, c.updatedAt, c.id) },
                cards = cards.map { c ->
                    CardRecord(
                        c.uuid, c.name, c.bank, c.lastFour, Money(c.creditLimitPaise), c.statementDay, c.dueDay, c.notes, c.active,
                        c.reminderDaysBefore, c.createdAt, c.updatedAt, c.id,
                    )
                },
                cashbackRules = database.cashbackRuleDao().getAll().map { r ->
                    CashbackRuleRecord(r.uuid, r.merchant, Percentage(r.cashbackPercentageBps), r.enabled, r.cardId?.let(cardUuid::get), r.createdAt, r.updatedAt, r.id)
                },
                expenses = expenses.map { e ->
                    ExpenseRecord(
                        e.uuid, LocalDate.ofEpochDay(e.dateEpochDay), e.merchant, e.category, e.paymentMethod, e.cardId?.let(cardUuid::get),
                        Money(e.originalAmountPaise), Percentage(e.cashbackPercentageBps), Money(e.cashbackAmountPaise), Money(e.effectiveAmountPaise),
                        e.note, e.recurringId?.let(recurringUuid::get), e.createdAt, e.updatedAt, e.id,
                    )
                },
                incomes = database.incomeDao().getAll().map { i ->
                    IncomeRecord(i.uuid, LocalDate.ofEpochDay(i.dateEpochDay), i.source, i.category, Money(i.amountPaise), i.note, i.createdAt, i.updatedAt, i.id)
                },
                ledger = database.udhaarDao().getAll().map { l ->
                    LedgerRecord(
                        l.uuid, personUuid.getValue(l.personId), LocalDate.ofEpochDay(l.dateEpochDay), l.type, l.direction, Money(l.amountPaise),
                        l.expenseId?.let(expenseUuid::get), l.note, l.createdAt, l.updatedAt, l.id,
                    )
                },
                cardPayments = database.cardDao().getAllPayments().map { p ->
                    CardPaymentRecord(p.uuid, cardUuid.getValue(p.cardId), LocalDate.ofEpochDay(p.dateEpochDay), Money(p.amountPaise), p.note, p.createdAt, p.updatedAt, p.id)
                },
                recurring = recurring.map { r ->
                    RecurringRecord(
                        r.uuid, r.title, Money(r.amountPaise), r.category, r.paymentMethod, r.cardId?.let(cardUuid::get), LocalDate.ofEpochDay(r.startEpochDay),
                        r.frequency, r.intervalCount, r.intervalUnit, LocalDate.ofEpochDay(r.nextEpochDay), r.occurrenceIndex,
                        r.endEpochDay?.let(LocalDate::ofEpochDay), r.active, r.reminderDaysBefore, r.note, r.createdAt, r.updatedAt, r.id,
                    )
                },
                reminders = database.reminderDao().getAll().map { r ->
                    ReminderRecord(r.uuid, r.title, r.note, LocalDate.ofEpochDay(r.dueEpochDay), r.personId?.let(personUuid::get), r.done, r.createdAt, r.updatedAt, r.id)
                },
                attachments = database.attachmentDao().getAll().map { a ->
                    AttachmentRecord(
                        a.uuid, a.expenseId?.let(expenseUuid::get), a.personId?.let(personUuid::get), a.fileName, a.mimeType, a.sizeBytes,
                        a.createdAt, a.updatedAt, a.id,
                    )
                },
            )
        }
    }

    /**
     * Replaces the whole database with [data] in one transaction. Records keep
     * their row id when they have one; references are resolved by uuid.
     */
    suspend fun replaceAll(data: BackupData) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.attachmentDao().deleteAll()
            database.reminderDao().deleteAll()
            database.udhaarDao().deleteAll()
            database.cardDao().deleteAllPayments()
            database.expenseDao().deleteAll()
            database.recurringDao().deleteAll()
            database.incomeDao().deleteAll()
            database.cashbackRuleDao().deleteAll()
            database.cardDao().deleteAll()
            database.categoryDao().deleteAll()
            database.personDao().deleteAll()

            val now = clock.now()
            fun created(at: Long) = if (at > 0) at else now

            database.categoryDao().insertAll(
                data.categories.withIdsFirst { it.id }.map { c ->
                    CategoryEntity(c.id, c.name, nameKey(c.name), c.kind, c.isDefault, c.uuid, created(c.createdAt), created(c.updatedAt))
                },
            )
            val people = data.people.withIdsFirst { it.id }
            val personIds = people.map { it.uuid }.zip(
                database.personDao().insertAll(
                    people.map { p ->
                        PersonEntity(
                            p.id, p.name, nameKey(p.name), p.phone, p.email, p.address, p.notes, joinTags(p.tags), p.photoFile, p.uuid,
                            created(p.createdAt), created(p.updatedAt),
                        )
                    },
                ),
            ).toMap()
            val cards = data.cards.withIdsFirst { it.id }
            val cardIds = cards.map { it.uuid }.zip(
                database.cardDao().insertAll(
                    cards.map { c ->
                        CreditCardEntity(
                            c.id, c.name, c.bank, c.lastFour, c.creditLimit.paise, c.statementDay, c.dueDay, c.notes, c.active, c.reminderDaysBefore,
                            c.uuid, created(c.createdAt), created(c.updatedAt),
                        )
                    },
                ),
            ).toMap()
            database.cashbackRuleDao().insertAll(
                data.cashbackRules.withIdsFirst { it.id }.map { r ->
                    CashbackRuleEntity(
                        r.id, r.merchant, nameKey(r.merchant), r.percentage.basisPoints, r.enabled, r.cardUuid?.let(cardIds::get), r.uuid,
                        created(r.createdAt), created(r.updatedAt),
                    )
                },
            )
            val recurring = data.recurring.withIdsFirst { it.id }
            val recurringIds = recurring.map { it.uuid }.zip(
                database.recurringDao().insertAll(
                    recurring.map { r ->
                        RecurringExpenseEntity(
                            r.id, r.title, r.amount.paise, r.category, r.paymentMethod, r.cardUuid?.let(cardIds::get), r.startDate.toEpochDay(),
                            r.frequency, r.intervalCount, r.intervalUnit, r.nextDate.toEpochDay(), r.occurrenceIndex, r.endDate?.toEpochDay(),
                            r.active, r.reminderDaysBefore, r.note, r.uuid, created(r.createdAt), created(r.updatedAt),
                        )
                    },
                ),
            ).toMap()
            val expenses = data.expenses.withIdsFirst { it.id }
            val expenseIds = expenses.map { it.uuid }.zip(
                database.expenseDao().insertAll(
                    expenses.map { e ->
                        ExpenseEntity(
                            e.id, e.originalAmount.paise, e.cashbackPercentage.basisPoints, e.cashbackAmount.paise, e.effectiveAmount.paise,
                            e.merchant, e.paymentMethod, e.date.toEpochDay(), e.note, e.category, e.cardUuid?.let(cardIds::get),
                            e.recurringUuid?.let(recurringIds::get), e.uuid, created(e.createdAt), created(e.updatedAt),
                        )
                    },
                ),
            ).toMap()
            database.incomeDao().insertAll(
                data.incomes.withIdsFirst { it.id }.map { i ->
                    IncomeEntity(i.id, i.amount.paise, i.source, i.category, i.date.toEpochDay(), i.note, i.uuid, created(i.createdAt), created(i.updatedAt))
                },
            )
            database.udhaarDao().insertAll(
                data.ledger.withIdsFirst { it.id }.map { l ->
                    UdhaarEntryEntity(
                        l.id, personIds.getValue(l.personUuid), l.direction, l.amount.paise, l.date.toEpochDay(), l.note,
                        l.expenseUuid?.let(expenseIds::getValue), l.type, l.uuid, created(l.createdAt), created(l.updatedAt),
                    )
                },
            )
            database.cardDao().insertPayments(
                data.cardPayments.withIdsFirst { it.id }.map { p ->
                    CardPaymentEntity(p.id, cardIds.getValue(p.cardUuid), p.amount.paise, p.date.toEpochDay(), p.note, p.uuid, created(p.createdAt), created(p.updatedAt))
                },
            )
            database.reminderDao().insertAll(
                data.reminders.withIdsFirst { it.id }.map { r ->
                    ReminderEntity(r.id, r.title, r.note, r.dueDate.toEpochDay(), r.personUuid?.let(personIds::get), r.done, r.uuid, created(r.createdAt), created(r.updatedAt))
                },
            )
            database.attachmentDao().insertAll(
                data.attachments.withIdsFirst { it.id }.map { a ->
                    AttachmentEntity(
                        a.id, a.expenseUuid?.let(expenseIds::get), a.personUuid?.let(personIds::get), a.fileName, a.mimeType, a.sizeBytes, a.uuid,
                        created(a.createdAt), created(a.updatedAt),
                    )
                },
            )
        }
    }

    /** Records that already have a row id are inserted first, so new ones never take their ids. */
    private fun <T> List<T>.withIdsFirst(id: (T) -> Long): List<T> = sortedBy { if (id(it) > 0) 0 else 1 }

    // ------------------------------------------------------------ Excel backup

    fun backupFileName(date: LocalDate = clock.today()) = "expense-tracker-backup-$date.xlsx"

    suspend fun exportWorkbook(out: OutputStream) = withContext(Dispatchers.IO) {
        val data = snapshot()
        val current = settings.current()
        BackupWorkbook.write(data, app, current.currency.code, Instant.ofEpochMilli(clock.now()), clock.today(), out)
        settings.setLastBackupAt(clock.now())
    }

    suspend fun readWorkbook(input: InputStream, fileName: String?): BackupReadResult = withContext(Dispatchers.IO) {
        BackupWorkbook.read(input, fileName)
    }

    /** What merging [incoming] would do, without changing anything. */
    suspend fun previewMerge(incoming: BackupData): MergeResult = BackupMerger.merge(snapshot(), incoming)

    /**
     * Restores a checked backup. [RestoreMode.REPLACE] makes the app hold
     * exactly the backup (and its settings); [RestoreMode.MERGE] adds it to
     * what is already here. A safety backup of the current data is saved first.
     */
    suspend fun restore(incoming: BackupData, mode: RestoreMode): RestoreSummary = withContext(Dispatchers.IO) {
        val current = snapshot()
        val safety = if (current.hasUserData) saveSafetyBackup("before-restore") else null
        when (mode) {
            RestoreMode.REPLACE -> {
                replaceAll(incoming.withoutLocalIds())
                settings.restore(incoming.settings)
                RestoreSummary(mode, safety, null)
            }
            RestoreMode.MERGE -> {
                val merged = BackupMerger.merge(current, incoming.withoutLocalIds())
                replaceAll(merged.data)
                RestoreSummary(mode, safety, merged)
            }
        }
    }

    private fun BackupData.withoutLocalIds() = copy(
        people = people.map { it.copy(id = 0) },
        categories = categories.map { it.copy(id = 0) },
        cards = cards.map { it.copy(id = 0) },
        cashbackRules = cashbackRules.map { it.copy(id = 0) },
        expenses = expenses.map { it.copy(id = 0) },
        incomes = incomes.map { it.copy(id = 0) },
        ledger = ledger.map { it.copy(id = 0) },
        cardPayments = cardPayments.map { it.copy(id = 0) },
        recurring = recurring.map { it.copy(id = 0) },
        reminders = reminders.map { it.copy(id = 0) },
        attachments = attachments.map { it.copy(id = 0) },
    )

    /** Writes a full backup into app storage, keeping the five most recent. */
    suspend fun saveSafetyBackup(reason: String): File = withContext(Dispatchers.IO) {
        val file = File(files.backups, "auto-$reason-${clock.now()}.xlsx")
        file.outputStream().use { out ->
            BackupWorkbook.write(snapshot(), app, settings.current().currency.code, Instant.ofEpochMilli(clock.now()), clock.today(), out)
        }
        files.backups.listFiles { f -> f.name.startsWith("auto-") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(5)
            .forEach { it.delete() }
        file
    }

    fun safetyBackups(): List<File> = files.backups.listFiles { f -> f.name.endsWith(".xlsx") }.orEmpty().sortedByDescending { it.lastModified() }

    // ------------------------------------------------------------ CSV

    fun csvFileName(kind: CsvKind, date: LocalDate = clock.today()) = kind.fileName.replace(".csv", "-$date.csv")

    suspend fun exportCsv(kind: CsvKind): String = Csv.BOM + CsvTransfer.export(kind, snapshot())

    suspend fun previewCsv(kind: CsvKind, text: String): CsvPreview = withContext(Dispatchers.Default) {
        CsvTransfer.preview(kind, text, snapshot())
    }

    /** Imports the valid rows of a previewed file. A safety backup is saved first. */
    suspend fun importCsv(preview: CsvPreview, choice: DuplicateChoice): CsvImportResult = withContext(Dispatchers.IO) {
        val current = snapshot()
        if (current.hasUserData) saveSafetyBackup("before-csv-import")
        val result = CsvTransfer.apply(preview, choice, current, clock.now())
        replaceAll(result.data)
        result
    }
}
