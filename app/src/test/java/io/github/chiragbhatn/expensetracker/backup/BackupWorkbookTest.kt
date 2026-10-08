package io.github.chiragbhatn.expensetracker.backup

import io.github.chiragbhatn.expensetracker.backup.BackupFixtures.normalized
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate

class BackupWorkbookTest {

    private val app = AppInfo("2.0.42", 42)
    private val exportedAt = Instant.parse("2026-10-08T09:30:00Z")

    private fun export(data: BackupData = BackupFixtures.data): ByteArray =
        ByteArrayOutputStream().also { BackupWorkbook.write(data, app, "INR", exportedAt, LocalDate.of(2026, 10, 8), it) }.toByteArray()

    /** Reads a workbook, lets [edit] change its cells, and writes it back as plain text cells. */
    private fun edited(bytes: ByteArray = export(), edit: (LinkedHashMap<String, MutableList<MutableList<String>>>) -> Unit): ByteArray {
        val sheets = LinkedHashMap<String, MutableList<MutableList<String>>>()
        XlsxReader.read(bytes).sheets.forEach { (name, rows) -> sheets[name] = rows.map { it.toMutableList() }.toMutableList() }
        edit(sheets)
        return XlsxWriter.toBytes(
            sheets.map { (name, rows) -> XlsxSheet(name, rows.first(), rows.drop(1).map { row -> row.map { XlsxCell.Text(it) } }) },
        )
    }

    private fun MutableList<MutableList<String>>.set(row: Int, column: String, value: String) {
        val index = first().indexOf(column)
        while (this[row].size <= index) this[row].add("")
        this[row][index] = value
    }

    private fun MutableList<MutableList<String>>.rowWith(id: String) = indexOfFirst { it.firstOrNull() == id }

    @Test
    fun `a full backup restores exactly what was exported`() {
        val result = BackupWorkbook.read(export(), "expense-tracker-backup.xlsx")

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(emptyList<Issue>(), result.warnings.filter { it.step != CheckStep.AMOUNTS })
        val expected = BackupFixtures.data.copy(settings = BackupFixtures.data.settings - "app_lock")
        assertEquals(expected.normalized(), result.data!!.normalized())
    }

    @Test
    fun `metadata records the format, app and schema versions`() {
        val metadata = BackupWorkbook.read(export()).metadata!!

        assertEquals("2.0", metadata.formatVersion)
        assertEquals("2.0.42", metadata.appVersion)
        assertEquals(2, metadata.schemaVersion)
        assertEquals(LocalDate.of(2026, 10, 8), metadata.exportDate)
        assertEquals("INR", metadata.currency)
        assertEquals(5, metadata.counts[BackupWorkbook.EXPENSES])
        assertEquals(3, metadata.counts[BackupWorkbook.PEOPLE])
        assertEquals(6, metadata.counts[BackupWorkbook.LEDGER])
        assertEquals(2, metadata.counts[BackupWorkbook.PAYMENTS])
        assertEquals(1, metadata.counts[BackupWorkbook.SETTLEMENTS])
    }

    @Test
    fun `sheets are laid out for people to read too`() {
        val sheets = XlsxReader.read(export()).sheets

        assertEquals(
            listOf(
                "Metadata", "Settings", "People", "Expenses", "Income", "LedgerTransactions", "Payments", "Settlements",
                "CreditCards", "CardPayments", "CashbackRules", "RecurringExpenses", "Categories", "Reminders", "Attachments",
            ),
            sheets.keys.toList(),
        )
        val expenses = sheets.getValue("Expenses")
        assertEquals(BackupWorkbook.EXPENSE_COLUMNS, expenses.first())
        val swiggy = expenses.first { it[0] == "e-1" }
        assertEquals("2026-10-03", swiggy[1])
        assertEquals("200.00", swiggy[expenses.first().indexOf("original_amount")])
        assertEquals("20000", swiggy[expenses.first().indexOf("original_amount_paise")])
        assertEquals("2000", swiggy[expenses.first().indexOf("cashback_amount_paise")])
        assertEquals("18000", swiggy[expenses.first().indexOf("effective_amount_paise")])
        assertEquals("Rahul", sheets.getValue("LedgerTransactions").first { it[0] == "l-1" }[2])
    }

    @Test
    fun `not an xlsx file`() {
        val result = BackupWorkbook.read("id,name\n1,Rahul\n".toByteArray(), "people.csv")

        assertFalse(result.isValid)
        assertEquals(CheckStep.FILE_TYPE, result.errors.single().step)
        assertNull(result.data)
    }

