package io.github.chiragbhatn.expensetracker.backup

import io.github.chiragbhatn.expensetracker.backup.BackupFixtures.T0
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvTransferTest {

    private val source = BackupFixtures.data
    private val now = T0 + 1_000
    private var counter = 0
    private val uuids = { "new-${++counter}" }

    /** A fresh install that already has the same cards and categories. */
    private val fresh = BackupData(categories = source.categories, cards = source.cards)

    private fun import(kind: CsvKind, into: BackupData, choice: DuplicateChoice = DuplicateChoice.SKIP, from: BackupData = source): CsvImportResult {
        val preview = CsvTransfer.preview(kind, Csv.BOM + CsvTransfer.export(kind, from), into)
        assertEquals(emptyList<String>(), preview.fileErrors)
        return CsvTransfer.apply(preview, choice, into, now, uuids)
    }

    @Test
    fun `expenses export with the cashback breakdown and shares`() {
        val rows = Csv.parse(CsvTransfer.export(CsvKind.EXPENSES, source))

        assertEquals(CsvTransfer.EXPENSE_COLUMNS, rows.first())
        val split = rows.single { it[0] == "e-2" }.let { row -> CsvTransfer.EXPENSE_COLUMNS.zip(row).toMap() }
        assertEquals("1000.00", split["original_amount"])
        assertEquals("10", split["cashback_percentage"])
        assertEquals("100.00", split["cashback_amount"])
        assertEquals("900.00", split["effective_amount"])
        assertEquals("300.00", split["my_share"])
        assertEquals("Rahul=300.00; Amit=300.00", split["shared_with"])
        assertEquals("Millennia", split["card_name"])
        assertEquals("1234", split["card_last_four"])
        assertEquals("'=SUM(A1)", rows.single { it[0] == "e-3" }.last())
    }

    @Test
    fun `people export with their balances`() {
        val rows = Csv.parse(CsvTransfer.export(CsvKind.PEOPLE, source)).drop(1).associateBy { it[1] }

        // Rahul: 180 + 300 + 20 charged, 300 paid.
        assertEquals("200.00", rows.getValue("Rahul")[7])
        assertEquals("200.00", rows.getValue("Rahul")[8])
        // Neha: owes 1,000 (version 1 share) and lent you 500, of which you repaid 200.
        assertEquals("700.00", rows.getValue("Neha")[7])
        assertEquals("college, flatmate", rows.getValue("Rahul")[6])
    }

    @Test
    fun `everything exported as CSV can be imported into a new install`() {
        var data = fresh
        data = import(CsvKind.PEOPLE, data).data
        data = import(CsvKind.EXPENSES, data).data
        data = import(CsvKind.INCOME, data).data
        val udhaar = import(CsvKind.UDHAAR, data)
        data = udhaar.data
        data = import(CsvKind.PAYMENTS, data).data

        assertEquals(source.people.map { it.copy(createdAt = now, updatedAt = now) }.sortedBy { it.uuid }, data.people.sortedBy { it.uuid })
        assertEquals(
            source.expenses.map { it.copy(createdAt = 0, updatedAt = 0, recurringUuid = null) }.sortedBy { it.uuid },
            data.expenses.map { it.copy(createdAt = 0, updatedAt = 0) }.sortedBy { it.uuid },
        )
        assertEquals(source.incomes.map { it.copy(createdAt = 0, updatedAt = 0) }, data.incomes.map { it.copy(createdAt = 0, updatedAt = 0) })

        fun ledgerKey(l: LedgerRecord) = listOf(l.personUuid, l.date, l.type, l.direction, l.amount, l.expenseUuid, l.note)
        assertEquals(source.ledger.map(::ledgerKey).toSet(), data.ledger.map(::ledgerKey).toSet())
        // Expense shares come in with the expenses, so the udhaar file skips them.
        assertEquals(2, udhaar.added)
    }

    @Test
    fun `importing the same file again finds every row as a duplicate`() {
        val once = import(CsvKind.EXPENSES, import(CsvKind.PEOPLE, fresh).data).data
        val preview = CsvTransfer.preview(CsvKind.EXPENSES, CsvTransfer.export(CsvKind.EXPENSES, source), once)

        assertEquals(5, preview.duplicates.size)
        assertTrue(preview.duplicates.all { it.duplicate!!.reason == "Same ID as an existing record" })

        val skipped = CsvTransfer.apply(preview, DuplicateChoice.SKIP, once, now, uuids)
        assertEquals(0, skipped.added)
        assertEquals(5, skipped.skipped)
        assertEquals(once, skipped.data)

        val asNew = CsvTransfer.apply(preview, DuplicateChoice.IMPORT_AS_NEW, once, now, uuids)
        assertEquals(5, asNew.added)
        assertEquals(10, asNew.data.expenses.size)
        assertEquals(10, asNew.data.expenses.map { it.uuid }.toSet().size)
    }

    @Test
    fun `update overwrites the matching record and its shares`() {
        val once = import(CsvKind.EXPENSES, import(CsvKind.PEOPLE, fresh).data).data
        val csv = """
            id,date,merchant,category,payment_method,original_amount,cashback_percentage,shared_with,note
            e-2,2026-10-05,Swiggy,Food,CARD,1000,10,Rahul=450; Amit=450,corrected
        """.trimIndent()

        val result = CsvTransfer.apply(CsvTransfer.preview(CsvKind.EXPENSES, csv, once), DuplicateChoice.UPDATE, once, now, uuids)

        assertEquals(1, result.updated)
        val expense = result.data.expenses.single { it.uuid == "e-2" }
        assertEquals("corrected", expense.note)
        assertEquals(once.expenses.single { it.uuid == "e-2" }.createdAt, expense.createdAt)
        assertEquals(listOf(Money.rupees(450), Money.rupees(450)), result.data.ledger.filter { it.expenseUuid == "e-2" }.map { it.amount })
    }

    @Test
    fun `rows without an id are matched on their content`() {
        val once = import(CsvKind.EXPENSES, import(CsvKind.PEOPLE, fresh).data).data
        val csv = "date,merchant,original_amount,payment_method\n2026-10-06,dmart,1234.56,UPI\n2026-10-06,DMart,99,UPI\n"

        val preview = CsvTransfer.preview(CsvKind.EXPENSES, csv, once)

        assertEquals("Same date, merchant and amount", preview.rows[0].duplicate?.reason)
        assertEquals("e-3", preview.rows[0].duplicate?.existingUuid)
        assertNull(preview.rows[1].duplicate)
    }

    @Test
    fun `invalid rows are listed with their errors and the rest can still be imported`() {
        val csv = """
            date,merchant,payment_method,original_amount,cashback_percentage,cashback_amount,effective_amount,shared_with
            2026-10-01,Swiggy,CARD,200,10,,,Rahul=180
            2026-13-01,Swiggy,CARD,200,10,,,
            2026-10-02,Zomato,Bitcoin,200,,,,
            2026-10-03,Amazon,CARD,500,5,25,470,
            2026-10-04,Swiggy,CARD,200,10,,,Rahul=150; Amit=100
            2026-10-05,,UPI,-20,,,,
            08/10/2026,Uber,UPI,"1,250.50",,,,Vikas=250.50
        """.trimIndent()

        val preview = CsvTransfer.preview(CsvKind.EXPENSES, csv, fresh)

        assertEquals(listOf(2, 8), preview.valid.map { it.line })
        assertEquals(listOf(3, 4, 5, 6, 7), preview.invalid.map { it.line })
        assertTrue(preview.invalid[0].errors.single().contains("not a date"))
        assertTrue(preview.invalid[1].errors.single().contains("Bitcoin"))
        assertTrue(preview.invalid[2].errors.single().contains("effective"))
        assertTrue(preview.invalid[3].errors.single().contains("more than the amount charged"))
        assertEquals(2, preview.invalid[4].errors.size)
        assertTrue(preview.valid[0].warnings.any { it.contains("New person \"Rahul\"") })

        val result = CsvTransfer.apply(preview, DuplicateChoice.SKIP, fresh, now, uuids)
        assertEquals(2, result.added)
        assertEquals(2, result.peopleAdded)
        val uber = result.data.expenses.single { it.merchant == "Uber" }
        assertEquals(LocalDate.of(2026, 10, 8), uber.date)
        assertEquals(Money(1_250_50), uber.originalAmount)
        assertEquals(PaymentMethod.UPI, uber.paymentMethod)
        val swiggy = result.data.expenses.single { it.merchant == "Swiggy" }
        assertEquals(Percentage(1_000), swiggy.cashbackPercentage)
        assertEquals(Money.rupees(180), swiggy.effectiveAmount)
    }

    @Test
    fun `a file of the wrong kind is refused`() {
        val preview = CsvTransfer.preview(CsvKind.EXPENSES, CsvTransfer.export(CsvKind.PEOPLE, source), fresh)

        assertFalse(preview.canImport)
        assertTrue(preview.fileErrors.single().contains("missing columns date, merchant, original_amount"))
    }

    @Test
    fun `people with the same name are updated or added under a new name`() {
        val withPeople = import(CsvKind.PEOPLE, fresh).data
        val csv = "name,phone\nrahul,11111 22222\n"

        val preview = CsvTransfer.preview(CsvKind.PEOPLE, csv, withPeople)
        assertEquals("Same name", preview.duplicates.single().duplicate?.reason)

        val updated = CsvTransfer.apply(preview, DuplicateChoice.UPDATE, withPeople, now, uuids).data
        assertEquals("11111 22222", updated.people.single { it.uuid == "p-rahul" }.phone)
        assertEquals("Rahul", updated.people.single { it.uuid == "p-rahul" }.name)

        val asNew = CsvTransfer.apply(preview, DuplicateChoice.IMPORT_AS_NEW, withPeople, now, uuids).data
        assertTrue(asNew.people.any { it.name == "rahul (2)" })
    }

    @Test
    fun `ledger rows check their type and direction`() {
        val csv = """
            date,person,type,direction,amount,note
            2026-10-01,Rahul,Payment received,,500,cash
            2026-10-02,Rahul,PAYMENT_RECEIVED,GAVE,500,
            2026-10-03,Rahul,ADJUSTMENT,,10,
            2026-10-04,Rahul,EXPENSE_SHARE,GAVE,10,
            2026-10-05,Rahul,SETTLEMENT,GAVE,10,
        """.trimIndent()

        val preview = CsvTransfer.preview(CsvKind.PAYMENTS, csv, fresh)

        assertEquals(listOf(2, 6), preview.valid.map { it.line })
        assertTrue(preview.invalid[0].errors.single().contains("must have direction GOT"))
        assertTrue(preview.invalid[1].errors.single().contains("need a direction"))
        assertTrue(preview.invalid[2].errors.single().contains("expenses file"))
        val result = CsvTransfer.apply(preview, DuplicateChoice.SKIP, fresh, now, uuids)
        assertEquals(listOf(LedgerType.PAYMENT_RECEIVED, LedgerType.SETTLEMENT), result.data.ledger.map { it.type })
        assertEquals(1, result.peopleAdded)
    }
}
