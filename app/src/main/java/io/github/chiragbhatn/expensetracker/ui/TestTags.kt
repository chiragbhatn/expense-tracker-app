package io.github.chiragbhatn.expensetracker.ui

import io.github.chiragbhatn.expensetracker.domain.PaymentMethod

/** Semantics tags used by UI tests; exposed as resource ids for UI Automator too. */
object TestTags {
    const val ADD_EXPENSE = "add_expense"
    const val AMOUNT_INPUT = "amount_input"
    const val MERCHANT_INPUT = "merchant_input"
    const val SAVE_EXPENSE = "save_expense"
    const val ADD_PERSON = "add_person"
    const val ADD_RULE = "add_rule"
    const val NAME_INPUT = "name_input"
    const val DIALOG_CONFIRM = "dialog_confirm"

    const val BREAKDOWN_ORIGINAL = "breakdown_original"
    const val BREAKDOWN_CASHBACK = "breakdown_cashback"
    const val BREAKDOWN_EFFECTIVE = "breakdown_effective"
    const val BREAKDOWN_UDHAAR = "breakdown_udhaar"

    const val DASHBOARD_CARD_SPENDING = "dashboard_card_spending"
    const val DASHBOARD_OTHER_SPENDING = "dashboard_other_spending"
    const val DASHBOARD_CASHBACK = "dashboard_cashback"
    const val DASHBOARD_EFFECTIVE = "dashboard_effective"
    const val DASHBOARD_TO_RECEIVE = "dashboard_to_receive"

    const val PERSON_BALANCE = "person_balance"
    const val RULE_MERCHANT_INPUT = "rule_merchant_input"
    const val RULE_PERCENTAGE_INPUT = "rule_percentage_input"

    fun paymentMethod(method: PaymentMethod) = "payment_${method.name.lowercase()}"
    fun paidFor(name: String) = "paid_for_$name"
    fun merchantSuggestion(name: String) = "merchant_suggestion_$name"
    fun ruleSwitch(merchant: String) = "rule_switch_$merchant"
    fun navTab(route: String) = "tab_$route"
}