    @Test
    fun `a spreadsheet that is not a backup`() {
        val other = XlsxWriter.toBytes(listOf(XlsxSheet("Budget", listOf("month", "amount"), emptyList())))

        val result = BackupWorkbook.read(other, "budget.xlsx")

        assertEquals(listOf(CheckStep.VERSION), result.errors.map { it.step })
        assertTrue(result.errors.single().message.contains("not an Expense Tracker backup"))
    }

    @Test
    fun `backups from a newer major version are refused without changing anything`() {
        val newer = edited { sheets -> sheets.getValue("Metadata").let { it.set(it.rowWith("backup_format_version"), "value", "3.0") } }

        val result = BackupWorkbook.read(newer)

        assertFalse(result.isValid)
        assertEquals(CheckStep.VERSION, result.errors.single().step)
        assertTrue(result.errors.single().message.contains("newer version of Expense Tracker"))
        assertEquals("3.0", result.metadata?.formatVersion)
    }

    @Test
    fun `backups from a newer minor version restore with a warning`() {
        val newer = edited { sheets ->
            sheets.getValue("Metadata").let { it.set(it.rowWith("backup_format_version"), "value", "2.1") }
            sheets.getValue("People").let { people ->
                people[0].add("favourite_colour")
                people.drop(1).forEach { it.add("teal") }
            }
        }

        val result = BackupWorkbook.read(newer)

        assertTrue(result.errors.toString(), result.isValid)
        assertTrue(result.warnings.any { it.step == CheckStep.VERSION && it.message.contains("2.1") })
        assertEquals(3, result.data!!.people.size)
    }

    @Test
    fun `missing sheets and columns are reported`() {
        val broken = edited { sheets ->
            sheets.remove("CreditCards")
            sheets.getValue("Expenses")[0][1] = "when"
        }

        val result = BackupWorkbook.read(broken)

        assertEquals(listOf("The CreditCards sheet is missing."), result.errors.map { it.message })
        val withColumnMissing = BackupWorkbook.read(edited { sheets -> sheets.getValue("Expenses")[0][1] = "when" })
        assertEquals(CheckStep.SHEETS, withColumnMissing.errors.single().step)
        assertTrue(withColumnMissing.errors.single().message.contains("date"))
    }

    @Test
    fun `row problems are reported with their sheet and row`() {
        val broken = edited { sheets ->
            val expenses = sheets.getValue("Expenses")
            expenses.set(expenses.rowWith("e-1"), "date", "31/02/2026")
            expenses.set(expenses.rowWith("e-2"), "effective_amount_paise", "1")
            val people = sheets.getValue("People")
            people.set(people.rowWith("p-amit"), "id", "p-rahul")
            val income = sheets.getValue("Income")
            income.set(income.rowWith("i-1"), "amount_paise", "-5")
        }

        val result = BackupWorkbook.read(broken)

        assertFalse(result.isValid)
        val steps = result.errors.map { it.step }.toSet()
        assertEquals(setOf(CheckStep.DATES, CheckStep.AMOUNTS), steps)
        val date = result.errors.first { it.step == CheckStep.DATES }
        assertEquals("Expenses", date.sheet)
        assertTrue(date.toString(), date.toString().startsWith("Expenses row "))
        // Duplicate IDs are checked once every row parses.
        val duplicates = BackupWorkbook.read(edited { sheets -> sheets.getValue("People").let { it.set(it.rowWith("p-amit"), "id", "p-rahul") } })
        assertEquals(CheckStep.IDS, duplicates.errors.first().step)
    }

    @Test
    fun `relationships must point at records in the backup`() {
        val broken = edited { sheets ->
            val ledger = sheets.getValue("Payments")
            ledger.set(ledger.rowWith("l-5"), "person_id", "p-nobody")
        }

        val result = BackupWorkbook.read(broken)

        assertEquals(CheckStep.RELATIONSHIPS, result.errors.single().step)
    }

    @Test
    fun `optional links to missing records are dropped with a warning`() {
        val fixed = edited { sheets ->
            val expenses = sheets.getValue("Expenses")
            expenses.set(expenses.rowWith("e-5"), "card_id", "c-gone")
            expenses.set(expenses.rowWith("e-5"), "category", "Streaming")
            sheets.getValue("Settings").removeAt(1)
        }

        val result = BackupWorkbook.read(fixed)

        assertTrue(result.errors.toString(), result.isValid)
        val restored = result.data!!
        assertNull(restored.expenses.first { it.uuid == "e-5" }.cardUuid)
        assertTrue(restored.categories.any { it.name == "Streaming" })
        assertTrue(result.warnings.any { it.message.contains("cards not in the backup") })
        assertTrue(result.warnings.any { it.message.contains("Streaming") })
    }

