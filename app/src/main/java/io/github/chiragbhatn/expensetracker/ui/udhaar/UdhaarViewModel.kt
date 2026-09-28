package io.github.chiragbhatn.expensetracker.ui.udhaar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.chiragbhatn.expensetracker.data.UdhaarRepository
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PersonBalance
import io.github.chiragbhatn.expensetracker.domain.moneyToGive
import io.github.chiragbhatn.expensetracker.domain.moneyToReceive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UdhaarUiState(
    val balances: List<PersonBalance>,
    val moneyToReceive: Money,
    val moneyToGive: Money,
)

class UdhaarViewModel(private val udhaar: UdhaarRepository) : ViewModel() {
    val uiState: StateFlow<UdhaarUiState?> = udhaar.observeBalances()
        .map { UdhaarUiState(it, it.moneyToReceive(), it.moneyToGive()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _addedPerson = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Emits the id of a newly added person so the screen can open their page. */
    val addedPerson: SharedFlow<Long> = _addedPerson.asSharedFlow()

    fun addPerson(name: String) {
        viewModelScope.launch { _addedPerson.emit(udhaar.addPerson(name)) }
    }
}
