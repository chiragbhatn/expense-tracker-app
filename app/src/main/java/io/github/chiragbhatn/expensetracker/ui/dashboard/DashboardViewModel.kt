package io.github.chiragbhatn.expensetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.chiragbhatn.expensetracker.data.ExpenseRepository
import io.github.chiragbhatn.expensetracker.data.UdhaarRepository
import io.github.chiragbhatn.expensetracker.domain.Expense
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.Period
import io.github.chiragbhatn.expensetracker.domain.PersonBalance
import io.github.chiragbhatn.expensetracker.domain.SpendingSummary
import io.github.chiragbhatn.expensetracker.domain.moneyToGive
import io.github.chiragbhatn.expensetracker.domain.moneyToReceive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

data class DashboardUiState(
    val period: Period = Period.Month(YearMonth.now()),
    val canShowNextMonth: Boolean = false,
    val summary: SpendingSummary = SpendingSummary.of(emptyList()),
    val recent: List<Expense> = emptyList(),
    /** Udhaar is a running balance, so it is not limited to the selected period. */
    val owedToYou: List<PersonBalance> = emptyList(),
    val moneyToReceive: Money = Money.ZERO,
    val moneyToGive: Money = Money.ZERO,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(expenses: ExpenseRepository, udhaar: UdhaarRepository) : ViewModel() {
    private val period = MutableStateFlow<Period>(Period.Month(YearMonth.now()))

    val uiState: StateFlow<DashboardUiState> = combine(
        period.flatMapLatest { selected -> expenses.observe(selected).map { selected to it } },
        udhaar.observeBalances(),
    ) { (selected, periodExpenses), balances ->
        DashboardUiState(
            period = selected,
            canShowNextMonth = selected is Period.Month && selected.month < YearMonth.now(),
            summary = SpendingSummary.of(periodExpenses),
            recent = periodExpenses.take(5),
            owedToYou = balances.filter { it.balance.isPositive },
            moneyToReceive = balances.moneyToReceive(),
            moneyToGive = balances.moneyToGive(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun showPreviousMonth() = period.update {
        if (it is Period.Month) Period.Month(it.month.minusMonths(1)) else it
    }

    fun showNextMonth() = period.update {
        if (it is Period.Month && it.month < YearMonth.now()) Period.Month(it.month.plusMonths(1)) else it
    }

    fun toggleAllTime() = period.update {
        if (it is Period.AllTime) Period.Month(YearMonth.now()) else Period.AllTime
    }
}
