package io.github.chiragbhatn.expensetracker.ui.rules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.chiragbhatn.expensetracker.data.CashbackRuleRepository
import io.github.chiragbhatn.expensetracker.data.SaveRuleResult
import io.github.chiragbhatn.expensetracker.domain.CashbackRule
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.cleanName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The add/edit rule dialog. [ruleId] is null when adding. */
data class RuleEditorState(
    val ruleId: Long? = null,
    val merchant: String = "",
    val percentage: String = "",
    val enabled: Boolean = true,
    val error: String? = null,
)

class CashbackRulesViewModel(private val repository: CashbackRuleRepository) : ViewModel() {
    /** Null until loaded. */
    val rules: StateFlow<List<CashbackRule>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The open add/edit dialog, as snapshot state so its text fields update synchronously. */
    var editor by mutableStateOf<RuleEditorState?>(null)
        private set

    fun startAdding() {
        editor = RuleEditorState()
    }

    fun startEditing(rule: CashbackRule) {
        editor = RuleEditorState(
            ruleId = rule.id,
            merchant = rule.merchant,
            percentage = rule.percentage.toInputString(),
            enabled = rule.enabled,
        )
    }

    fun onEditorChange(state: RuleEditorState) {
        editor = state.copy(error = null)
    }

    fun dismissEditor() {
        editor = null
    }

    fun saveEditor() {
        val current = editor ?: return
        val merchant = cleanName(current.merchant)
        val percentage = Percentage.parse(current.percentage)
        when {
            merchant.isEmpty() -> showError("Enter a merchant name")
            percentage == null -> showError("Enter a cashback percentage above 0 and up to 100")
            else -> viewModelScope.launch {
                when (val result = repository.save(current.ruleId, merchant, percentage, current.enabled)) {
                    SaveRuleResult.Saved -> editor = null
                    is SaveRuleResult.DuplicateMerchant ->
                        showError("A rule for ${result.existingMerchant} already exists")
                }
            }
        }
    }

    fun setEnabled(rule: CashbackRule, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(rule.id, enabled) }
    }

    fun delete(rule: CashbackRule) {
        viewModelScope.launch { repository.delete(rule.id) }
    }

    private fun showError(message: String) {
        editor = editor?.copy(error = message)
    }
}
