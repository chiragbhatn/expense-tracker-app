package io.github.chiragbhatn.expensetracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chiragbhatn.expensetracker.backup.BackupData
import io.github.chiragbhatn.expensetracker.backup.BackupWorkbook
import io.github.chiragbhatn.expensetracker.backup.CheckStep
import io.github.chiragbhatn.expensetracker.backup.CsvKind
import io.github.chiragbhatn.expensetracker.backup.DuplicateChoice
import io.github.chiragbhatn.expensetracker.backup.XlsxReader
import io.github.chiragbhatn.expensetracker.backup.XlsxSheet
import io.github.chiragbhatn.expensetracker.backup.XlsxWriter
import io.github.chiragbhatn.expensetracker.domain.CashbackBreakdown
import io.github.chiragbhatn.expensetracker.domain.ExpenseInput
import io.github.chiragbhatn.expensetracker.domain.Frequency
import io.github.chiragbhatn.expensetracker.domain.IncomeInput
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.Split
import io.github.chiragbhatn.expensetracker.domain.ThemeMode
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private val oldPhone = TestApp("old")
    private val newPhone = TestApp("new")

    @After
    fun tearDown() {
        oldPhone.close()
        newPhone.close()
    }

    /** A little of everything, recorded through the repositories like the app does. */
    private suspend fun TestApp.fill(): Long {
        val today = clock.today()
        val rahul = people.addPerson("Rahul")
        val amit = people.addPerson("Amit")
        val card = cards.save(null, CardInput("Millennia", "HDFC", "1234", Money.rupees(1_00_000), 20, 5, reminderDaysBefore = 3))
        rules.save(null, "Amazon", Percentage(500), enabled = true, cardId = card)
        val amounts = CashbackBreakdown.calculate(Money.rupees(1_000), Percentage(1_000))
        expenses.save(
            null,
            ExpenseInput(amounts.originalAmount, amounts.cashbackPercentage, "Swiggy", PaymentMethod.CARD, Split.equal(amounts.effectiveAmount, true, listOf(rahul, amit)), today, "Team dinner", "Food", card),
        )
        expenses.save(null, ExpenseInput(Money(1_234_56), Percentage.ZERO, "DMart", PaymentMethod.UPI, Split.mine(Money(1_234_56)), today.minusDays(3), "", "Groceries"))
        expenses.saveIncome(null, IncomeInput(Money.rupees(60_000), "Employer", "Salary", today.withDayOfMonth(1), ""))
        people.settle(rahul, Money.rupees(300), today, "UPI")
        people.addEntry(amit, LedgerType.UDHAAR_TAKEN, UdhaarDirection.GOT, Money.rupees(500), today, "Cab")
        people.addEntry(amit, LedgerType.PAYMENT_MADE, UdhaarDirection.GAVE, Money.rupees(200), today, "")
        cards.addPayment(card, Money.rupees(400), today, "")
        recurring.save(null, RecurringInput("Netflix", Money.rupees(649), "Subscription", PaymentMethod.CARD, card, today.plusDays(5), Frequency.MONTHLY))
        people.addReminder("Ask Amit", "", today.plusDays(2), amit)
        settings.update { it.copy(themeMode = ThemeMode.DARK, appLock = true) }
        return rahul
    }

    private suspend fun exportFrom(app: TestApp): ByteArray = ByteArrayOutputStream().also { app.backup.exportWorkbook(it) }.toByteArray()

    /** What a backup holds, without this device's row ids or timestamps it does not control. */
    private fun BackupData.comparable() = copy(
        settings = emptyMap(),
        people = people.map { it.copy(id = 0) }.sortedBy { it.uuid },
        categories = categories.map { it.copy(id = 0) }.sortedBy { it.uuid },
        cards = cards.map { it.copy(id = 0) }.sortedBy { it.uuid },
        cashbackRules = cashbackRules.map { it.copy(id = 0) }.sortedBy { it.uuid },
        expenses = expenses.map { it.copy(id = 0) }.sortedBy { it.uuid },
        incomes = incomes.map { it.copy(id = 0) }.sortedBy { it.uuid },
        ledger = ledger.map { it.copy(id = 0) }.sortedBy { it.uuid },
        cardPayments = cardPayments.map { it.copy(id = 0) }.sortedBy { it.uuid },
        recurring = recurring.map { it.copy(id = 0) }.sortedBy { it.uuid },
        reminders = reminders.map { it.copy(id = 0) }.sortedBy { it.uuid },
        attachments = attachments.map { it.copy(id = 0) }.sortedBy { it.uuid },
    )

    @Test
    fun fullBackupRestoresEverythingOnANewPhone() = runBlocking {
        oldPhone.fill()
        val bytes = exportFrom(oldPhone)

        val checked = newPhone.backup.readWorkbook(bytes.inputStream(), newPhone.backup.backupFileName())
        assertTrue(checked.errors.toString(), checked.isValid)
        assertEquals(BackupWorkbook.VERSION, checked.metadata?.formatVersion)
        assertFalse(newPhone.backup.snapshot().hasUserData)

        val summary = newPhone.backup.restore(checked.data!!, RestoreMode.REPLACE)

        assertNull(summary.safetyBackup)
        assertEquals(oldPhone.backup.snapshot().comparable(), newPhone.backup.snapshot().comparable())
        // Balances, card outstanding and totals come out the same.
        val before = oldPhone.data()
        val after = newPhone.data()
        assertEquals(before.balances.map { it.person.name to it.summary }, after.balances.map { it.person.name to it.summary })
        assertEquals(before.cardSummaries(oldPhone.clock.today()).map { it.outstanding }, after.cardSummaries(newPhone.clock.today()).map { it.outstanding })
        // Settings come along, except the app lock, which belongs to the device.
        val restored = newPhone.settings.current()
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertFalse(restored.appLock)
    }

    @Test
    fun restoringTwiceGivesTheSameResult() = runBlocking {
        oldPhone.fill()
        val bytes = exportFrom(oldPhone)
        val data = newPhone.backup.readWorkbook(bytes.inputStream(), "backup.xlsx").data!!

        newPhone.backup.restore(data, RestoreMode.REPLACE)
        val first = newPhone.backup.snapshot().comparable()
        val replaced = newPhone.backup.restore(data, RestoreMode.REPLACE)

        assertNotNull("Existing data is saved before it is replaced", replaced.safetyBackup)
        assertTrue(replaced.safetyBackup!!.exists())
        assertEquals(first, newPhone.backup.snapshot().comparable())
    }

    @Test
    fun mergeAddsTheBackupWithoutDuplicatingPeople() = runBlocking {
        oldPhone.fill()
        val bytes = exportFrom(oldPhone)
        // The new phone already has its own Rahul and an expense of its own.
        val rahulHere = newPhone.people.addPerson("rahul")
        newPhone.expenses.save(null, ExpenseInput(Money.rupees(150), Percentage.ZERO, "Chai Point", PaymentMethod.CASH, Split.forPerson(rahulHere, Money.rupees(150)), newPhone.clock.today(), ""))
        val data = newPhone.backup.readWorkbook(bytes.inputStream(), "backup.xlsx").data!!

        val preview = newPhone.backup.previewMerge(data)
        assertEquals(1, preview.stats.getValue(BackupWorkbook.PEOPLE).added)
        val summary = newPhone.backup.restore(data, RestoreMode.MERGE)

        assertNotNull(summary.safetyBackup)
        val merged = newPhone.data()
        assertEquals(listOf("amit", "rahul"), merged.people.map { it.name.lowercase() }.sorted())
        assertEquals(3, merged.expenses.size)
        val rahul = merged.balances.single { it.person.name.equals("rahul", ignoreCase = true) }
        // ₹150 here + ₹300 share from the backup − ₹300 settled.
        assertEquals(Money.rupees(150), rahul.summary.receivable)

        // Merging the same backup again adds nothing.
        val again = newPhone.backup.restore(newPhone.backup.readWorkbook(bytes.inputStream(), "backup.xlsx").data!!, RestoreMode.MERGE)
        assertEquals(0, again.merge!!.added)
        assertEquals(3, newPhone.data().expenses.size)
    }

    @Test
    fun aBackupFromANewerAppIsRefused() = runBlocking {
        oldPhone.fill()
        val sheets = XlsxReader.read(exportFrom(oldPhone)).sheets.map { (name, rows) ->
            val edited = if (name == BackupWorkbook.METADATA) rows.map { row -> if (row.firstOrNull() == "backup_format_version") listOf(row[0], "3.0") else row } else rows
            XlsxSheet(name, edited.first(), edited.drop(1).map { row -> row.map { io.github.chiragbhatn.expensetracker.backup.XlsxCell.Text(it) } })
        }
        val result = newPhone.backup.readWorkbook(XlsxWriter.toBytes(sheets).inputStream(), "future.xlsx")

        assertFalse(result.isValid)
        assertEquals(CheckStep.VERSION, result.errors.single().step)
        assertNull(result.data)
        assertFalse(newPhone.backup.snapshot().hasUserData)
    }

    @Test
    fun csvFilesMoveEverydayRecordsBetweenPhones() = runBlocking {
        oldPhone.fill()
        for (kind in listOf(CsvKind.PEOPLE, CsvKind.EXPENSES, CsvKind.INCOME, CsvKind.UDHAAR, CsvKind.PAYMENTS)) {
            val text = oldPhone.backup.exportCsv(kind)
            val preview = newPhone.backup.previewCsv(kind, text)
            assertTrue("$kind: ${preview.fileErrors}", preview.fileErrors.isEmpty())
            newPhone.backup.importCsv(preview, DuplicateChoice.SKIP)
        }

        val before = oldPhone.data()
        val after = newPhone.data()
        assertEquals(before.expenses.map { it.uuid }.toSet(), after.expenses.map { it.uuid }.toSet())
        assertEquals(before.balances.map { it.person.name to it.summary.balance }, after.balances.map { it.person.name to it.summary.balance })
        assertEquals(before.incomes.map { it.amount }, after.incomes.map { it.amount })

        // The same expenses file again: every row is recognised as a duplicate.
        val again = newPhone.backup.previewCsv(CsvKind.EXPENSES, oldPhone.backup.exportCsv(CsvKind.EXPENSES))
        assertEquals(again.valid.size, again.duplicates.size)
        val result = newPhone.backup.importCsv(again, DuplicateChoice.SKIP)
        assertEquals(0, result.added)
        assertEquals(before.expenses.size, newPhone.data().expenses.size)
    }

    @Test
    fun safetyBackupsAreKeptToTheMostRecentFive() = runBlocking {
        oldPhone.fill()
        repeat(7) { oldPhone.backup.saveSafetyBackup("test") }

        assertEquals(5, oldPhone.backup.safetyBackups().size)
        assertTrue(oldPhone.backup.safetyBackups().all { it.length() > 0 })
        assertEquals(LocalDate.of(2026, 10, 8), oldPhone.clock.today())
    }
}