    @Test
    fun `duplicate people, categories and shares are refused`() {
        val twins = edited { sheets -> sheets.getValue("People").let { it.set(it.rowWith("p-amit"), "name", "rahul") } }
        assertEquals(CheckStep.DUPLICATES, BackupWorkbook.read(twins).errors.single().step)

        val doubleShare = edited { sheets ->
            sheets.getValue("LedgerTransactions").let { it.set(it.rowWith("l-3"), "person_id", "p-rahul") }
        }
        assertTrue(BackupWorkbook.read(doubleShare).errors.any { it.step == CheckStep.DUPLICATES })
    }

    @Test
    fun `version 1 shares are restored unchanged with a note`() {
        val result = BackupWorkbook.read(export())

        val legacy = result.data!!.ledger.single { it.uuid == "l-4" }
        assertEquals(Money.rupees(1_000), legacy.amount)
        assertTrue(result.warnings.any { it.step == CheckStep.AMOUNTS && it.message.contains("before version 2") })
    }

    @Test
    fun `values edited in a spreadsheet are still understood`() {
        val edited = edited { sheets ->
            val expenses = sheets.getValue("Expenses")
            val row = expenses.rowWith("e-3")
            // Excel turned the date into a day number and the paise cell was cleared.
            expenses.set(row, "date", "46301")
            expenses.set(row, "original_amount_paise", "")
            expenses.set(row, "original_amount", "1234.56")
            expenses.set(row, "cashback_amount_paise", "")
            expenses.set(row, "effective_amount_paise", "123456.0")
            sheets.getValue("CreditCards").let { it.set(it.rowWith("c-sbi"), "last_four", "42") }
            sheets.getValue("Payments").let { it.set(it.rowWith("l-5"), "amount", "299.99") }
        }

        val result = BackupWorkbook.read(edited)

        assertTrue(result.errors.toString(), result.isValid)
        val dmart = result.data!!.expenses.single { it.uuid == "e-3" }
        assertEquals(LocalDate.of(2026, 10, 6), dmart.date)
        assertEquals(Money(1_234_56), dmart.originalAmount)
        assertEquals("0042", result.data!!.cards.single { it.uuid == "c-sbi" }.lastFour)
        assertEquals(Money.rupees(300), result.data!!.ledger.single { it.uuid == "l-5" }.amount)
        assertTrue(result.warnings.any { it.message.contains("amount_paise (300.00) is used") })
    }

    @Test
    fun `row counts that differ from the metadata are flagged`() {
        val trimmed = edited { sheets -> sheets.getValue("Income").removeAt(2) }

        val result = BackupWorkbook.read(trimmed)

        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.message.contains("Income sheet has 1 rows but the backup recorded 2") })
    }

    @Test
    fun `share entries of missing expenses keep the balance as udhaar`() {
        val orphan = edited { sheets ->
            val expenses = sheets.getValue("Expenses")
            expenses.removeAt(expenses.rowWith("e-1"))
        }

        val result = BackupWorkbook.read(orphan)

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(LedgerType.UDHAAR_GIVEN, result.data!!.ledger.single { it.uuid == "l-1" }.type)
    }

    @Test
    fun `writes a sample workbook for spreadsheet apps to open`() {
        val dir = System.getProperty("fixtures.dir").orEmpty()
        if (dir.isEmpty()) return
        File(dir).mkdirs()
        File(dir, "sample-backup.xlsx").writeBytes(export())
        File(dir, "expenses.csv").writeText(Csv.BOM + CsvTransfer.export(CsvKind.EXPENSES, BackupFixtures.data))
    }

    @Test
    fun `reads a workbook re-saved by another spreadsheet app`() {
        // The sample backup opened and saved again with openpyxl ("Microsoft Excel Compatible"),
        // which rewrites the cells, styles and workbook parts its own way.
        val bytes = checkNotNull(javaClass.getResourceAsStream("/backup/resaved-backup.xlsx")).use { it.readBytes() }

        val result = BackupWorkbook.read(bytes, "resaved-backup.xlsx")

        assertTrue(result.errors.toString(), result.isValid)
        val expected = BackupFixtures.data.copy(settings = BackupFixtures.data.settings - "app_lock")
        assertEquals(expected.normalized(), result.data!!.normalized())
    }
}
