package io.github.chiragbhatn.expensetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.DefaultCategories
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.domain.newUuid

@Database(
    entities = [
        CashbackRuleEntity::class,
        ExpenseEntity::class,
        PersonEntity::class,
        UdhaarEntryEntity::class,
        IncomeEntity::class,
        CreditCardEntity::class,
        CardPaymentEntity::class,
        RecurringExpenseEntity::class,
        CategoryEntity::class,
        ReminderEntity::class,
        AttachmentEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cashbackRuleDao(): CashbackRuleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun personDao(): PersonDao
    abstract fun udhaarDao(): UdhaarDao
    abstract fun incomeDao(): IncomeDao
    abstract fun cardDao(): CardDao
    abstract fun recurringDao(): RecurringDao
    abstract fun categoryDao(): CategoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        const val NAME = "expense-tracker.db"

        fun open(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .addCallback(SeedDefaults)
                .build()
    }

    /** Starter rules and categories for a new install; the user can change or delete them. */
    object SeedDefaults : RoomDatabase.Callback() {
        private val rules = listOf(
            Triple("Swiggy", 1_000, true),
            Triple("Zomato", 1_000, false),
            Triple("Amazon", 500, false),
        )

        override fun onCreate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            for ((merchant, basisPoints, enabled) in rules) {
                db.execSQL(
                    "INSERT INTO cashback_rules (merchant, merchant_key, cashback_percentage_bps, enabled, uuid, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(merchant, nameKey(merchant), basisPoints, if (enabled) 1 else 0, newUuid(), now, now),
                )
            }
            seedCategories(db, now)
        }
    }
}

internal fun seedCategories(db: SupportSQLiteDatabase, now: Long) {
    for (kind in CategoryKind.entries) {
        for (name in DefaultCategories.forKind(kind)) {
            db.execSQL(
                "INSERT OR IGNORE INTO categories (name, name_key, kind, is_default, uuid, created_at, updated_at) VALUES (?, ?, ?, 1, ?, ?, ?)",
                arrayOf(name, nameKey(name), kind.name, newUuid(), now, now),
            )
        }
    }
}

/** SQL for a random version 4 UUID, evaluated separately for every row. */
private const val RANDOM_UUID =
    "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) || '-' || " +
        "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

private const val DAY_MILLIS = 86_400_000L

