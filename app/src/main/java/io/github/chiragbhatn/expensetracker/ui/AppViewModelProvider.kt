package io.github.chiragbhatn.expensetracker.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.chiragbhatn.expensetracker.AppContainer
import io.github.chiragbhatn.expensetracker.ExpenseTrackerApp
import io.github.chiragbhatn.expensetracker.ui.dashboard.DashboardViewModel
import io.github.chiragbhatn.expensetracker.ui.expenses.ExpenseEditViewModel
import io.github.chiragbhatn.expensetracker.ui.expenses.ExpenseListViewModel
import io.github.chiragbhatn.expensetracker.ui.rules.CashbackRulesViewModel
import io.github.chiragbhatn.expensetracker.ui.udhaar.PersonDetailViewModel
import io.github.chiragbhatn.expensetracker.ui.udhaar.UdhaarViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { DashboardViewModel(container().expenses, container().udhaar) }
        initializer { ExpenseListViewModel(container().expenses) }
        initializer {
            ExpenseEditViewModel(
                savedStateHandle = createSavedStateHandle(),
                expenses = container().expenses,
                cashbackRules = container().cashbackRules,
                udhaar = container().udhaar,
            )
        }
        initializer { UdhaarViewModel(container().udhaar) }
        initializer { PersonDetailViewModel(createSavedStateHandle(), container().udhaar) }
        initializer { CashbackRulesViewModel(container().cashbackRules) }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExpenseTrackerApp).container
