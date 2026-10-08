package io.github.chiragbhatn.expensetracker.backup

import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.Frequency
import io.github.chiragbhatn.expensetracker.domain.IntervalUnit
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.domain.newUuid
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** What the app knows about itself when writing a backup. */
data class AppInfo(val versionName: String, val versionCode: Long)

data class BackupMetadata(
    val formatVersion: String,
    val appVersion: String,
    val schemaVersion: Int?,
    val exportDate: LocalDate?,
    val currency: String,
    /** Rows per sheet as recorded at export time. */
    val counts: Map<String, Int>,
)

/** The checks run on a workbook before anything is restored, in order. */
enum class CheckStep(val label: String) {
    FILE_TYPE("File type"),
    STRUCTURE("Workbook structure"),
    VERSION("Backup format version"),
    SHEETS("Required sheets"),
    IDS("IDs"),
    DATES("Dates"),
    AMOUNTS("Amounts"),
    RELATIONSHIPS("Relationships"),
    DUPLICATES("Duplicate records"),
}

data class Issue(val step: CheckStep, val message: String, val sheet: String? = null, val row: Int? = null) {
    override fun toString(): String = buildString {
        if (sheet != null) append(sheet)
        if (row != null) append(" row ").append(row)
        if (sheet != null || row != null) append(": ")
        append(message)
    }
}

/** The result of reading and checking a backup workbook. [data] is only set when there are no errors. */
data class BackupReadResult(
    val metadata: BackupMetadata?,
    val data: BackupData?,
    val errors: List<Issue>,
    val warnings: List<Issue>,
) {
    val isValid: Boolean get() = errors.isEmpty() && data != null

    /** Steps that ran without errors, for the validation summary. */
    fun passed(step: CheckStep): Boolean = errors.none { it.step == step }
}

/**
 * The full-backup workbook (format [VERSION]). Every table of the app has its
 * own sheet with one record per row and the column names below. Amounts are
 * stored as whole paise (exact) next to a rupee column for reading; when both
 * are present the paise column wins.
 */
object BackupWorkbook {
    const val VERSION = "2.0"
    const val MAJOR = 2
    const val MINOR = 0
    const val SCHEMA_VERSION = 2

    const val METADATA = "Metadata"
    const val SETTINGS = "Settings"
    const val PEOPLE = "People"
    const val EXPENSES = "Expenses"
    const val INCOME = "Income"
    const val LEDGER = "LedgerTransactions"
    const val PAYMENTS = "Payments"
    const val SETTLEMENTS = "Settlements"
    const val CARDS = "CreditCards"
    const val CARD_PAYMENTS = "CardPayments"
    const val RULES = "CashbackRules"
    const val RECURRING = "RecurringExpenses"
    const val CATEGORIES = "Categories"
    const val REMINDERS = "Reminders"
    const val ATTACHMENTS = "Attachments"

    /** Sheets every 2.x backup must have. */
    val REQUIRED_SHEETS = listOf(METADATA, PEOPLE, EXPENSES, INCOME, LEDGER, PAYMENTS, SETTLEMENTS, CARDS, RULES, RECURRING, CATEGORIES)

    /** Settings that are personal to a device and are never restored. */
    val DEVICE_SETTINGS = setOf("app_lock")

    val PEOPLE_COLUMNS = listOf("id", "name", "phone", "email", "address", "notes", "tags", "photo_file", "created_at", "updated_at")
    val CATEGORY_COLUMNS = listOf("id", "name", "kind", "is_default", "created_at", "updated_at")
    val CARD_COLUMNS = listOf(
        "id", "name", "bank", "last_four", "credit_limit", "credit_limit_paise", "statement_day", "due_day", "notes", "active",
        "reminder_days_before", "created_at", "updated_at",
    )
    val RULE_COLUMNS = listOf("id", "merchant", "cashback_percentage", "cashback_basis_points", "enabled", "card_id", "created_at", "updated_at")
    val EXPENSE_COLUMNS = listOf(
        "id", "date", "merchant", "category", "payment_method", "card_id", "original_amount", "original_amount_paise",
        "cashback_percentage", "cashback_basis_points", "cashback_amount", "cashback_amount_paise", "effective_amount",
        "effective_amount_paise", "note", "recurring_id", "created_at", "updated_at",
    )
    val INCOME_COLUMNS = listOf("id", "date", "source", "category", "amount", "amount_paise", "note", "created_at", "updated_at")
    val LEDGER_COLUMNS = listOf(
        "id", "person_id", "person_name", "date", "type", "direction", "amount", "amount_paise", "expense_id", "note", "created_at", "updated_at",
    )
    val CARD_PAYMENT_COLUMNS = listOf("id", "card_id", "date", "amount", "amount_paise", "note", "created_at", "updated_at")
    val RECURRING_COLUMNS = listOf(
        "id", "title", "amount", "amount_paise", "category", "payment_method", "card_id", "start_date", "frequency", "interval_count",
        "interval_unit", "next_date", "occurrence_index", "end_date", "active", "reminder_days_before", "note", "created_at", "updated_at",
    )
    val REMINDER_COLUMNS = listOf("id", "title", "note", "due_date", "person_id", "done", "created_at", "updated_at")
    val ATTACHMENT_COLUMNS = listOf("id", "expense_id", "person_id", "file_name", "mime_type", "size_bytes", "created_at", "updated_at")

    // ---------------------------------------------------------------- writing

