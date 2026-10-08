package io.github.chiragbhatn.expensetracker.backup

import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.LedgerSummary
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.domain.UdhaarEntry
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.domain.newUuid
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class CsvKind(val label: String, val fileName: String) {
    EXPENSES("Expenses", "expenses.csv"),
    INCOME("Income", "income.csv"),
    PEOPLE("People", "people.csv"),
    UDHAAR("Udhaar transactions", "udhaar-transactions.csv"),
    PAYMENTS("Payments", "payments.csv"),
}

/** What to do with imported rows that match a record already in the app. */
enum class DuplicateChoice(val label: String) {
    SKIP("Skip"),
    UPDATE("Update"),
    IMPORT_AS_NEW("Import as new"),
}

/** One imported row and what was found while checking it. */
data class CsvRow(
    val line: Int,
    val summary: String,
    val errors: List<String>,
    val warnings: List<String>,
    val duplicate: CsvDuplicate?,
    val draft: CsvDraft?,
) {
    val isValid: Boolean get() = errors.isEmpty() && draft != null
}

data class CsvDuplicate(val existingUuid: String, val reason: String)

data class CsvPreview(val kind: CsvKind, val rows: List<CsvRow>, val fileErrors: List<String>) {
    val valid: List<CsvRow> get() = rows.filter { it.isValid }
    val invalid: List<CsvRow> get() = rows.filter { !it.isValid }
    val duplicates: List<CsvRow> get() = valid.filter { it.duplicate != null }
    val canImport: Boolean get() = fileErrors.isEmpty() && valid.isNotEmpty()
}

data class CsvImportResult(val data: BackupData, val added: Int, val updated: Int, val skipped: Int, val peopleAdded: Int, val categoriesAdded: Int)

sealed interface CsvDraft {
    val uuid: String?

    /** Optional values are null when the file has no column for them, so an update keeps what was there. */
    data class ExpenseDraft(
        override val uuid: String?,
        val date: LocalDate,
        val merchant: String,
        val category: String?,
        val paymentMethod: PaymentMethod?,
        val card: CardLink,
        val original: Money,
        val percentage: Percentage,
        val cashback: Money,
        val effective: Money,
        val shares: List<Pair<String, Money>>,
        val note: String?,
    ) : CsvDraft

    sealed interface CardLink {
        data object NotGiven : CardLink
        data class To(val cardUuid: String?) : CardLink
    }

    data class IncomeDraft(override val uuid: String?, val date: LocalDate, val source: String, val category: String, val amount: Money, val note: String) : CsvDraft

    data class PersonDraft(
        override val uuid: String?,
        val name: String,
        val phone: String?,
        val email: String?,
        val address: String?,
        val notes: String?,
        val tags: List<String>?,
    ) : CsvDraft

    data class LedgerDraft(
        override val uuid: String?,
        val date: LocalDate,
        val person: String,
        val type: LedgerType,
        val direction: UdhaarDirection,
        val amount: Money,
        val note: String,
    ) : CsvDraft
}

/**
 * CSV export and import for expenses, income, people, udhaar transactions and
 * payments. Amounts are in rupees with up to two decimals, dates are
 * YYYY-MM-DD (DD/MM/YYYY is also read), and `id` is the record's permanent ID.
 */
object CsvTransfer {
    val EXPENSE_COLUMNS = listOf(
        "id", "date", "merchant", "category", "payment_method", "card_name", "card_last_four", "original_amount", "cashback_percentage",
        "cashback_amount", "effective_amount", "my_share", "shared_with", "note",
    )
    val INCOME_COLUMNS = listOf("id", "date", "source", "category", "amount", "note")
    val PEOPLE_COLUMNS = listOf("id", "name", "phone", "email", "address", "notes", "tags", "balance", "owes_you", "you_owe", "credit")
    val LEDGER_COLUMNS = listOf("id", "date", "person", "type", "direction", "amount", "expense_id", "merchant", "note")
    val PAYMENT_COLUMNS = listOf("id", "date", "person", "type", "direction", "amount", "note")

    private val REQUIRED = mapOf(
        CsvKind.EXPENSES to listOf("date", "merchant", "original_amount"),
        CsvKind.INCOME to listOf("date", "amount"),
        CsvKind.PEOPLE to listOf("name"),
        CsvKind.UDHAAR to listOf("date", "person", "type", "amount"),
        CsvKind.PAYMENTS to listOf("date", "person", "type", "amount"),
    )

