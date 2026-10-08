package io.github.chiragbhatn.expensetracker.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.MIGRATION_1_2
import io.github.chiragbhatn.expensetracker.domain.DefaultCategories
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.LegacyShares
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Upgrading from version 1 keeps every expense, person and ledger entry with
 * its id, amounts, dates and notes, and the result matches the version 2
 * schema exactly (Room validates it).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val name = "migration-test.db"
    private val day = LocalDate.of(2026, 9, 10).toEpochDay()

    private fun createVersion1() {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO cashback_rules (id, merchant, merchant_key, cashback_percentage_bps, enabled) VALUES " +
                    "(1, 'Swiggy', 'swiggy', 1000, 1), (2, 'Zomato', 'zomato', 1000, 0), (3, 'Amazon', 'amazon', 500, 0)",
            )
            db.execSQL("INSERT INTO people (id, name, name_key) VALUES (1, 'Rahul', 'rahul'), (2, 'Amit', 'amit'), (3, 'Neha', 'neha')")
            db.execSQL(
                "INSERT INTO expenses (id, original_amount_paise, cashback_percentage_bps, cashback_amount_paise, effective_amount_paise, " +
                    "merchant, payment_method, date_epoch_day, note) VALUES " +
                    "(10, 100000, 1000, 10000, 90000, 'Swiggy', 'CARD', $day, 'Team dinner'), " +
                    "(11, 25000, 0, 0, 25000, 'DMart', 'UPI', ${day + 1}, '')",
            )
            db.execSQL(
                "INSERT INTO udhaar_entries (id, person_id, direction, amount_paise, date_epoch_day, note, expense_id) VALUES " +
                    // Rahul: version 1 charged him the full ₹1,000 of the Swiggy order, then he paid ₹400.
                    "(100, 1, 'GAVE', 100000, $day, 'Team dinner', 10), " +
                    "(101, 1, 'GOT', 40000, ${day + 2}, 'UPI', NULL), " +
                    // Amit lent you ₹250 when he owed nothing, then you lent him ₹50.
                    "(102, 2, 'GOT', 25000, ${day + 3}, '', NULL), " +
                    "(103, 2, 'GAVE', 5000, ${day + 4}, '', NULL), " +
                    "(104, 3, 'GAVE', 3000, ${day + 5}, 'Auto fare', NULL)",
            )
        }
    }

    @Test
    fun upgradeValidatesAgainstTheVersion2Schema() {
        createVersion1()

        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT id, type, uuid, created_at FROM udhaar_entries ORDER BY id").use { cursor ->
                val types = mutableListOf<String>()
                val uuids = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    types += cursor.getString(1)
                    uuids += cursor.getString(2)
                }
                assertEquals(listOf("EXPENSE_SHARE", "PAYMENT_RECEIVED", "UDHAAR_TAKEN", "UDHAAR_GIVEN", "UDHAAR_GIVEN"), types)
                assertEquals(5, uuids.size)
                assertTrue(uuids.all { Regex("""[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}""").matches(it) })
            }
            db.query("SELECT COUNT(*) FROM categories").use { cursor ->
                cursor.moveToFirst()
                assertEquals(DefaultCategories.expense.size + DefaultCategories.income.size, cursor.getInt(0))
            }
        }
    }

    @Test
    fun version1DataIsPreserved() = runBlocking {
        createVersion1()
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()

        val database = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val data = database.observeAppData().first()

            val swiggy = data.expensesById.getValue(10)
            assertEquals(Money.rupees(1_000), swiggy.amounts.originalAmount)
            assertEquals(Percentage(1_000), swiggy.amounts.cashbackPercentage)
            assertEquals(Money.rupees(100), swiggy.amounts.cashbackAmount)
            assertEquals(Money.rupees(900), swiggy.amounts.effectiveAmount)
            assertEquals(LocalDate.of(2026, 9, 10), swiggy.date)
            assertEquals("Team dinner", swiggy.note)
            assertEquals(PaymentMethod.CARD, swiggy.paymentMethod)
            assertEquals(DefaultCategories.OTHER, swiggy.category)
            assertEquals(listOf("Rahul" to Money.rupees(1_000)), swiggy.shares.map { it.person.name to it.amount })
            assertEquals(setOf(10L, 11L), data.expenses.map { it.id }.toSet())
            assertEquals(listOf(1L, 2L, 3L), data.people.sortedBy { it.id }.map { it.id })

            // Balances are exactly what version 1 showed.
            val balances = data.balances.associate { it.person.name to it.summary }
            assertEquals(Money.rupees(600), balances.getValue("Rahul").receivable)
            assertEquals(Money.rupees(-200), balances.getValue("Amit").balance)
            assertEquals(Money.rupees(200), balances.getValue("Amit").payable)
            assertEquals(Money.rupees(30), balances.getValue("Neha").receivable)
            assertEquals("Auto fare", data.entries.single { it.id == 104L }.note)
            assertEquals(LedgerType.UDHAAR_TAKEN, data.entries.single { it.id == 102L }.type)

            // Version 1 charged Rahul before cashback: offered for review, not changed.
            assertEquals(Money.rupees(1_000), LegacyShares.find(data.expenses).single().oldShare)

            assertEquals(listOf("Amazon", "Swiggy", "Zomato"), data.rules.map { it.merchant })
            assertTrue(data.rules.all { it.uuid.isNotEmpty() && it.createdAtMillis > 0 })
            assertTrue(data.people.all { it.uuid.isNotEmpty() })
        } finally {
            database.close()
        }
    }
}