    fun write(data: BackupData, app: AppInfo, currency: String, exportedAt: Instant, exportDate: LocalDate, out: OutputStream) {
        val peopleNames = data.people.associate { it.uuid to it.name }
        val sheets = listOf(
            metadataSheet(data, app, currency, exportedAt, exportDate),
            keyValueSheet(SETTINGS, data.settings),
            sheet(PEOPLE, PEOPLE_COLUMNS, data.people, listOf(38, 22, 16, 24, 30, 30, 20, 20, 26, 26)) { p ->
                listOf(t(p.uuid), t(p.name), t(p.phone), t(p.email), t(p.address), t(p.notes), t(p.tags.joinToString(", ")), t(p.photoFile), ts(p.createdAt), ts(p.updatedAt))
            },
            sheet(EXPENSES, EXPENSE_COLUMNS, data.expenses.sortedWith(compareBy({ it.date }, { it.createdAt })), listOf(38, 12, 22, 16, 14, 38, 14, 18, 14, 14, 14, 18, 14, 18, 30, 38, 26, 26)) { e ->
                listOf(
                    t(e.uuid), d(e.date), t(e.merchant), t(e.category), t(e.paymentMethod.name), t(e.cardUuid),
                    rupees(e.originalAmount), paise(e.originalAmount), pct(e.cashbackPercentage), n(e.cashbackPercentage.basisPoints.toLong()),
                    rupees(e.cashbackAmount), paise(e.cashbackAmount), rupees(e.effectiveAmount), paise(e.effectiveAmount),
                    t(e.note), t(e.recurringUuid), ts(e.createdAt), ts(e.updatedAt),
                )
            },
            sheet(INCOME, INCOME_COLUMNS, data.incomes.sortedWith(compareBy({ it.date }, { it.createdAt })), listOf(38, 12, 22, 16, 14, 16, 30, 26, 26)) { i ->
                listOf(t(i.uuid), d(i.date), t(i.source), t(i.category), rupees(i.amount), paise(i.amount), t(i.note), ts(i.createdAt), ts(i.updatedAt))
            },
            ledgerSheet(LEDGER, data.otherLedger, peopleNames),
            ledgerSheet(PAYMENTS, data.payments, peopleNames),
            ledgerSheet(SETTLEMENTS, data.settlements, peopleNames),
            sheet(CARDS, CARD_COLUMNS, data.cards, listOf(38, 20, 16, 10, 14, 18, 14, 10, 30, 8, 20, 26, 26)) { c ->
                listOf(
                    t(c.uuid), t(c.name), t(c.bank), t(c.lastFour), rupees(c.creditLimit), paise(c.creditLimit), n(c.statementDay.toLong()),
                    n(c.dueDay.toLong()), t(c.notes), bool(c.active), c.reminderDaysBefore?.let { n(it.toLong()) }, ts(c.createdAt), ts(c.updatedAt),
                )
            },
            sheet(CARD_PAYMENTS, CARD_PAYMENT_COLUMNS, data.cardPayments.sortedBy { it.date }, listOf(38, 38, 12, 14, 16, 30, 26, 26)) { p ->
                listOf(t(p.uuid), t(p.cardUuid), d(p.date), rupees(p.amount), paise(p.amount), t(p.note), ts(p.createdAt), ts(p.updatedAt))
            },
            sheet(RULES, RULE_COLUMNS, data.cashbackRules, listOf(38, 22, 20, 22, 8, 38, 26, 26)) { r ->
                listOf(t(r.uuid), t(r.merchant), pct(r.percentage), n(r.percentage.basisPoints.toLong()), bool(r.enabled), t(r.cardUuid), ts(r.createdAt), ts(r.updatedAt))
            },
            sheet(RECURRING, RECURRING_COLUMNS, data.recurring, listOf(38, 20, 14, 16, 16, 14, 38, 12, 12, 14, 14, 12, 16, 12, 8, 20, 30, 26, 26)) { r ->
                listOf(
                    t(r.uuid), t(r.title), rupees(r.amount), paise(r.amount), t(r.category), t(r.paymentMethod.name), t(r.cardUuid), d(r.startDate),
                    t(r.frequency.name), n(r.intervalCount.toLong()), t(r.intervalUnit.name), d(r.nextDate), n(r.occurrenceIndex.toLong()), d(r.endDate),
                    bool(r.active), r.reminderDaysBefore?.let { n(it.toLong()) }, t(r.note), ts(r.createdAt), ts(r.updatedAt),
                )
            },
            sheet(CATEGORIES, CATEGORY_COLUMNS, data.categories, listOf(38, 20, 10, 10, 26, 26)) { c ->
                listOf(t(c.uuid), t(c.name), t(c.kind.name), bool(c.isDefault), ts(c.createdAt), ts(c.updatedAt))
            },
            sheet(REMINDERS, REMINDER_COLUMNS, data.reminders, listOf(38, 24, 30, 12, 38, 8, 26, 26)) { r ->
                listOf(t(r.uuid), t(r.title), t(r.note), d(r.dueDate), t(r.personUuid), bool(r.done), ts(r.createdAt), ts(r.updatedAt))
            },
            sheet(ATTACHMENTS, ATTACHMENT_COLUMNS, data.attachments, listOf(38, 38, 38, 30, 14, 12, 26, 26)) { a ->
                listOf(t(a.uuid), t(a.expenseUuid), t(a.personUuid), t(a.fileName), t(a.mimeType), n(a.sizeBytes), ts(a.createdAt), ts(a.updatedAt))
            },
        )
        XlsxWriter.write(sheets, out, title = "Expense Tracker backup $exportDate", created = exportedAt.toString())
    }

    private fun metadataSheet(data: BackupData, app: AppInfo, currency: String, exportedAt: Instant, exportDate: LocalDate): XlsxSheet {
        val rows = linkedMapOf(
            "backup_format_version" to VERSION,
            "app_name" to "Expense Tracker",
            "app_version" to app.versionName,
            "app_version_code" to app.versionCode.toString(),
            "database_schema_version" to SCHEMA_VERSION.toString(),
            "export_date" to exportDate.toString(),
            "exported_at" to exportedAt.toString(),
            "currency" to currency,
            "amounts" to "Columns ending in _paise hold exact amounts in paise (1 rupee = 100 paise); they are what a restore uses.",
            "ids" to "The id columns are permanent record IDs; other sheets refer to records by them.",
            "rows_$PEOPLE" to data.people.size.toString(),
            "rows_$EXPENSES" to data.expenses.size.toString(),
            "rows_$INCOME" to data.incomes.size.toString(),
            "rows_$LEDGER" to data.otherLedger.size.toString(),
            "rows_$PAYMENTS" to data.payments.size.toString(),
            "rows_$SETTLEMENTS" to data.settlements.size.toString(),
            "rows_$CARDS" to data.cards.size.toString(),
            "rows_$CARD_PAYMENTS" to data.cardPayments.size.toString(),
            "rows_$RULES" to data.cashbackRules.size.toString(),
            "rows_$RECURRING" to data.recurring.size.toString(),
            "rows_$CATEGORIES" to data.categories.size.toString(),
            "rows_$REMINDERS" to data.reminders.size.toString(),
            "rows_$ATTACHMENTS" to data.attachments.size.toString(),
        )
        return keyValueSheet(METADATA, rows)
    }