/**
 * Version 1 → 2. Every existing row is kept with its id, amounts, dates and
 * notes; tables only gain columns:
 * - each row gets a permanent uuid, and created/updated times (the
 *   transaction's date for expenses and ledger entries, the upgrade time for
 *   people and rules);
 * - expenses get a category ("Other"), and optional card and recurring links;
 * - people get contact details, tags and a photo;
 * - ledger entries get a type: "You got" entries become payments received,
 *   or money borrowed when nothing was owed at the time; "You gave" entries
 *   become expense shares or udhaar given. Balances are unchanged;
 * - cashback rules can be limited to a card, so a merchant may have more
 *   than one rule.
 * New tables hold income, cards, card payments, recurring expenses,
 * categories (seeded with the defaults), reminders and attachments.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        // cashback_rules
        db.execSQL("ALTER TABLE `cashback_rules` ADD COLUMN `card_id` INTEGER")
        db.execSQL("ALTER TABLE `cashback_rules` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `cashback_rules` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `cashback_rules` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `cashback_rules` SET `uuid` = $RANDOM_UUID, `created_at` = $now, `updated_at` = $now")
        db.execSQL("DROP INDEX IF EXISTS `index_cashback_rules_merchant_key`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cashback_rules_merchant_key` ON `cashback_rules` (`merchant_key`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cashback_rules_uuid` ON `cashback_rules` (`uuid`)")

        // expenses
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'Other'")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `card_id` INTEGER")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `recurring_id` INTEGER")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE `expenses` SET `uuid` = $RANDOM_UUID, `created_at` = `date_epoch_day` * $DAY_MILLIS, `updated_at` = `date_epoch_day` * $DAY_MILLIS",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_card_id` ON `expenses` (`card_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expenses_uuid` ON `expenses` (`uuid`)")

        // people
        for (column in listOf("phone", "email", "address", "notes", "tags")) {
            db.execSQL("ALTER TABLE `people` ADD COLUMN `$column` TEXT NOT NULL DEFAULT ''")
        }
        db.execSQL("ALTER TABLE `people` ADD COLUMN `photo_path` TEXT")
        db.execSQL("ALTER TABLE `people` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `people` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `people` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `people` SET `uuid` = $RANDOM_UUID, `created_at` = $now, `updated_at` = $now")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_people_uuid` ON `people` (`uuid`)")

        // udhaar_entries
        db.execSQL("ALTER TABLE `udhaar_entries` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'UDHAAR_GIVEN'")
        db.execSQL("ALTER TABLE `udhaar_entries` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `udhaar_entries` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `udhaar_entries` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE `udhaar_entries` SET
                `type` = CASE
                    WHEN `direction` = 'GOT' THEN 'PAYMENT_RECEIVED'
                    WHEN `expense_id` IS NOT NULL THEN 'EXPENSE_SHARE'
                    ELSE 'UDHAAR_GIVEN'
                END,
                `uuid` = $RANDOM_UUID,
                `created_at` = `date_epoch_day` * $DAY_MILLIS,
                `updated_at` = `date_epoch_day` * $DAY_MILLIS
            """.trimIndent(),
        )
        markBorrowing(db)
        db.execSQL("DROP INDEX IF EXISTS `index_udhaar_entries_expense_id`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_udhaar_entries_expense_id` ON `udhaar_entries` (`expense_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_udhaar_entries_expense_id_person_id` ON `udhaar_entries` (`expense_id`, `person_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_udhaar_entries_uuid` ON `udhaar_entries` (`uuid`)")

        // New tables
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `incomes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount_paise` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, `category` TEXT NOT NULL, `date_epoch_day` INTEGER NOT NULL, `note` TEXT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_incomes_date_epoch_day` ON `incomes` (`date_epoch_day`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_incomes_uuid` ON `incomes` (`uuid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `credit_cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `bank` TEXT NOT NULL, " +
                "`last_four` TEXT NOT NULL, `credit_limit_paise` INTEGER NOT NULL, `statement_day` INTEGER NOT NULL, `due_day` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, `active` INTEGER NOT NULL, `reminder_days_before` INTEGER, `uuid` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_credit_cards_uuid` ON `credit_cards` (`uuid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `card_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `card_id` INTEGER NOT NULL, " +
                "`amount_paise` INTEGER NOT NULL, `date_epoch_day` INTEGER NOT NULL, `note` TEXT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`card_id`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_payments_card_id` ON `card_payments` (`card_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_card_payments_uuid` ON `card_payments` (`uuid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`amount_paise` INTEGER NOT NULL, `category` TEXT NOT NULL, `payment_method` TEXT NOT NULL, `card_id` INTEGER, " +
                "`start_epoch_day` INTEGER NOT NULL, `frequency` TEXT NOT NULL, `interval_count` INTEGER NOT NULL, `interval_unit` TEXT NOT NULL, " +
                "`next_epoch_day` INTEGER NOT NULL, `occurrence_index` INTEGER NOT NULL, `end_epoch_day` INTEGER, `active` INTEGER NOT NULL, " +
                "`reminder_days_before` INTEGER, `note` TEXT NOT NULL, `uuid` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, FOREIGN KEY(`card_id`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_card_id` ON `recurring_expenses` (`card_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_expenses_uuid` ON `recurring_expenses` (`uuid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`name_key` TEXT NOT NULL, `kind` TEXT NOT NULL, `is_default` INTEGER NOT NULL, `uuid` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name_key_kind` ON `categories` (`name_key`, `kind`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_uuid` ON `categories` (`uuid`)")
        seedCategories(db, now)

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, `due_epoch_day` INTEGER NOT NULL, `person_id` INTEGER, `done` INTEGER NOT NULL, `uuid` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_person_id` ON `reminders` (`person_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reminders_uuid` ON `reminders` (`uuid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `expense_id` INTEGER, " +
                "`person_id` INTEGER, `file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `uuid` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`expense_id`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_expense_id` ON `attachments` (`expense_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_person_id` ON `attachments` (`person_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)")
    }

    /**
     * Version 1 had one "You got" kind of entry. When the person owed
     * nothing at the time, the money was lent to you, so it is recorded as
     * udhaar taken (you owe them) instead of a payment (their credit).
     */
    private fun markBorrowing(db: SupportSQLiteDatabase) {
        val borrowed = mutableListOf<Long>()
        db.query("SELECT `id`, `person_id`, `direction`, `amount_paise` FROM `udhaar_entries` ORDER BY `person_id`, `date_epoch_day`, `id`").use { cursor ->
            var person = Long.MIN_VALUE
            var balance = 0L
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val personId = cursor.getLong(1)
                val got = cursor.getString(2) == "GOT"
                val amount = cursor.getLong(3)
                if (personId != person) {
                    person = personId
                    balance = 0
                }
                if (got) {
                    if (balance <= 0) borrowed += id
                    balance -= amount
                } else {
                    balance += amount
                }
            }
        }
        borrowed.chunked(500).forEach { ids ->
            db.execSQL("UPDATE `udhaar_entries` SET `type` = 'UDHAAR_TAKEN' WHERE `id` IN (${ids.joinToString(",")})")
        }
    }
}