    fun columns(kind: CsvKind) = when (kind) {
        CsvKind.EXPENSES -> EXPENSE_COLUMNS
        CsvKind.INCOME -> INCOME_COLUMNS
        CsvKind.PEOPLE -> PEOPLE_COLUMNS
        CsvKind.UDHAAR -> LEDGER_COLUMNS
        CsvKind.PAYMENTS -> PAYMENT_COLUMNS
    }

    // ---------------------------------------------------------------- export

    /** The CSV text, without a byte-order mark. */
    fun export(kind: CsvKind, data: BackupData): String {
        val people = data.people.associateBy { it.uuid }
        val cards = data.cards.associateBy { it.uuid }
        val expenses = data.expenses.associateBy { it.uuid }
        val sharesByExpense = data.ledger.filter { it.type == LedgerType.EXPENSE_SHARE }.groupBy { it.expenseUuid }
        val rows: List<List<String>> = when (kind) {
            CsvKind.EXPENSES -> data.expenses.sortedWith(compareBy({ it.date }, { it.createdAt })).map { e ->
                val shares = sharesByExpense[e.uuid].orEmpty()
                val card = e.cardUuid?.let(cards::get)
                listOf(
                    e.uuid, e.date.toString(), text(e.merchant), text(e.category), e.paymentMethod.name, text(card?.name.orEmpty()), card?.lastFour.orEmpty(),
                    e.originalAmount.toPlainString(), e.cashbackPercentage.toInputString(), e.cashbackAmount.toPlainString(), e.effectiveAmount.toPlainString(),
                    (e.effectiveAmount - shares.fold(Money.ZERO) { sum, s -> sum + s.amount }).toPlainString(),
                    text(shares.joinToString("; ") { "${people[it.personUuid]?.name.orEmpty()}=${it.amount.toPlainString()}" }),
                    text(e.note),
                )
            }
            CsvKind.INCOME -> data.incomes.sortedWith(compareBy({ it.date }, { it.createdAt })).map { i ->
                listOf(i.uuid, i.date.toString(), text(i.source), text(i.category), i.amount.toPlainString(), text(i.note))
            }
            CsvKind.PEOPLE -> {
                val byPerson = data.ledger.groupBy { it.personUuid }
                data.people.sortedBy { it.name.lowercase() }.map { p ->
                    val summary = LedgerSummary.of(byPerson[p.uuid].orEmpty().map(::toEntry))
                    listOf(
                        p.uuid, text(p.name), text(p.phone), text(p.email), text(p.address), text(p.notes), text(p.tags.joinToString(", ")),
                        summary.balance.toPlainString(), summary.receivable.toPlainString(), summary.payable.toPlainString(), summary.credit.toPlainString(),
                    )
                }
            }
            CsvKind.UDHAAR -> data.otherLedger.sortedWith(compareBy({ it.date }, { it.createdAt })).map { l ->
                listOf(
                    l.uuid, l.date.toString(), text(people[l.personUuid]?.name.orEmpty()), l.type.name, l.direction.name, l.amount.toPlainString(),
                    l.expenseUuid.orEmpty(), text(l.expenseUuid?.let(expenses::get)?.merchant.orEmpty()), text(l.note),
                )
            }
            CsvKind.PAYMENTS -> (data.payments + data.settlements).sortedWith(compareBy({ it.date }, { it.createdAt })).map { l ->
                listOf(l.uuid, l.date.toString(), text(people[l.personUuid]?.name.orEmpty()), l.type.name, l.direction.name, l.amount.toPlainString(), text(l.note))
            }
        }
        return Csv.write(listOf(columns(kind)) + rows)
    }

    private fun text(value: String) = Csv.safeText(value)

    private fun toEntry(record: LedgerRecord) = UdhaarEntry(
        id = record.id,
        personId = 0,
        direction = record.direction,
        amount = record.amount,
        date = record.date,
        note = record.note,
        expenseId = null,
        expenseMerchant = null,
        type = record.type,
        createdAtMillis = record.createdAt,
    )

    // ---------------------------------------------------------------- import