    private fun keyValueSheet(name: String, values: Map<String, String>) =
        XlsxSheet(name, listOf("key", "value"), values.map { (key, value) -> listOf(t(key), t(value)) }, listOf(28, 60))

    private fun ledgerSheet(name: String, entries: List<LedgerRecord>, peopleNames: Map<String, String>) =
        sheet(name, LEDGER_COLUMNS, entries.sortedWith(compareBy({ it.date }, { it.createdAt })), listOf(38, 38, 20, 12, 18, 10, 14, 16, 38, 30, 26, 26)) { l ->
            listOf(
                t(l.uuid), t(l.personUuid), t(peopleNames[l.personUuid]), d(l.date), t(l.type.name), t(l.direction.name),
                rupees(l.amount), paise(l.amount), t(l.expenseUuid), t(l.note), ts(l.createdAt), ts(l.updatedAt),
            )
        }

    private fun <T> sheet(name: String, columns: List<String>, records: List<T>, widths: List<Int>, row: (T) -> List<XlsxCell?>) =
        XlsxSheet(name, columns, records.map(row), widths)

    private fun t(value: String?): XlsxCell? = value?.let { XlsxCell.Text(it) }
    private fun n(value: Long): XlsxCell = XlsxCell.Number(value.toString())
    private fun paise(money: Money): XlsxCell = n(money.paise)
    private fun rupees(money: Money): XlsxCell = XlsxCell.Number(money.toPlainString(), twoDecimals = true)
    private fun pct(percentage: Percentage): XlsxCell = XlsxCell.Number(percentage.toInputString())
    private fun d(date: LocalDate?): XlsxCell? = date?.let { XlsxCell.Text(it.toString()) }
    private fun bool(value: Boolean): XlsxCell = XlsxCell.Text(if (value) "TRUE" else "FALSE")
    private fun ts(millis: Long): XlsxCell? = if (millis <= 0) null else XlsxCell.Text(Instant.ofEpochMilli(millis).toString())

    // ---------------------------------------------------------------- reading

    /**
     * Reads and checks a backup. [fileName] is used for the file type check
     * when known. Nothing is returned in [BackupReadResult.data] unless every
     * check passed.
     */
    fun read(input: InputStream, fileName: String? = null): BackupReadResult {
        val bytes = try {
            input.readBytes()
        } catch (e: Exception) {
            return failure(Issue(CheckStep.FILE_TYPE, "The file could not be opened (${e.message})."))
        }
        return read(bytes, fileName)
    }

    fun read(bytes: ByteArray, fileName: String? = null): BackupReadResult {
        // 1. File type
        if (fileName != null && !fileName.endsWith(".xlsx", ignoreCase = true) && !XlsxReader.looksLikeXlsx(bytes)) {
            return failure(Issue(CheckStep.FILE_TYPE, "\"$fileName\" is not an Excel .xlsx file. Choose a backup created with Export Full Backup."))
        }
        if (!XlsxReader.looksLikeXlsx(bytes)) {
            return failure(Issue(CheckStep.FILE_TYPE, "This is not an Excel .xlsx file. Choose a backup created with Export Full Backup."))
        }
        // 2. Workbook structure
        val workbook = try {
            XlsxReader.read(bytes)
        } catch (e: XlsxException) {
            return failure(Issue(CheckStep.STRUCTURE, e.message ?: "The workbook could not be read."))
        }
        return Parser(workbook).parse()
    }

    private fun failure(issue: Issue) = BackupReadResult(null, null, listOf(issue), emptyList())

    /** Parses "2.0", "2" or "2.1" into major and minor numbers. */
    fun parseVersion(text: String): Pair<Int, Int>? {
        val match = Regex("""^\s*(\d+)(?:\.(\d+))?(?:\.\d+)?\s*$""").matchEntire(text) ?: return null
        return match.groupValues[1].toInt() to (match.groupValues[2].toIntOrNull() ?: 0)
    }

    private class Parser(private val workbook: XlsxWorkbook) {
        private val errors = mutableListOf<Issue>()
        private val warnings = mutableListOf<Issue>()

