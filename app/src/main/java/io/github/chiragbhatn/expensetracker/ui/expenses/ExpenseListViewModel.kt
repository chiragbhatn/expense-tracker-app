package io.github.chiragbhatn.expensetracker.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.chiragbhatn.expensetracker.data.ExpenseRepository
import io.github.chiragbhatn.expensetracker.domain.Expense
import io.github.chiragbhatn.expensetracker.domain.Period
import io.github.chiragbhatn.expensetracker.domain.SpendingSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class MonthGroup(val month: YearMonth, val expenses: List<Expense>, val summary: SpendingSummary)

class ExpenseListViewModel(expenses: ExpenseRepository) : ViewModel() {
    /** Newest month first; null until loaded. */
    val months: StateFlow<List<MonthGroup>?> = expenses.observe(Period.AllTime)
        .map { all ->
            all.groupBy { YearMonth.from(it.date) }
                .map { (month, expenses) -> MonthGroup(month, expenses, SpendingSummary.of(expenses)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