    fun preview(kind: CsvKind, text: String, existing: BackupData): CsvPreview {
        val table = try {
            Csv.parse(text)
        } catch (e: CsvException) {
            return CsvPreview(kind, emptyList(), listOf(e.message ?: "The file could not be read."))
        }
        if (table.isEmpty()) return CsvPreview(kind, emptyList(), listOf("The file is empty."))
        val header = table.first().map { it.trim().lowercase().replace(Regex("""[\s\-]+"""), "_") }
        val missing = REQUIRED.getValue(kind).filter { it !in header }
        if (missing.isNotEmpty()) {
            return CsvPreview(
                kind,
                emptyList(),
                listOf("This file doesn't match the ${kind.label} format: missing column${if (missing.size > 1) "s" else ""} ${missing.joinToString()}."),
            )
        }
        val context = Context(existing)
        val seenIds = mutableSetOf<String>()
        val rows = table.drop(1).mapIndexedNotNull { index, cells ->
            if (cells.all { it.isBlank() }) return@mapIndexedNotNull null
            val values = header.mapIndexed { column, name -> name to Csv.readText(cells.getOrNull(column)?.trim().orEmpty()) }.toMap()
            val row = parseRow(kind, values, context)
            val id = row.draft?.uuid
            if (id != null && !seenIds.add(id)) {
                row.copy(line = index + 2, errors = row.errors + "The id $id appears more than once in this file.")
            } else {
                row.copy(line = index + 2)
            }
        }
        return CsvPreview(kind, rows, emptyList())
    }

    private class Context(val existing: BackupData) {
        val peopleByKey = existing.people.associateBy { nameKey(it.name) }
        val categories = existing.categories.map { nameKey(it.name) to it.kind }.toSet()
    }

    private fun parseRow(kind: CsvKind, values: Map<String, String>, context: Context): CsvRow {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        fun value(name: String) = values[name].orEmpty().trim()
        fun optional(name: String) = if (name in values) value(name) else null
        val uuid = value("id").ifEmpty { null }?.also { if (it.length > 100) errors += "The id is too long." }
        val date = value("date").let { raw ->
            if (raw.isEmpty()) null else parseDate(raw).also { if (it == null) errors += "Date \"$raw\" is not a date (use YYYY-MM-DD)." }
        }
        if (kind != CsvKind.PEOPLE && value("date").isEmpty()) errors += "Date is required."

        val draft: CsvDraft? = when (kind) {
            CsvKind.EXPENSES -> expenseDraft(uuid, date, ::value, ::optional, context, errors, warnings)
            CsvKind.INCOME -> {
                val amount = amount(value("amount"), "Amount", errors)
                if (amount != null && amount.isZero) errors += "Amount must be more than zero."
                val category = cleanName(value("category")).ifEmpty { "Other" }
                if ((nameKey(category) to CategoryKind.INCOME) !in context.categories) warnings += "New income category \"$category\" will be added."
                if (date != null && amount != null) CsvDraft.IncomeDraft(uuid, date, cleanName(value("source")), category, amount, value("note")) else null
            }
            CsvKind.PEOPLE -> {
                val name = cleanName(value("name"))
                if (name.isEmpty()) errors += "Name is required."
                if (name.isEmpty()) {
                    null
                } else {
                    CsvDraft.PersonDraft(
                        uuid = uuid,
                        name = name,
                        phone = optional("phone"),
                        email = optional("email"),
                        address = optional("address"),
                        notes = optional("notes"),
                        tags = optional("tags")?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct(),
                    )
                }
            }
            CsvKind.UDHAAR, CsvKind.PAYMENTS -> ledgerDraft(uuid, date, ::value, context, errors, warnings)
        }
        val duplicate = draft?.let { findDuplicate(it, context) }
        return CsvRow(0, summarize(kind, values), errors, warnings, duplicate, if (errors.isEmpty()) draft else null)
    }