        fun parse(): BackupReadResult {
            val sheets = workbook.sheets
            // 3. Backup format version
            val metadataRows = sheets[METADATA]
            if (metadataRows == null) {
                errors += Issue(CheckStep.VERSION, "This workbook has no Metadata sheet, so it is not an Expense Tracker backup.")
                return result(null, null)
            }
            val meta = keyValues(metadataRows)
            val versionText = meta["backup_format_version"]
            if (versionText.isNullOrBlank()) {
                errors += Issue(CheckStep.VERSION, "The Metadata sheet has no backup_format_version, so this is not an Expense Tracker backup.")
                return result(null, null)
            }
            val version = parseVersion(versionText)
            val metadata = BackupMetadata(
                formatVersion = versionText.trim(),
                appVersion = meta["app_version"].orEmpty(),
                schemaVersion = meta["database_schema_version"]?.trim()?.toBigDecimalOrNull()?.toInt(),
                exportDate = meta["export_date"]?.let { parseDate(it) },
                currency = meta["currency"].orEmpty().ifBlank { "INR" },
                counts = meta.filterKeys { it.startsWith("rows_") }
                    .mapNotNull { (key, value) -> value.trim().toBigDecimalOrNull()?.toInt()?.let { key.removePrefix("rows_") to it } }
                    .toMap(),
            )
            when {
                version == null -> {
                    errors += Issue(CheckStep.VERSION, "Backup format \"$versionText\" is not a version number this app understands.")
                    return result(metadata, null)
                }
                version.first > MAJOR -> {
                    errors += Issue(
                        CheckStep.VERSION,
                        "This backup uses format ${metadata.formatVersion}, made by a newer version of Expense Tracker. " +
                            "Update the app to restore it; nothing has been changed.",
                    )
                    return result(metadata, null)
                }
                version.first < MAJOR -> {
                    errors += Issue(CheckStep.VERSION, "Backup format ${metadata.formatVersion} is not supported. This app restores format $MAJOR.x backups.")
                    return result(metadata, null)
                }
                version.second > MINOR -> warnings += Issue(
                    CheckStep.VERSION,
                    "This backup was made by a newer app version (format ${metadata.formatVersion}). Anything this version does not know about will be skipped.",
                )
            }

            // 4. Required sheets and columns
            REQUIRED_SHEETS.filter { it !in sheets }.forEach { errors += Issue(CheckStep.SHEETS, "The $it sheet is missing.") }
            listOf(SETTINGS, CARD_PAYMENTS, REMINDERS, ATTACHMENTS).filter { it !in sheets }.forEach {
                warnings += Issue(CheckStep.SHEETS, "The $it sheet is missing; nothing will be restored from it.")
            }
            if (errors.isNotEmpty()) return result(metadata, null)

            val people = table(PEOPLE, listOf("id", "name")) { row ->
                PersonRecord(
                    uuid = row.id(),
                    name = cleanName(row.text("name")).also { if (it.isEmpty()) row.error(CheckStep.IDS, "Name is empty.") },
                    phone = row.text("phone"),
                    email = row.text("email"),
                    address = row.text("address"),
                    notes = row.text("notes"),
                    tags = row.text("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                    photoFile = row.text("photo_file").ifBlank { null },
                    createdAt = row.timestamp("created_at"),
                    updatedAt = row.timestamp("updated_at"),
                )
            }
            val categories = table(CATEGORIES, listOf("id", "name", "kind")) { row ->
                CategoryRecord(
                    uuid = row.id(),
                    name = cleanName(row.text("name")).also { if (it.isEmpty()) row.error(CheckStep.IDS, "Name is empty.") },
                    kind = row.enum("kind", CategoryKind.entries) ?: CategoryKind.EXPENSE,
                    isDefault = row.bool("is_default", false),
                    createdAt = row.timestamp("created_at"),
                    updatedAt = row.timestamp("updated_at"),
                )
            }
            val cards = table(CARDS, listOf("id", "name", "statement_day", "due_day"), anyOf = listOf(listOf("credit_limit_paise", "credit_limit"))) { row ->
                CardRecord(
                    uuid = row.id(),
                    name = cleanName(row.text("name")).also { if (it.isEmpty()) row.error(CheckStep.IDS, "Card name is empty.") },
                    bank = row.text("bank"),
                    lastFour = row.text("last_four").removeSuffix(".0")
                        // A spreadsheet may have turned "0123" into the number 123.
                        .let { if (it.length in 1..3 && it.all(Char::isDigit)) it.padStart(4, '0') else it }
                        .also { if (it.isNotEmpty() && !Regex("""\d{4}""").matches(it)) row.error(CheckStep.IDS, "last_four must be 4 digits.") },
                    creditLimit = row.money("credit_limit_paise", "credit_limit") ?: Money.ZERO,
                    statementDay = row.int("statement_day", 1..31) ?: 1,
                    dueDay = row.int("due_day", 1..31) ?: 1,
                    notes = row.text("notes"),
                    active = row.bool("active", true),
                    reminderDaysBefore = row.int("reminder_days_before", 0..365, required = false),
                    createdAt = row.timestamp("created_at"),
                    updatedAt = row.timestamp("updated_at"),
                )
            }
            val rules = table(RULES, listOf("id", "merchant"), anyOf = listOf(listOf("cashback_basis_points", "cashback_percentage"))) { row ->
                CashbackRuleRecord(
                    uuid = row.id(),
                    merchant = cleanName(row.text("merchant")).also { if (it.isEmpty()) row.error(CheckStep.IDS, "Merchant is empty.") },
                    percentage = row.percentage("cashback_basis_points", "cashback_percentage") ?: Percentage.ZERO,
                    enabled = row.bool("enabled", true),
                    cardUuid = row.ref("card_id"),
                    createdAt = row.timestamp("created_at"),
                    updatedAt = row.timestamp("updated_at"),
                )
            }
            val recurring = table(RECURRING, listOf("id", "title", "start_date", "frequency", "next_date"), anyOf = listOf(listOf("amount_paise", "amount"))) { row ->
                val start = row.date("start_date")
                val next = row.date("next_date")
                val end = row.date("end_date", required = false)
                if (start != null && end != null && end.isBefore(start)) row.error(CheckStep.DATES, "end_date is before start_date.")
                RecurringRecord(
                    uuid = row.id(),
                    title = cleanName(row.text("title")).also { if (it.isEmpty()) row.error(CheckStep.IDS, "Title is empty.") },
                    amount = row.money("amount_paise", "amount") ?: Money.ZERO,
                    category = cleanName(row.text("category")).ifEmpty { "Other" },
                    paymentMethod = row.enum("payment_method", PaymentMethod.entries, required = false) ?: PaymentMethod.OTHER,
                    cardUuid = row.ref("card_id"),
                    startDate = start ?: LocalDate.MIN,
                    frequency = row.enum("frequency", Frequency.entries) ?: Frequency.MONTHLY,
                    intervalCount = row.int("interval_count", 1..1000, required = false) ?: 1,
                    intervalUnit = row.enum("interval_unit", IntervalUnit.entries, required = false) ?: IntervalUnit.MONTHS,
                    nextDate = next ?: LocalDate.MIN,
                    occurrenceIndex = row.int("occurrence_index", 0..1_000_000, required = false) ?: 0,
                    endDate = end,
                    active = row.bool("active", true),
                    reminderDaysBefore = row.int("reminder_days_before", 0..365, required = false),
                    note = row.text("note"),
                    createdAt = row.timestamp("created_at"),
                    updatedAt = row.timestamp("updated_at"),
                )
            }
            val expenses = table(
                EXPENSES,
                listOf("id", "date", "merchant", "payment_method"),
                anyOf = listOf(listOf("original_amount_paise", "original_amount")),
            ) { row -> expense(row) }
            val incomes = table(INCOME, listOf("id", "date"), anyOf = listOf(listOf("amount_paise", "amount"))) { row ->
                IncomeRecord(
                    uuid = row.id(),
                    date = row.date("date") ?: LocalDate.MIN,
                    source = cleanName(row.text("source")),
                    category = cleanName(row.text("category")).ifEmpty { "Other" },
                    amount = row.money("amount_paise", "amount")?.also { if (it.isZero) row.error(CheckStep.AMOUNTS, "Amount must be more than zero.") } ?: Money.ZERO,
                    note = row.text("note"),
                    createdAt = row.timestamp("created_at"),
                    updatedAt = row.timestamp("updated_at"),
                )
            }
            val ledger = listOf(LEDGER, PAYMENTS, SETTLEMENTS).flatMap { sheet ->
                table(sheet, listOf("id", "person_id", "date", "type"), anyOf = listOf(listOf("amount_paise", "amount"))) { row -> ledgerEntry(row) }
            }
            val cardPayments = optionalTable(CARD_PAYMENTS, listOf("id", "card_id", "date"), anyOf = listOf(listOf("amount_paise", "amount"))) { row ->
                CardPaymentRecord(
                    uuid = row.id(),
                    cardUuid = row.ref("card_id").orEmpty(),
                    date = row.date("date") ?: LocalDate.MIN,
                    amount = row.money("amount_paise", "amount")?.also { if (it.isZero) row.error(CheckStep.AMOUNTS, "Amount must be more than zero.") } ?: Money.ZERO,
                    note = row.text("note"),
                    createdAt = row.timestamp("created_at"),
                    updatedAt = row.timestamp("updated_at"),
                )
            }
            val reminders = optionalTable(REMINDERS, listOf("id", "title", "due_date")) { row ->
                ReminderRecord(
                    uuid = row.id(),
                    title = cleanName(row.text("title")).also { if (it.isEmpty()) row.error(CheckStep.IDS, "Title is empty.") },
                    note = row.text("note"),
                    dueDate = row.date("due_date") ?: LocalDate.MIN,
                    personUuid = row.ref("person_id"),
                    done = row.bool("done", false),
                    createdAt = row.timestamp("created_at"),
                    updatedAt = row.timestamp("updated_at"),
                )
            }
            val attachments = optionalTable(ATTACHMENTS, listOf("id", "file_name")) { row ->
                AttachmentRecord(
                    uuid = row.id(),
                    expenseUuid = row.ref("expense_id"),
                    personUuid = row.ref("person_id"),
                    fileName = row.text("file_name"),
                    mimeType = row.text("mime_type").ifBlank { "application/octet-stream" },
                    sizeBytes = row.long("size_bytes") ?: 0,
                    createdAt = row.timestamp("created_at"),
                    updatedAt = row.timestamp("updated_at"),
                )
            }
            val settings = sheets[SETTINGS]?.let(::keyValues).orEmpty().filterKeys { it !in DEVICE_SETTINGS }

            checkCounts(metadata, people.size, expenses.size, incomes.size, cards.size, rules.size, recurring.size, categories.size)
            if (errors.isNotEmpty()) return result(metadata, null)

            val parsed = BackupData(
                settings = settings,
                people = people,
                categories = categories,
                cards = cards,
                cashbackRules = rules,
                expenses = expenses,
                incomes = incomes,
                ledger = ledger,
                cardPayments = cardPayments,
                recurring = recurring,
                reminders = reminders,
                attachments = attachments,
            )
            val checked = BackupChecks(parsed, errors, warnings).run()
            return result(metadata, if (errors.isEmpty()) checked else null)
        }

        private fun expense(row: Row): ExpenseRecord {
            val original = row.money("original_amount_paise", "original_amount") ?: Money.ZERO
            val percentage = row.percentage("cashback_basis_points", "cashback_percentage", required = false) ?: Percentage.ZERO
            val cashback = row.money("cashback_amount_paise", "cashback_amount", required = false) ?: percentage.of(original)
            val effective = row.money("effective_amount_paise", "effective_amount", required = false) ?: (original - cashback)
            if (original.isZero) row.error(CheckStep.AMOUNTS, "Original amount must be more than zero.")
            if (cashback > original) row.error(CheckStep.AMOUNTS, "Cashback ${cashback.toPlainString()} is more than the original amount ${original.toPlainString()}.")
            if (cashback + effective != original) {
                row.error(
                    CheckStep.AMOUNTS,
                    "Original ${original.toPlainString()} − cashback ${cashback.toPlainString()} should equal effective ${effective.toPlainString()}.",
                )
            } else if (cashback != percentage.of(original)) {
                row.warn(CheckStep.AMOUNTS, "Cashback ${cashback.toPlainString()} is not ${percentage.format()} of ${original.toPlainString()}; it is restored as recorded.")
            }
            return ExpenseRecord(
                uuid = row.id(),
                date = row.date("date") ?: LocalDate.MIN,
                merchant = cleanName(row.text("merchant")).also { if (it.isEmpty()) row.error(CheckStep.IDS, "Merchant is empty.") },
                category = cleanName(row.text("category")).ifEmpty { "Other" },
                paymentMethod = row.enum("payment_method", PaymentMethod.entries) ?: PaymentMethod.OTHER,
                cardUuid = row.ref("card_id"),
                originalAmount = original,
                cashbackPercentage = percentage,
                cashbackAmount = cashback,
                effectiveAmount = effective,
                note = row.text("note"),
                recurringUuid = row.ref("recurring_id"),
                createdAt = row.timestamp("created_at"),
                updatedAt = row.timestamp("updated_at"),
            )
        }

        private fun ledgerEntry(row: Row): LedgerRecord {
            val type = row.enum("type", LedgerType.entries) ?: LedgerType.ADJUSTMENT
            val given = row.enum("direction", UdhaarDirection.entries, required = false)
            val fixed = type.fixedDirection
            val direction = when {
                fixed != null && given != null && given != fixed -> {
                    row.error(CheckStep.AMOUNTS, "${type.label} entries must have direction ${fixed.name}, not ${given.name}.")
                    fixed
                }
                fixed != null -> fixed
                given != null -> given
                else -> {
                    row.error(CheckStep.AMOUNTS, "${type.label} entries need a direction (GAVE or GOT).")
                    UdhaarDirection.GAVE
                }
            }
            return LedgerRecord(
                uuid = row.id(),
                personUuid = row.ref("person_id").orEmpty(),
                date = row.date("date") ?: LocalDate.MIN,
                type = type,
                direction = direction,
                amount = row.money("amount_paise", "amount")?.also { if (it.isZero) row.error(CheckStep.AMOUNTS, "Amount must be more than zero.") } ?: Money.ZERO,
                expenseUuid = row.ref("expense_id"),
                note = row.text("note"),
                createdAt = row.timestamp("created_at"),
                updatedAt = row.timestamp("updated_at"),
            )
        }

        private fun checkCounts(metadata: BackupMetadata, people: Int, expenses: Int, incomes: Int, cards: Int, rules: Int, recurring: Int, categories: Int) {
            val actual = mapOf(
                PEOPLE to people,
                EXPENSES to expenses,
                INCOME to incomes,
                LEDGER to (workbook.sheets[LEDGER]?.let { dataRows(it).size } ?: 0),
                PAYMENTS to (workbook.sheets[PAYMENTS]?.let { dataRows(it).size } ?: 0),
                SETTLEMENTS to (workbook.sheets[SETTLEMENTS]?.let { dataRows(it).size } ?: 0),
                CARDS to cards,
                RULES to rules,
                RECURRING to recurring,
                CATEGORIES to categories,
            )
            actual.forEach { (sheet, count) ->
                val recorded = metadata.counts[sheet] ?: return@forEach
                if (recorded != count) {
                    warnings += Issue(CheckStep.STRUCTURE, "The $sheet sheet has $count rows but the backup recorded $recorded; it may have been edited.")
                }
            }
        }

        private fun result(metadata: BackupMetadata?, data: BackupData?) = BackupReadResult(metadata, data, errors.toList(), warnings.toList())

        private fun keyValues(rows: List<List<String>>): Map<String, String> =
            rows.drop(1).mapNotNull { row ->
                val key = row.getOrNull(0)?.trim().orEmpty()
                if (key.isEmpty()) null else key to (row.getOrNull(1) ?: "")
            }.toMap()

        private fun dataRows(rows: List<List<String>>) = rows.drop(1).filter { row -> row.any { it.isNotBlank() } }

        private fun <T> optionalTable(sheet: String, required: List<String>, anyOf: List<List<String>> = emptyList(), parse: (Row) -> T): List<T> =
            if (sheet in workbook.sheets) table(sheet, required, anyOf, parse) else emptyList()

        private fun <T> table(sheet: String, required: List<String>, anyOf: List<List<String>> = emptyList(), parse: (Row) -> T): List<T> {
            val rows = workbook.sheets[sheet] ?: return emptyList()
            val header = rows.firstOrNull().orEmpty().mapIndexed { index, name -> columnKey(name) to index }.toMap()
            val missing = required.filter { it !in header } + anyOf.filter { group -> group.none { it in header } }.map { it.joinToString(" or ") }
            if (missing.isNotEmpty()) {
                errors += Issue(CheckStep.SHEETS, "Missing column${if (missing.size > 1) "s" else ""}: ${missing.joinToString()}.", sheet)
                return emptyList()
            }
            return rows.drop(1).mapIndexedNotNull { index, cells ->
                if (cells.all { it.isBlank() }) null else parse(Row(sheet, index + 2, cells, header))
            }
        }

        private fun columnKey(name: String) = name.trim().lowercase().replace(Regex("""[\s\-]+"""), "_")

        inner class Row(val sheet: String, val number: Int, val cells: List<String>, val columns: Map<String, Int>) {
            fun error(step: CheckStep, message: String) {
                errors += Issue(step, message, sheet, number)
            }

            fun warn(step: CheckStep, message: String) {
                warnings += Issue(step, message, sheet, number)
            }

            fun text(column: String): String = columns[column]?.let { cells.getOrNull(it) }?.trim().orEmpty()

            fun id(): String {
                val id = text("id")
                when {
                    id.isEmpty() -> error(CheckStep.IDS, "The id is empty.")
                    id.length > 100 -> error(CheckStep.IDS, "The id is too long.")
                }
                return id
            }

            fun ref(column: String): String? = text(column).ifEmpty { null }

            fun date(column: String, required: Boolean = true): LocalDate? {
                val raw = text(column)
                if (raw.isEmpty()) {
                    if (required) error(CheckStep.DATES, "$column is empty.")
                    return null
                }
                return parseDate(raw) ?: run {
                    error(CheckStep.DATES, "$column \"$raw\" is not a date (use YYYY-MM-DD).")
                    null
                }
            }

            fun timestamp(column: String): Long {
                val raw = text(column)
                if (raw.isEmpty()) return 0
                return parseTimestamp(raw) ?: run {
                    error(CheckStep.DATES, "$column \"$raw\" is not a date and time.")
                    0
                }
            }

            /** Reads the exact paise column, or the rupee column when paise is empty. */
            fun money(paiseColumn: String, rupeeColumn: String, required: Boolean = true): Money? {
                val paiseText = text(paiseColumn)
                val rupeeText = text(rupeeColumn)
                val fromPaise = if (paiseText.isEmpty()) null else parsePaise(paiseText).also {
                    if (it == null) error(CheckStep.AMOUNTS, "$paiseColumn \"$paiseText\" is not a whole number of paise.")
                }
                val fromRupees = if (rupeeText.isEmpty()) null else parseRupees(rupeeText).also {
                    if (it == null && paiseText.isEmpty()) error(CheckStep.AMOUNTS, "$rupeeColumn \"$rupeeText\" is not an amount.")
                }
                if (paiseText.isEmpty() && rupeeText.isEmpty()) {
                    if (required) error(CheckStep.AMOUNTS, "$paiseColumn is empty.")
                    return null
                }
                val amount = fromPaise ?: fromRupees ?: return null
                if (amount.isNegative) {
                    error(CheckStep.AMOUNTS, "Amounts cannot be negative.")
                    return null
                }
                if (fromPaise != null && fromRupees != null && fromPaise != fromRupees) {
                    warn(CheckStep.AMOUNTS, "$rupeeColumn and $paiseColumn differ; $paiseColumn (${fromPaise.toPlainString()}) is used.")
                }
                return amount
            }

            fun percentage(bpsColumn: String, percentColumn: String, required: Boolean = true): Percentage? {
                val bpsText = text(bpsColumn)
                val percentText = text(percentColumn).removeSuffix("%").trim()
                val bps = when {
                    bpsText.isNotEmpty() -> bpsText.toBigDecimalOrNull()?.takeIf { it.stripTrailingZeros().scale() <= 0 }?.toInt()
                    percentText.isNotEmpty() -> percentText.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toInt()
                    else -> {
                        if (required) error(CheckStep.AMOUNTS, "$bpsColumn is empty.")
                        return null
                    }
                }
                if (bps == null || bps !in 0..10_000) {
                    error(CheckStep.AMOUNTS, "Cashback percentage must be between 0 and 100.")
                    return null
                }
                return Percentage(bps)
            }

            fun int(column: String, range: IntRange, required: Boolean = true): Int? {
                val raw = text(column)
                if (raw.isEmpty()) {
                    if (required) error(CheckStep.AMOUNTS, "$column is empty.")
                    return null
                }
                val value = raw.toBigDecimalOrNull()?.takeIf { it.stripTrailingZeros().scale() <= 0 }?.toInt()
                if (value == null || value !in range) {
                    error(CheckStep.AMOUNTS, "$column must be a whole number from ${range.first} to ${range.last}.")
                    return null
                }
                return value
            }

            fun long(column: String): Long? = text(column).toBigDecimalOrNull()?.toLong()

            fun bool(column: String, default: Boolean): Boolean = when (text(column).lowercase()) {
                "true", "1", "yes", "y" -> true
                "false", "0", "no", "n" -> false
                "" -> default
                else -> {
                    error(CheckStep.IDS, "$column must be TRUE or FALSE.")
                    default
                }
            }

            fun <E : Enum<E>> enum(column: String, values: List<E>, required: Boolean = true): E? {
                val raw = text(column)
                if (raw.isEmpty()) {
                    if (required) error(CheckStep.IDS, "$column is empty.")
                    return null
                }
                val key = raw.uppercase().replace(' ', '_')
                return values.firstOrNull { it.name == key } ?: run {
                    error(CheckStep.IDS, "$column \"$raw\" is not one of ${values.joinToString { it.name }}.")
                    null
                }
            }
        }
    }

