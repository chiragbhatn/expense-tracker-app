package io.github.chiragbhatn.expensetracker

import android.app.Application
import android.content.Context
import io.github.chiragbhatn.expensetracker.data.CashbackRuleRepository
import io.github.chiragbhatn.expensetracker.data.ExpenseRepository
import io.github.chiragbhatn.expensetracker.data.UdhaarRepository
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase

class ExpenseTrackerApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    private val database = AppDatabase.open(context)

    val cashbackRules = CashbackRuleRepository(database)
    val expenses = ExpenseRepository(database)
    val udhaar = UdhaarRepository(database)
}