    private fun expenseDraft(
        uuid: String?,
        date: LocalDate?,
        value: (String) -> String,
        optional: (String) -> String?,
        context: Context,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ): CsvDraft.ExpenseDraft? {
        val merchant = cleanName(value("merchant"))
        if (merchant.isEmpty()) errors += "Merchant is required."
        val original = amount(value("original_amount"), "Original amount", errors)
        if (original != null && original.isZero) errors += "Original amount must be more than zero."
        val percentage = value("cashback_percentage").removeSuffix("%").trim().let { raw ->
            if (raw.isEmpty()) {
                Percentage.ZERO
            } else {
                raw.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toInt()?.takeIf { it in 0..10_000 }?.let(::Percentage)
                    ?: Percentage.ZERO.also { errors += "Cashback percentage \"$raw\" must be a number from 0 to 100." }
            }
        }
        val cashback = value("cashback_amount").let { raw -> if (raw.isEmpty()) original?.let(percentage::of) else amount(raw, "Cashback amount", errors) }
        val effective = value("effective_amount").let { raw -> if (raw.isEmpty()) cashback?.let { original?.minus(it) } else amount(raw, "Effective amount", errors) }
        if (original != null && cashback != null && effective != null) {
            if (cashback > original) {
                errors += "Cashback is more than the original amount."
            } else if (cashback + effective != original) {
                errors += "Original − cashback should equal the effective amount."
            } else if (value("cashback_percentage").isNotEmpty() && value("cashback_amount").isNotEmpty() && cashback != percentage.of(original)) {
                errors += "Cashback ${cashback.toPlainString()} should be ${percentage.of(original).toPlainString()} (${percentage.format()} of ${original.toPlainString()})."
            }
        }
        val method = optional("payment_method")?.let { raw ->
            if (raw.isEmpty()) {
                null
            } else {
                PaymentMethod.entries.firstOrNull { it.name.equals(raw, true) || it.label.equals(raw, true) }
                    ?: PaymentMethod.OTHER.also { errors += "Payment method \"$raw\" is not Card, UPI, Cash or Other." }
            }
        }
        if (method == null) warnings += "No payment method; recorded as Other."
        val card = if (optional("card_name") == null && optional("card_last_four") == null) {
            CsvDraft.CardLink.NotGiven
        } else {
            CsvDraft.CardLink.To(findCard(value("card_name"), value("card_last_four"), context, warnings))
        }
        val category = optional("category")?.let { cleanName(it).ifEmpty { "Other" } }
        if (category != null && (nameKey(category) to CategoryKind.EXPENSE) !in context.categories) warnings += "New category \"$category\" will be added."

        val shares = mutableListOf<Pair<String, Money>>()
        value("shared_with").split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            val separator = part.indexOfAny(charArrayOf('=', ':'))
            val name = cleanName(if (separator < 0) part else part.substring(0, separator))
            val share = if (separator < 0) null else amount(part.substring(separator + 1).trim(), "Share for $name", errors)
            when {
                name.isEmpty() -> errors += "A share in shared_with has no name."
                share == null && separator < 0 -> errors += "Share for $name has no amount (write it as $name=180)."
                share != null -> {
                    if (shares.any { nameKey(it.first) == nameKey(name) }) errors += "$name appears twice in shared_with."
                    shares += name to share
                    if (nameKey(name) !in context.peopleByKey) warnings += "New person \"$name\" will be added."
                }
            }
        }
        val sharesTotal = shares.fold(Money.ZERO) { sum, share -> sum + share.second }
        if (original != null && effective != null) {
            if (sharesTotal > original) {
                errors += "Shares add up to ${sharesTotal.toPlainString()}, more than the amount charged."
            } else if (sharesTotal > effective) {
                warnings += "Shares are more than the amount after cashback (the way version 1 recorded them); imported unchanged."
            }
            val myShare = value("my_share")
            if (myShare.isNotEmpty()) {
                val parsed = signedAmount(myShare)
                if (parsed == null) {
                    errors += "my_share \"$myShare\" is not an amount."
                } else if (parsed != effective - sharesTotal) {
                    errors += "my_share ${parsed.toPlainString()} plus the shares should equal the effective amount ${effective.toPlainString()}."
                }
            }
        }
        if (errors.isNotEmpty() || date == null || original == null || cashback == null || effective == null) return null
        return CsvDraft.ExpenseDraft(uuid, date, merchant, category, method, card, original, percentage, cashback, effective, shares, optional("note"))
    }

    private fun ledgerDraft(
        uuid: String?,
        date: LocalDate?,
        value: (String) -> String,
        context: Context,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ): CsvDraft.LedgerDraft? {
        val person = cleanName(value("person"))
        if (person.isEmpty()) errors += "Person is required."
        val rawType = value("type")
        val type = LedgerType.entries.firstOrNull { it.name.equals(rawType.replace(' ', '_'), true) || it.label.equals(rawType, true) }
        when {
            type == null -> errors += "Type \"$rawType\" is not a ledger type (${LedgerType.entries.joinToString { it.name }})."
            type == LedgerType.EXPENSE_SHARE -> errors += "Expense shares are imported with the expenses file (shared_with column), so this row is skipped."
        }
        val rawDirection = value("direction")
        val given = UdhaarDirection.entries.firstOrNull { it.name.equals(rawDirection, true) }
        if (rawDirection.isNotEmpty() && given == null) errors += "Direction \"$rawDirection\" must be GAVE or GOT."
        val fixed = type?.fixedDirection
        val direction = when {
            fixed != null && given != null && given != fixed -> {
                errors += "${type.label} entries must have direction ${fixed.name}."
                fixed
            }
            fixed != null -> fixed
            given != null -> given
            else -> {
                if (type != null) errors += "${type.label} entries need a direction (GAVE or GOT)."
                UdhaarDirection.GAVE
            }
        }
        val amount = amount(value("amount"), "Amount", errors)
        if (amount != null && amount.isZero) errors += "Amount must be more than zero."
        if (person.isNotEmpty() && nameKey(person) !in context.peopleByKey) warnings += "New person \"$person\" will be added."
        if (errors.isNotEmpty() || date == null || type == null || amount == null) return null
        return CsvDraft.LedgerDraft(uuid, date, person, type, direction, amount, value("note"))
    }

    private fun findCard(name: String, lastFour: String, context: Context, warnings: MutableList<String>): String? {
        if (name.isEmpty() && lastFour.isEmpty()) return null
        val cards = context.existing.cards
        val match = cards.firstOrNull { lastFour.isNotEmpty() && it.lastFour == lastFour && (name.isEmpty() || nameKey(it.name) == nameKey(name)) }
            ?: cards.firstOrNull { lastFour.isNotEmpty() && it.lastFour == lastFour }
            ?: cards.firstOrNull { name.isNotEmpty() && nameKey(it.name) == nameKey(name) }
        if (match == null) warnings += "Card \"${listOf(name, lastFour).filter { it.isNotEmpty() }.joinToString(" ")}\" is not in the app; imported without a card."
        return match?.uuid
    }

    private fun findDuplicate(draft: CsvDraft, context: Context): CsvDuplicate? {
        val existing = context.existing
        val id = draft.uuid
        val sameId = id != null && when (draft) {
            is CsvDraft.ExpenseDraft -> existing.expenses.any { it.uuid == id }
            is CsvDraft.IncomeDraft -> existing.incomes.any { it.uuid == id }
            is CsvDraft.PersonDraft -> existing.people.any { it.uuid == id }
            is CsvDraft.LedgerDraft -> existing.ledger.any { it.uuid == id }
        }
        if (sameId) return CsvDuplicate(id!!, "Same ID as an existing record")
        return when (draft) {
            is CsvDraft.ExpenseDraft -> existing.expenses
                .firstOrNull { it.date == draft.date && nameKey(it.merchant) == nameKey(draft.merchant) && it.originalAmount == draft.original }
                ?.let { CsvDuplicate(it.uuid, "Same date, merchant and amount") }
            is CsvDraft.IncomeDraft -> existing.incomes
                .firstOrNull { it.date == draft.date && nameKey(it.source) == nameKey(draft.source) && it.amount == draft.amount }
                ?.let { CsvDuplicate(it.uuid, "Same date, source and amount") }
            is CsvDraft.PersonDraft -> context.peopleByKey[nameKey(draft.name)]?.let { CsvDuplicate(it.uuid, "Same name") }
            is CsvDraft.LedgerDraft -> {
                val person = context.peopleByKey[nameKey(draft.person)] ?: return null
                existing.ledger
                    .firstOrNull { it.personUuid == person.uuid && it.date == draft.date && it.type == draft.type && it.amount == draft.amount }
                    ?.let { CsvDuplicate(it.uuid, "Same person, date, type and amount") }
            }
        }
    }

    private fun summarize(kind: CsvKind, values: Map<String, String>): String {
        fun v(name: String) = values[name].orEmpty().trim()
        return when (kind) {
            CsvKind.EXPENSES -> listOf(v("date"), v("merchant"), v("original_amount")).filter { it.isNotEmpty() }.joinToString(" · ")
            CsvKind.INCOME -> listOf(v("date"), v("source"), v("amount")).filter { it.isNotEmpty() }.joinToString(" · ")
            CsvKind.PEOPLE -> listOf(v("name"), v("phone")).filter { it.isNotEmpty() }.joinToString(" · ")
            CsvKind.UDHAAR, CsvKind.PAYMENTS -> listOf(v("date"), v("person"), v("type"), v("amount")).filter { it.isNotEmpty() }.joinToString(" · ")
        }
    }

    /**
     * Adds the valid rows of [preview] to [existing]. Rows matching an existing
     * record are skipped, update that record, or are added as new records,
     * as [choice] says. The returned data is the app's complete new contents.
     */
    fun apply(preview: CsvPreview, choice: DuplicateChoice, existing: BackupData, now: Long, makeUuid: () -> String = ::newUuid): CsvImportResult {
        val people = existing.people.toMutableList()
        val categories = existing.categories.toMutableList()
        val expenses = existing.expenses.toMutableList()
        val incomes = existing.incomes.toMutableList()
        val ledger = existing.ledger.toMutableList()
        var added = 0
        var updated = 0
        var skipped = 0
        var peopleAdded = 0
        var categoriesAdded = 0
        val usedUuids = (existing.people.map { it.uuid } + existing.expenses.map { it.uuid } + existing.incomes.map { it.uuid } + existing.ledger.map { it.uuid }).toMutableSet()

        fun freshUuid(wanted: String?): String {
            if (wanted != null && wanted !in usedUuids) {
                usedUuids += wanted
                return wanted
            }
            var uuid = makeUuid()
            while (uuid in usedUuids) uuid = makeUuid()
            usedUuids += uuid
            return uuid
        }

        fun personUuid(name: String): String {
            people.firstOrNull { nameKey(it.name) == nameKey(name) }?.let { return it.uuid }
            val person = PersonRecord(uuid = freshUuid(null), name = name, createdAt = now, updatedAt = now)
            people += person
            peopleAdded++
            return person.uuid
        }

        fun ensureCategory(name: String, kind: CategoryKind) {
            if (categories.none { nameKey(it.name) == nameKey(name) && it.kind == kind }) {
                categories += CategoryRecord(freshUuid(null), name, kind, isDefault = false, createdAt = now, updatedAt = now)
                categoriesAdded++
            }
        }

        fun uniqueName(name: String): String {
            if (people.none { nameKey(it.name) == nameKey(name) }) return name
            var n = 2
            while (people.any { nameKey(it.name) == nameKey("$name ($n)") }) n++
            return "$name ($n)"
        }

        preview.valid.forEach { row ->
            val draft = row.draft ?: return@forEach
            val duplicate = row.duplicate
            if (duplicate != null && choice == DuplicateChoice.SKIP) {
                skipped++
                return@forEach
            }
            val updating = duplicate != null && choice == DuplicateChoice.UPDATE
            when (draft) {
                is CsvDraft.ExpenseDraft -> {
                    val current = if (updating) expenses.first { it.uuid == duplicate!!.existingUuid } else null
                    val category = draft.category ?: current?.category ?: "Other"
                    ensureCategory(category, CategoryKind.EXPENSE)
                    val record = ExpenseRecord(
                        uuid = current?.uuid ?: freshUuid(draft.uuid),
                        date = draft.date,
                        merchant = draft.merchant,
                        category = category,
                        paymentMethod = draft.paymentMethod ?: current?.paymentMethod ?: PaymentMethod.OTHER,
                        cardUuid = when (val card = draft.card) {
                            CsvDraft.CardLink.NotGiven -> current?.cardUuid
                            is CsvDraft.CardLink.To -> card.cardUuid
                        },
                        originalAmount = draft.original,
                        cashbackPercentage = draft.percentage,
                        cashbackAmount = draft.cashback,
                        effectiveAmount = draft.effective,
                        note = draft.note ?: current?.note.orEmpty(),
                        recurringUuid = current?.recurringUuid,
                        createdAt = current?.createdAt ?: now,
                        updatedAt = now,
                        id = current?.id ?: 0,
                    )
                    if (current != null) {
                        expenses[expenses.indexOf(current)] = record
                        ledger.removeAll { it.type == LedgerType.EXPENSE_SHARE && it.expenseUuid == record.uuid }
                        updated++
                    } else {
                        expenses += record
                        added++
                    }
                    draft.shares.forEach { (name, amount) ->
                        ledger += LedgerRecord(
                            uuid = freshUuid(null),
                            personUuid = personUuid(name),
                            date = draft.date,
                            type = LedgerType.EXPENSE_SHARE,
                            direction = UdhaarDirection.GAVE,
                            amount = amount,
                            expenseUuid = record.uuid,
                            note = "",
                            createdAt = now,
                            updatedAt = now,
                        )
                    }
                }
                is CsvDraft.IncomeDraft -> {
                    ensureCategory(draft.category, CategoryKind.INCOME)
                    val current = if (updating) incomes.first { it.uuid == duplicate!!.existingUuid } else null
                    val record = IncomeRecord(
                        uuid = current?.uuid ?: freshUuid(draft.uuid),
                        date = draft.date,
                        source = draft.source,
                        category = draft.category,
                        amount = draft.amount,
                        note = draft.note,
                        createdAt = current?.createdAt ?: now,
                        updatedAt = now,
                        id = current?.id ?: 0,
                    )
                    if (current != null) {
                        incomes[incomes.indexOf(current)] = record
                        updated++
                    } else {
                        incomes += record
                        added++
                    }
                }
                is CsvDraft.PersonDraft -> {
                    val current = if (updating) people.first { it.uuid == duplicate!!.existingUuid } else null
                    if (current != null) {
                        // Keep the current spelling when only the case differs, and never take another person's name.
                        val keepName = nameKey(draft.name) == nameKey(current.name) ||
                            people.any { it.uuid != current.uuid && nameKey(it.name) == nameKey(draft.name) }
                        people[people.indexOf(current)] = current.copy(
                            name = if (keepName) current.name else draft.name,
                            phone = draft.phone ?: current.phone,
                            email = draft.email ?: current.email,
                            address = draft.address ?: current.address,
                            notes = draft.notes ?: current.notes,
                            tags = draft.tags ?: current.tags,
                            updatedAt = now,
                        )
                        updated++
                    } else {
                        people += PersonRecord(
                            uuid = freshUuid(draft.uuid),
                            name = uniqueName(draft.name),
                            phone = draft.phone.orEmpty(),
                            email = draft.email.orEmpty(),
                            address = draft.address.orEmpty(),
                            notes = draft.notes.orEmpty(),
                            tags = draft.tags.orEmpty(),
                            createdAt = now,
                            updatedAt = now,
                        )
                        added++
                    }
                }
                is CsvDraft.LedgerDraft -> {
                    val current = if (updating) ledger.first { it.uuid == duplicate!!.existingUuid } else null
                    val record = LedgerRecord(
                        uuid = current?.uuid ?: freshUuid(draft.uuid),
                        personUuid = personUuid(draft.person),
                        date = draft.date,
                        type = draft.type,
                        direction = draft.direction,
                        amount = draft.amount,
                        expenseUuid = null,
                        note = draft.note,
                        createdAt = current?.createdAt ?: now,
                        updatedAt = now,
                        id = current?.id ?: 0,
                    )
                    if (current != null) {
                        ledger[ledger.indexOf(current)] = record
                        updated++
                    } else {
                        ledger += record
                        added++
                    }
                }
            }
        }
        val data = existing.copy(people = people, categories = categories, expenses = expenses, incomes = incomes, ledger = ledger)
        return CsvImportResult(data, added, updated, skipped, peopleAdded, categoriesAdded)
    }

    private val DAY_FIRST = listOf("d/M/uuuu", "d-M-uuuu", "d.M.uuuu").map { DateTimeFormatter.ofPattern(it) }

    /** YYYY-MM-DD, or day-first dates such as 08/10/2026. */
    fun parseDate(raw: String): LocalDate? {
        val text = raw.trim()
        try {
            return LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
        }
        DAY_FIRST.forEach { format ->
            try {
                return LocalDate.parse(text, format)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun amount(raw: String, label: String, errors: MutableList<String>): Money? {
        if (raw.isEmpty()) {
            errors += "$label is required."
            return null
        }
        return Money.parse(raw) ?: run {
            errors += "$label \"$raw\" is not an amount."
            null
        }
    }

    private fun signedAmount(raw: String): Money? {
        val text = raw.trim()
        val negative = text.startsWith("-") || text.startsWith("−")
        val amount = Money.parse(if (negative) text.substring(1) else text) ?: return null
        return if (negative) -amount else amount
    }
}
