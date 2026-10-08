package io.github.chiragbhatn.expensetracker.ui.expenses

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.chiragbhatn.expensetracker.AppContainer
import io.github.chiragbhatn.expensetracker.data.AppData
import io.github.chiragbhatn.expensetracker.data.InvalidSplitException
import io.github.chiragbhatn.expensetracker.data.NewAttachment
import io.github.chiragbhatn.expensetracker.domain.CashbackBreakdown
import io.github.chiragbhatn.expensetracker.domain.CashbackQuote
import io.github.chiragbhatn.expensetracker.domain.CashbackRule
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.CreditCard
import io.github.chiragbhatn.expensetracker.domain.DefaultCategories
import io.github.chiragbhatn.expensetracker.domain.Expense
import io.github.chiragbhatn.expensetracker.domain.ExpenseInput
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.ReceiptDraft
import io.github.chiragbhatn.expensetracker.domain.SavedCashback
import io.github.chiragbhatn.expensetracker.domain.Share
import io.github.chiragbhatn.expensetracker.domain.Split
import io.github.chiragbhatn.expensetracker.domain.SplitProblem
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.domain.newUuid
import io.github.chiragbhatn.expensetracker.domain.quoteCashback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

enum class SplitMode { EQUAL, CUSTOM }

/** A person sharing the expense; [amountText] is their custom share. */
data class Participant(val personId: Long, val name: String, val amountText: String = "")

/** What the user has entered so far. */
data class ExpenseForm(
    val amountText: String = "",
    val merchant: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CARD,
    val cardId: Long? = null,
    val category: String = DefaultCategories.OTHER,
    val categoryChosen: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    /** Cashback the expense was saved with; null for a new expense. */
    val saved: SavedCashback? = null,
    /** A cashback percentage typed for this expense only, instead of the rules'. */
    val customPercentage: Percentage? = null,
    val participants: List<Participant> = emptyList(),
    val includeMe: Boolean = true,
    val splitMode: SplitMode = SplitMode.EQUAL,
    val myAmountText: String = "",
    /** A scanned receipt photo waiting to be attached when the expense is saved. */
    val receiptPath: String? = null,
    val showErrors: Boolean = false,
)

data class MerchantSuggestion(val name: String, val rule: CashbackRule?)

data class ExpenseFormUiState(
    val isNew: Boolean,
    val form: ExpenseForm,
    val quote: CashbackQuote,
    /** Original, cashback and effective amounts; null until a valid amount is entered. */
    val amounts: CashbackBreakdown?,
    val split: Split?,
    val problems: List<SplitProblem>,
    val people: List<Person>,
    val cards: List<CreditCard>,
    val categories: List<String>,
    val suggestions: List<MerchantSuggestion>,
    /** True for a version 1 expense whose person was charged more than the effective amount. */
    val isLegacyShare: Boolean,
) {
    val percentage: Percentage get() = form.customPercentage ?: quote.percentage

    /** Each participant's share as shown: computed for equal splits, typed for custom ones. */
    fun shareFor(personId: Long): Money? = split?.shares?.firstOrNull { it.personId == personId }?.amount

    val amountError: String?
        get() = if (form.showErrors && amounts?.originalAmount?.isPositive != true) "Enter an amount" else null

    val merchantError: String?
        get() = if (form.showErrors && form.merchant.isBlank()) "Enter a merchant" else null

    val splitError: String?
        get() {
            val effective = amounts?.effectiveAmount ?: return null
            return problems.firstNotNullOfOrNull { problem ->
                when (problem) {
                    SplitProblem.NegativeShare -> "Shares cannot be negative."
                    SplitProblem.DuplicatePerson -> "Someone is in the split twice."
                    is SplitProblem.Mismatch -> if (problem.remaining.isPositive) {
                        "${problem.remaining.format()} of ${effective.format()} is not assigned to anyone yet."
                    } else {
                        "Shares add up to ${problem.allocated.format()}, ${problem.remaining.abs().format()} more than the effective ${effective.format()}."
                    }
                }
            }
        }
}

