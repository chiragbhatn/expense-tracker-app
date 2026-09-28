package io.github.chiragbhatn.expensetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.chiragbhatn.expensetracker.domain.nameKey

@Database(
    entities = [
        CashbackRuleEntity::class,
        ExpenseEntity::class,
        PersonEntity::class,
        UdhaarEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cashbackRuleDao(): CashbackRuleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun personDao(): PersonDao
    abstract fun udhaarDao(): UdhaarDao

    companion object {
        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "expense-tracker.db")
                .addCallback(SeedDefaultRules)
                .build()
    }

    /** Starter rules the user can edit, switch off or delete. */
    object SeedDefaultRules : RoomDatabase.Callback() {
        private val defaults = listOf(
            Triple("Swiggy", 1_000, true),
            Triple("Zomato", 1_000, false),
            Triple("Amazon", 500, false),
        )

        override fun onCreate(db: SupportSQLiteDatabase) {
            for ((merchant, basisPoints, enabled) in defaults) {
                db.execSQL(
                    "INSERT INTO cashback_rules (merchant, merchant_key, cashback_percentage_bps, enabled) VALUES (?, ?, ?, ?)",
                    arrayOf(merchant, nameKey(merchant), basisPoints, if (enabled) 1 else 0),
                )
            }
        }
    }
}
