package io.github.chiragbhatn.expensetracker.ui.udhaar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.chiragbhatn.expensetracker.data.UdhaarRepository
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.domain.UdhaarEntry
import io.github.chiragbhatn.expensetracker.domain.balance
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.ui.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PersonDetailUiState(
    val person: Person,
    /** Newest first. */
    val entries: List<UdhaarEntry>,
    /** Positive: they owe you. Negative: you owe them. */
    val balance: Money,
)

class PersonDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val udhaar: UdhaarRepository,
) : ViewModel() {
    private val personId: Long = checkNotNull(savedStateHandle[Routes.PERSON_ID_ARG])

    /** Null until loaded, and again once the person is deleted. */
    val uiState: StateFlow<PersonDetailUiState?> = combine(
        udhaar.observePerson(personId),
        udhaar.observeEntries(personId),
    ) { person, entries ->
        person?.let { PersonDetailUiState(it, entries, entries.balance()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _renameError = MutableStateFlow<String?>(null)
    val renameError: StateFlow<String?> = _renameError.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun addEntry(direction: UdhaarDirection, amount: Money, date: LocalDate, note: String) {
        viewModelScope.launch { udhaar.addEntry(personId, direction, amount, date, note) }
    }

    fun deleteEntry(entry: UdhaarEntry) {
        viewModelScope.launch { udhaar.deleteEntry(entry) }
    }

    /** Renames the person; on a name clash sets [renameError] instead. Calls [onRenamed] on success. */
    fun rename(name: String, onRenamed: () -> Unit) {
        viewModelScope.launch {
            if (udhaar.renamePerson(personId, name)) {
                _renameError.value = null
                onRenamed()
            } else {
                _renameError.value = "Someone called ${cleanName(name)} already exists"
            }
        }
    }

    fun clearRenameError() {
        _renameError.value = null
    }

    fun deletePerson() {
        viewModelScope.launch {
            udhaar.deletePerson(personId)
            _deleted.value = true
        }
    }
}