class ExpenseFormViewModel(savedStateHandle: SavedStateHandle, private val container: AppContainer) : ViewModel() {
    private val expenseId: Long? = savedStateHandle.get<Long>(ARG_EXPENSE_ID)?.takeIf { it > 0 }
    private val personId: Long? = savedStateHandle.get<Long>(ARG_PERSON_ID)?.takeIf { it > 0 }
    private val _finished = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private var saving = false
    private val loaded = MutableStateFlow(false)

    /** True once the expense has been saved or deleted and the screen should close. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    /** OCR results waiting for the user to review them. */
    var receiptDraft by mutableStateOf<ReceiptDraft?>(null)
        private set

    /** Snapshot state rather than a flow so text fields update synchronously while typing. */
    var form by mutableStateOf(ExpenseForm(date = container.clock.today()))
        private set

    val uiState: StateFlow<ExpenseFormUiState?> = combine(
        snapshotFlow { form },
        container.data.filterNotNull(),
        loaded,
    ) { form, data, loaded -> if (loaded) state(form, data) else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val data = container.data.filterNotNull().first()
            val expense = expenseId?.let { data.expensesById[it] }
            form = when {
                expense != null -> formFrom(expense)
                else -> newForm(data)
            }
            loaded.value = true
        }
    }

    private fun newForm(data: AppData): ExpenseForm {
        val activeCards = data.cards.filter { it.active }
        val lastCard = data.expenses.firstOrNull { it.cardId != null && data.cardsById[it.cardId]?.active == true }?.cardId
        val person = personId?.let { data.peopleById[it] }
        return form.copy(
            cardId = lastCard ?: activeCards.singleOrNull()?.id,
            participants = listOfNotNull(person?.let { Participant(it.id, it.name) }),
            includeMe = person == null,
        )
    }

    private fun formFrom(expense: Expense): ExpenseForm {
        val effective = expense.amounts.effectiveAmount
        val includeMe = expense.myShare.isPositive
        val ids = expense.shares.map { it.person.id }
        val equal = ids.isNotEmpty() && Split.equal(effective, includeMe, ids) == expense.split
        return ExpenseForm(
            amountText = expense.amounts.originalAmount.toInputString(),
            merchant = expense.merchant,
            paymentMethod = expense.paymentMethod,
            cardId = expense.cardId,
            category = expense.category,
            categoryChosen = true,
            date = expense.date,
            note = expense.note,
            saved = SavedCashback(expense.merchant, expense.paymentMethod, expense.amounts.cashbackPercentage, expense.cardId),
            participants = expense.shares.map { Participant(it.person.id, it.person.name, it.amount.toInputString()) },
            includeMe = includeMe || expense.shares.isEmpty(),
            splitMode = if (equal || expense.shares.isEmpty()) SplitMode.EQUAL else SplitMode.CUSTOM,
            myAmountText = if (includeMe) expense.myShare.toInputString() else "",
        )
    }

    private fun state(form: ExpenseForm, data: AppData): ExpenseFormUiState {
        val cardId = if (form.paymentMethod == PaymentMethod.CARD) form.cardId else null
        val quote = quoteCashback(form.merchant, form.paymentMethod, data.rules, form.saved, cardId)
        val percentage = form.customPercentage ?: quote.percentage
        val amounts = Money.parse(form.amountText)?.let { CashbackBreakdown.calculate(it, percentage) }
        val split = amounts?.let { splitFor(form, it.effectiveAmount) }
        val expense = expenseId?.let { data.expensesById[it] }
        return ExpenseFormUiState(
            isNew = expenseId == null,
            form = form,
            quote = quote,
            amounts = amounts,
            split = split,
            problems = if (amounts != null && split != null) split.problems(amounts.effectiveAmount) else emptyList(),
            people = data.people,
            cards = data.cards.filter { it.active || it.id == form.cardId },
            categories = data.categoryNames(CategoryKind.EXPENSE).let { if (form.category in it) it else it + form.category },
            suggestions = merchantSuggestions(form.merchant, data.rules, data.merchants),
            isLegacyShare = expense != null && expense.othersShare > expense.amounts.effectiveAmount,
        )
    }

    private fun splitFor(form: ExpenseForm, effective: Money): Split {
        if (form.participants.isEmpty()) return Split.mine(effective)
        return when (form.splitMode) {
            SplitMode.EQUAL -> Split.equal(effective, form.includeMe, form.participants.map { it.personId })
            SplitMode.CUSTOM -> Split(
                myShare = if (form.includeMe) Money.parse(form.myAmountText) ?: Money.ZERO else Money.ZERO,
                shares = form.participants.map { Share(it.personId, Money.parse(it.amountText) ?: Money.ZERO) },
            )
        }
    }

    fun onAmountChange(text: String) {
        form = form.copy(amountText = text)
    }

    fun onMerchantChange(merchant: String) {
        form = form.copy(merchant = merchant, customPercentage = null).withSuggestedCategory()
    }

    private fun ExpenseForm.withSuggestedCategory(): ExpenseForm {
        if (categoryChosen) return this
        val data = container.data.value ?: return this
        val previous = data.expenses.firstOrNull { nameKey(it.merchant) == nameKey(merchant) }?.category
        return copy(category = previous ?: DefaultCategories.OTHER)
    }

    fun onPaymentMethodChange(method: PaymentMethod) {
        form = form.copy(paymentMethod = method, customPercentage = null)
    }

    fun onCardChange(cardId: Long?) {
        form = form.copy(cardId = cardId, customPercentage = null)
    }

    fun onCategoryChange(category: String) {
        form = form.copy(category = category, categoryChosen = true)
    }

    fun onDateChange(date: LocalDate) {
        form = form.copy(date = date)
    }

    fun onNoteChange(note: String) {
        form = form.copy(note = note)
    }

    /** Sets a cashback percentage for this expense only; null goes back to the rules. */
    fun onCustomPercentage(percentage: Percentage?) {
        form = form.copy(customPercentage = percentage)
    }

    /** Drops the percentage an edited expense was saved with, so the current rules apply. */
    fun useCurrentRule() {
        form = form.copy(saved = null, customPercentage = null)
    }

    fun setParticipants(people: List<Person>) {
        val existing = form.participants.associateBy { it.personId }
        form = form.copy(participants = people.map { existing[it.id] ?: Participant(it.id, it.name) })
    }

    fun removeParticipant(personId: Long) {
        form = form.copy(participants = form.participants.filterNot { it.personId == personId })
    }

    fun addNewPerson(name: String) {
        viewModelScope.launch {
            val id = container.people.addPerson(name)
            val person = container.data.filterNotNull().first { it.peopleById.containsKey(id) }.peopleById.getValue(id)
            if (form.participants.none { it.personId == id }) form = form.copy(participants = form.participants + Participant(id, person.name))
        }
    }

    fun onIncludeMeChange(include: Boolean) {
        form = form.copy(includeMe = include)
    }

    /** Switching to custom amounts starts from the equal split, so only the differences need typing. */
    fun onSplitModeChange(mode: SplitMode) {
        val state = uiState.value
        form = if (mode == SplitMode.CUSTOM && form.splitMode == SplitMode.EQUAL && state?.split != null) {
            form.copy(
                splitMode = mode,
                myAmountText = if (form.includeMe) state.split.myShare.toInputString() else "",
                participants = form.participants.map { p -> p.copy(amountText = state.shareFor(p.personId)?.toInputString() ?: "") },
            )
        } else {
            form.copy(splitMode = mode)
        }
    }

    fun onShareChange(personId: Long, text: String) {
        form = form.copy(participants = form.participants.map { if (it.personId == personId) it.copy(amountText = text) else it })
    }

    fun onMyShareChange(text: String) {
        form = form.copy(myAmountText = text)
    }

    /** Charges everyone the effective amount split equally: the fix for a version 1 expense. */
    fun splitEqually() {
        form = form.copy(splitMode = SplitMode.EQUAL)
    }

    /** OCR finished: show what was read so the user can confirm it. Nothing is saved yet. */
    fun onReceiptRead(draft: ReceiptDraft, photo: File?) {
        receiptDraft = draft
        form = form.copy(receiptPath = photo?.path ?: form.receiptPath)
    }

    fun dismissReceipt() {
        receiptDraft = null
    }

    /** Copies the confirmed receipt values into the form. The user still saves the expense. */
    fun applyReceipt(merchant: String?, amount: Money?, date: LocalDate?) {
        var updated = form
        if (!merchant.isNullOrBlank()) updated = updated.copy(merchant = merchant, customPercentage = null)
        if (amount != null) updated = updated.copy(amountText = amount.toInputString())
        if (date != null) updated = updated.copy(date = date)
        form = updated.withSuggestedCategory()
        receiptDraft = null
    }

    fun clearError() {
        _error.value = null
    }

    fun save() {
        val state = uiState.value ?: return
        if (saving) return
        val amounts = state.amounts
        val split = state.split
        if (amounts == null || !amounts.originalAmount.isPositive || form.merchant.isBlank() || split == null || state.problems.isNotEmpty()) {
            form = form.copy(showErrors = true)
            return
        }
        saving = true
        val input = ExpenseInput(
            originalAmount = amounts.originalAmount,
            cashbackPercentage = amounts.cashbackPercentage,
            merchant = form.merchant,
            paymentMethod = form.paymentMethod,
            split = split,
            date = form.date,
            note = form.note,
            category = form.category,
            cardId = if (form.paymentMethod == PaymentMethod.CARD) form.cardId else null,
        )
        viewModelScope.launch {
            try {
                val receipt = form.receiptPath?.let { path -> keepReceipt(File(path)) }
                container.expenses.save(expenseId, input, receipt)
                _finished.value = true
            } catch (e: InvalidSplitException) {
                form = form.copy(showErrors = true)
            } catch (e: Exception) {
                _error.value = e.message ?: "The expense could not be saved."
            } finally {
                saving = false
            }
        }
    }

    private fun keepReceipt(photo: File): NewAttachment? {
        if (!photo.exists()) return null
        val target = container.files.attachment("${newUuid()}.jpg")
        photo.copyTo(target, overwrite = true)
        return NewAttachment(target, "image/jpeg")
    }

    fun delete() {
        val id = expenseId ?: return
        viewModelScope.launch {
            container.expenses.delete(id)
            _finished.value = true
        }
    }

    companion object {
        const val ARG_EXPENSE_ID = "expenseId"
        const val ARG_PERSON_ID = "personId"
        const val ARG_SCAN = "scan"
    }
}

/** Rule merchants (enabled first) and recently used ones, filtered by what has been typed. */
private fun merchantSuggestions(typed: String, rules: List<CashbackRule>, recentMerchants: List<String>): List<MerchantSuggestion> {
    val query = nameKey(typed)
    val ruleKeys = rules.map { nameKey(it.merchant) }.toSet()
    val fromRules = rules.sortedByDescending { it.enabled }.distinctBy { nameKey(it.merchant) }.map { MerchantSuggestion(it.merchant, it) }
    val fromHistory = recentMerchants.filter { nameKey(it) !in ruleKeys }.map { MerchantSuggestion(it, null) }
    return (fromRules + fromHistory)
        .filter { query.isEmpty() || (nameKey(it.name).contains(query) && nameKey(it.name) != query) }
        .take(8)
}
