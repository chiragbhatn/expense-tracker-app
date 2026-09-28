package io.github.chiragbhatn.expensetracker.ui.expenses

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.chiragbhatn.expensetracker.data.CashbackRuleRepository
import io.github.chiragbhatn.expensetracker.data.ExpenseRepository
import io.github.chiragbhatn.expensetracker.data.UdhaarRepository
import io.github.chiragbhatn.expensetracker.domain.CashbackEligibility
import io.github.chiragbhatn.expensetracker.domain.CashbackQuote
import io.github.chiragbhatn.expensetracker.domain.CashbackRule
import io.github.chiragbhatn.expensetracker.domain.Expense
import io.github.chiragbhatn.expensetracker.domain.ExpenseInput
import io.github.chiragbhatn.expensetracker.domain.LedgerPosting
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.SavedCashback
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.domain.quoteCashback
import io.github.chiragbhatn.expensetracker.domain.toLedgerPosting
import io.github.chiragbhatn.expensetracker.ui.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the user has typed so far. */
data class ExpenseForm(
    val amountText: String = "",
    val merchant: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CARD,
    val paidForPersonId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    /** Cashback the expense was saved with; null for a new expense. */
    val saved: SavedCashback? = null,
    val showErrors: Boolean = false,
) {
    companion object {
        fun from(expense: Expense) = ExpenseForm(
            amountText = expense.amounts.originalAmount.toInputString(),
            merchant = expense.merchant,
            paymentMethod = expense.paymentMethod,
            paidForPersonId = expense.paidFor?.id,
            date = expense.date,
            note = expense.note,
            saved = SavedCashback(expense.merchant, expense.paymentMethod, expense.amounts.cashbackPercentage),
        )
    }
}

data class MerchantSuggestion(val name: String, val rule: CashbackRule?)

data class ExpenseEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = true,
    val form: ExpenseForm = ExpenseForm(),
    val quote: CashbackQuote = CashbackQuote(CashbackEligibility.NoRule, Percentage.ZERO, Percentage.ZERO),
    /** The form as it would be saved; null until a valid amount is entered. */
    val input: ExpenseInput? = null,
    val people: List<Person> = emptyList(),
    val paidFor: Person? = null,
    val suggestions: List<MerchantSuggestion> = emptyList(),
) {
    /** Both sides of the transaction, recalculated as the user types. */
    val preview: LedgerPosting? get() = input?.toLedgerPosting()

    val amountError: String?
        get() = if (form.showErrors && input?.originalAmount?.isPositive != true) "Enter an amount" else null

    val merchantError: String?
        get() = if (form.showErrors && form.merchant.isBlank()) "Enter a merchant" else null
}

class ExpenseEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val expenses: ExpenseRepository,
    cashbackRules: CashbackRuleRepository,
    private val udhaar: UdhaarRepository,
) : ViewModel() {

    private val expenseId: Long? = savedStateHandle.get<Long>(Routes.EXPENSE_ID_ARG)?.takeIf { it > 0 }
    private val loaded = MutableStateFlow(expenseId == null)
    private val _finished = MutableStateFlow(false)
    private var saving = false

    /** True once the expense has been saved or deleted and the screen should close. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    /** Snapshot state rather than a flow so text fields update synchronously while typing. */
    var form by mutableStateOf(ExpenseForm())
        private set

    val uiState: StateFlow<ExpenseEditUiState> = combine(
        snapshotFlow { form },
        loaded,
        cashbackRules.observeAll(),
        udhaar.observePeople(),
        expenses.observeRecentMerchants(),
    ) { form, loaded, rules, people, recentMerchants ->
        val quote = quoteCashback(form.merchant, form.paymentMethod, rules, form.saved)
        val paidFor = people.find { it.id == form.paidForPersonId }
        ExpenseEditUiState(
            isNew = expenseId == null,
            isLoading = !loaded,
            form = form,
            quote = quote,
            input = Money.parse(form.amountText)?.let { amount ->
                ExpenseInput(
                    originalAmount = amount,
                    cashbackPercentage = quote.percentage,
                    merchant = form.merchant,
                    paymentMethod = form.paymentMethod,
                    paidForPersonId = paidFor?.id,
                    date = form.date,
                    note = form.note,
                )
            },
            people = people,
            paidFor = paidFor,
            suggestions = merchantSuggestions(form.merchant, rules, recentMerchants),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseEditUiState(isNew = expenseId == null))

    init {
        if (expenseId != null) {
            viewModelScope.launch {
                expenses.get(expenseId)?.let { form = ExpenseForm.from(it) }
                loaded.value = true
            }
        }
    }

    fun onAmountChange(text: String) {
        form = form.copy(amountText = text)
    }

    fun onMerchantChange(merchant: String) {
        form = form.copy(merchant = merchant)
    }

    fun onPaymentMethodChange(method: PaymentMethod) {
        form = form.copy(paymentMethod = method)
    }

    fun onPaidForChange(personId: Long?) {
        form = form.copy(paidForPersonId = personId)
    }

    fun onDateChange(date: LocalDate) {
        form = form.copy(date = date)
    }

    fun onNoteChange(note: String) {
        form = form.copy(note = note)
    }

    /** Drops the percentage an edited expense was saved with, so the current rules apply. */
    fun useCurrentRule() {
        form = form.copy(saved = null)
    }

    fun addPerson(name: String) {
        viewModelScope.launch {
            val id = udhaar.addPerson(name)
            form = form.copy(paidForPersonId = id)
        }
    }

    fun save() {
        val state = uiState.value
        if (state.isLoading || saving) return
        val input = state.input
        if (input == null || !input.originalAmount.isPositive || input.merchant.isBlank()) {
            form = form.copy(showErrors = true)
            return
        }
        saving = true
        viewModelScope.launch {
            expenses.save(expenseId, input)
            _finished.value = true
        }
    }

    fun delete() {
        val id = expenseId ?: return
        viewModelScope.launch {
            expenses.delete(id)
            _finished.value = true
        }
    }
}

/** Rule merchants (enabled first) and recently used ones, filtered by what has been typed. */
private fun merchantSuggestions(
    typed: String,
    rules: List<CashbackRule>,
    recentMerchants: List<String>,
): List<MerchantSuggestion> {
    val query = nameKey(typed)
    val ruleKeys = rules.map { nameKey(it.merchant) }.toSet()
    val fromRules = rules.sortedByDescending { it.enabled }.map { MerchantSuggestion(it.merchant, it) }
    val fromHistory = recentMerchants.filter { nameKey(it) !in ruleKeys }.map { MerchantSuggestion(it, null) }
    return (fromRules + fromHistory)
        .filter { query.isEmpty() || nameKey(it.name).contains(query) }
        .take(8)
}