    /** ISO dates ("2026-10-08"), or the day numbers spreadsheets use for dates. */
    fun parseDate(raw: String): LocalDate? {
        val text = raw.trim()
        try {
            return LocalDate.parse(text.take(10))
        } catch (_: DateTimeParseException) {
        }
        val serial = text.toBigDecimalOrNull() ?: return null
        if (serial < BigDecimal(61) || serial > BigDecimal(2_958_465)) return null
        return LocalDate.of(1899, 12, 30).plusDays(serial.setScale(0, RoundingMode.FLOOR).toLong())
    }

    fun parseTimestamp(raw: String): Long? {
        val text = raw.trim()
        try {
            return Instant.parse(text).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        return parseDate(text)?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
    }

    fun parsePaise(raw: String): Money? {
        val value = raw.trim().toBigDecimalOrNull() ?: return null
        return try {
            Money(value.setScale(0, RoundingMode.UNNECESSARY).longValueExact())
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun parseRupees(raw: String): Money? {
        val cleaned = raw.trim().replace(",", "").replace("₹", "").trim()
        val value = cleaned.toBigDecimalOrNull() ?: return null
        return try {
            Money(value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact())
        } catch (_: ArithmeticException) {
            null
        }
    }
}

/** Checks across sheets: links between records and duplicates. Fixes what can be fixed safely and warns. */
private class BackupChecks(
    private val data: BackupData,
    private val errors: MutableList<Issue>,
    private val warnings: MutableList<Issue>,
) {
    fun run(): BackupData {
        // 5. IDs: unique within each table (ledger entries share one table).
        duplicateIds(BackupWorkbook.PEOPLE, data.people.map { it.uuid })
        duplicateIds(BackupWorkbook.CATEGORIES, data.categories.map { it.uuid })
        duplicateIds(BackupWorkbook.CARDS, data.cards.map { it.uuid })
        duplicateIds(BackupWorkbook.RULES, data.cashbackRules.map { it.uuid })
        duplicateIds(BackupWorkbook.EXPENSES, data.expenses.map { it.uuid })
        duplicateIds(BackupWorkbook.INCOME, data.incomes.map { it.uuid })
        duplicateIds("Ledger sheets", data.ledger.map { it.uuid })
        duplicateIds(BackupWorkbook.CARD_PAYMENTS, data.cardPayments.map { it.uuid })
        duplicateIds(BackupWorkbook.RECURRING, data.recurring.map { it.uuid })
        duplicateIds(BackupWorkbook.REMINDERS, data.reminders.map { it.uuid })
        duplicateIds(BackupWorkbook.ATTACHMENTS, data.attachments.map { it.uuid })

        // 9. Duplicate records: names that must be unique.
        data.people.groupBy { nameKey(it.name) }.filter { it.value.size > 1 }.forEach { (_, same) ->
            errors += Issue(CheckStep.DUPLICATES, "${same.size} people are named \"${same.first().name}\"; names must be unique.", BackupWorkbook.PEOPLE)
        }
        data.categories.groupBy { nameKey(it.name) to it.kind }.filter { it.value.size > 1 }.forEach { (_, same) ->
            errors += Issue(CheckStep.DUPLICATES, "The ${same.first().kind.name.lowercase()} category \"${same.first().name}\" appears ${same.size} times.", BackupWorkbook.CATEGORIES)
        }
        data.cashbackRules.groupBy { nameKey(it.merchant) to it.cardUuid }.filter { it.value.size > 1 }.forEach { (_, same) ->
            errors += Issue(CheckStep.DUPLICATES, "There are ${same.size} rules for \"${same.first().merchant}\" on the same card.", BackupWorkbook.RULES)
        }

        // 8. Relationships.
        val people = data.people.map { it.uuid }.toSet()
        val cards = data.cards.map { it.uuid }.toSet()
        val expenses = data.expenses.associateBy { it.uuid }
        val recurring = data.recurring.map { it.uuid }.toSet()

        data.ledger.filter { it.personUuid !in people }.forEach {
            errors += Issue(CheckStep.RELATIONSHIPS, "Ledger entry ${it.uuid} refers to a person (${it.personUuid.ifEmpty { "blank" }}) that is not in the People sheet.")
        }
        data.cardPayments.filter { it.cardUuid !in cards }.forEach {
            errors += Issue(CheckStep.RELATIONSHIPS, "Card payment ${it.uuid} refers to a card that is not in the CreditCards sheet.")
        }

        var droppedCardLinks = 0
        var droppedRecurringLinks = 0
        val fixedExpenses = data.expenses.map { expense ->
            var fixed = expense
            if (expense.cardUuid != null && expense.cardUuid !in cards) {
                droppedCardLinks++
                fixed = fixed.copy(cardUuid = null)
            }
            if (expense.recurringUuid != null && expense.recurringUuid !in recurring) {
                droppedRecurringLinks++
                fixed = fixed.copy(recurringUuid = null)
            }
            fixed
        }
        val fixedRecurring = data.recurring.map { item ->
            if (item.cardUuid != null && item.cardUuid !in cards) {
                droppedCardLinks++
                item.copy(cardUuid = null)
            } else {
                item
            }
        }
        val (keptRules, orphanRules) = data.cashbackRules.partition { it.cardUuid == null || it.cardUuid in cards }
        orphanRules.forEach {
            warnings += Issue(CheckStep.RELATIONSHIPS, "The ${it.merchant} rule is for a card that is not in the backup and will be skipped.", BackupWorkbook.RULES)
        }
        if (droppedCardLinks > 0) warnings += Issue(CheckStep.RELATIONSHIPS, "$droppedCardLinks records refer to cards not in the backup; they will be restored without a card.")
        if (droppedRecurringLinks > 0) warnings += Issue(CheckStep.RELATIONSHIPS, "$droppedRecurringLinks expenses refer to recurring expenses not in the backup; the link is removed.")

        var convertedShares = 0
        var droppedExpenseLinks = 0
        val fixedLedger = data.ledger.map { entry ->
            when {
                entry.type == LedgerType.EXPENSE_SHARE && (entry.expenseUuid == null || entry.expenseUuid !in expenses) -> {
                    convertedShares++
                    entry.copy(type = LedgerType.UDHAAR_GIVEN, expenseUuid = null)
                }
                entry.type != LedgerType.EXPENSE_SHARE && entry.expenseUuid != null -> {
                    droppedExpenseLinks++
                    entry.copy(expenseUuid = null)
                }
                else -> entry
            }
        }
        if (convertedShares > 0) {
            warnings += Issue(CheckStep.RELATIONSHIPS, "$convertedShares expense shares refer to expenses not in the backup; they are restored as udhaar given, keeping balances unchanged.")
        }
        if (droppedExpenseLinks > 0) warnings += Issue(CheckStep.RELATIONSHIPS, "$droppedExpenseLinks ledger entries had an expense link they cannot have; it is removed.")

        // Shares of one expense: each person once, and never more than the amount charged.
        fixedLedger.filter { it.type == LedgerType.EXPENSE_SHARE }.groupBy { it.expenseUuid!! }.forEach { (expenseUuid, shares) ->
            val expense = expenses.getValue(expenseUuid)
            if (shares.map { it.personUuid }.toSet().size != shares.size) {
                errors += Issue(CheckStep.DUPLICATES, "Expense ${expense.merchant} on ${expense.date} has the same person twice.", BackupWorkbook.LEDGER)
            }
            val total = shares.fold(Money.ZERO) { sum, share -> sum + share.amount }
            if (total > expense.originalAmount) {
                errors += Issue(
                    CheckStep.AMOUNTS,
                    "Shares of ${expense.merchant} on ${expense.date} add up to ${total.toPlainString()}, more than its original amount ${expense.originalAmount.toPlainString()}.",
                    BackupWorkbook.LEDGER,
                )
            }
        }

        val (keptReminders, droppedPersonLinks) = data.reminders.partition { it.personUuid == null || it.personUuid in people }
        if (droppedPersonLinks.isNotEmpty()) {
            warnings += Issue(CheckStep.RELATIONSHIPS, "${droppedPersonLinks.size} reminders refer to people not in the backup; they are kept without the person.")
        }
        val (keptAttachments, orphanAttachments) = data.attachments.partition { attachment ->
            (attachment.expenseUuid != null && attachment.expenseUuid in expenses) || (attachment.personUuid != null && attachment.personUuid in people)
        }
        if (orphanAttachments.isNotEmpty()) {
            warnings += Issue(CheckStep.RELATIONSHIPS, "${orphanAttachments.size} attachments belong to records not in the backup and are skipped.")
        }

        // Categories used by expenses, income or recurring expenses but missing from the sheet are added.
        val known = data.categories.map { nameKey(it.name) to it.kind }.toSet()
        val missingExpense = (fixedExpenses.map { it.category } + fixedRecurring.map { it.category })
            .filter { (nameKey(it) to CategoryKind.EXPENSE) !in known }.distinctBy(::nameKey)
        val missingIncome = data.incomes.map { it.category }.filter { (nameKey(it) to CategoryKind.INCOME) !in known }.distinctBy(::nameKey)
        val added = missingExpense.map { CategoryRecord(newUuid(), it, CategoryKind.EXPENSE, isDefault = false) } +
            missingIncome.map { CategoryRecord(newUuid(), it, CategoryKind.INCOME, isDefault = false) }
        if (added.isNotEmpty()) {
            warnings += Issue(CheckStep.RELATIONSHIPS, "Categories used but not listed will be added: ${added.joinToString { it.name }}.")
        }

        val legacyShares = fixedLedger.filter { it.type == LedgerType.EXPENSE_SHARE }.groupBy { it.expenseUuid!! }
            .count { (uuid, shares) -> shares.fold(Money.ZERO) { sum, share -> sum + share.amount } > expenses.getValue(uuid).effectiveAmount }
        if (legacyShares > 0) {
            warnings += Issue(
                CheckStep.AMOUNTS,
                "$legacyShares expenses charge people more than the amount after cashback (recorded before version 2). They are restored unchanged.",
            )
        }

        return data.copy(
            categories = data.categories + added,
            cashbackRules = keptRules,
            expenses = fixedExpenses,
            recurring = fixedRecurring,
            ledger = fixedLedger,
            reminders = keptReminders + droppedPersonLinks.map { it.copy(personUuid = null) },
            attachments = keptAttachments,
        )
    }

    private fun duplicateIds(table: String, ids: List<String>) {
        ids.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.take(20).forEach { id ->
            errors += Issue(CheckStep.IDS, "The id $id is used more than once.", table)
        }
    }
}
