package io.github.chiragbhatn.expensetracker.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chiragbhatn.expensetracker.MainActivity
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Drives the real app (Compose UI, view models and Room) through the cashback and udhaar flows. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun payingForRahulOnSwiggy_cashbackIsYoursAndRahulOwesTheFullAmount() {
        addSwiggyExpenseForRahul(amount = "1000")

        // Dashboard keeps card spending, cashback, effective expenses and udhaar apart.
        compose.waitForText(TestTags.DASHBOARD_CARD_SPENDING, "₹1,000")
        compose.assertText(TestTags.DASHBOARD_CASHBACK, "₹100")
        compose.assertText(TestTags.DASHBOARD_EFFECTIVE, "₹900")
        compose.waitForText(TestTags.DASHBOARD_TO_RECEIVE, "₹1,000")
    }

    @Test
    fun cashbackIsOnlySubtractedFromCardPayments() {
        compose.onNodeWithTag(TestTags.ADD_EXPENSE).performClick()
        compose.onNodeWithTag(TestTags.AMOUNT_INPUT).performTextInput("750")
        compose.onNodeWithTag(TestTags.MERCHANT_INPUT).performTextInput("Swiggy")
        compose.onNodeWithTag(TestTags.paymentMethod(PaymentMethod.UPI)).performClick()

        compose.waitForText(TestTags.BREAKDOWN_EFFECTIVE, "₹750")
        compose.onNodeWithTag(TestTags.BREAKDOWN_CASHBACK, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Swiggy cashback applies to card payments only.", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag(TestTags.paymentMethod(PaymentMethod.CARD)).performClick()

        compose.waitForText(TestTags.BREAKDOWN_EFFECTIVE, "₹675")
        compose.assertText(TestTags.BREAKDOWN_ORIGINAL, "₹750")
        compose.assertText(TestTags.BREAKDOWN_CASHBACK, "−₹75")
        compose.onNodeWithText("Cashback (10%)", useUnmergedTree = true).assertExists()
    }

    @Test
    fun repaymentReducesWhatRahulOwesButNotYourExpense() {
        addSwiggyExpenseForRahul(amount = "1000")

        compose.onNodeWithTag(TestTags.navTab(Routes.UDHAAR)).performClick()
        compose.waitUntilExists(hasText("Rahul"))
        compose.onNodeWithText("Rahul").performClick()
        compose.waitForText(TestTags.PERSON_BALANCE, "₹1,000")

        compose.onNodeWithText("You got").performClick()
        compose.onNodeWithTag(TestTags.AMOUNT_INPUT).performTextInput("400")
        compose.onNodeWithTag(TestTags.DIALOG_CONFIRM).performClick()
        compose.waitForText(TestTags.PERSON_BALANCE, "₹600")

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag(TestTags.navTab(Routes.DASHBOARD)).performClick()
        compose.waitForText(TestTags.DASHBOARD_TO_RECEIVE, "₹600")
        compose.assertText(TestTags.DASHBOARD_EFFECTIVE, "₹900")
    }

    @Test
    fun cashbackRulesCanBeAddedEditedSwitchedOffAndDeleted() {
        compose.onNodeWithTag(TestTags.navTab(Routes.RULES)).performClick()

        compose.onNodeWithTag(TestTags.ADD_RULE).performClick()
        compose.onNodeWithTag(TestTags.RULE_MERCHANT_INPUT).performTextInput("swiggy")
        compose.onNodeWithTag(TestTags.RULE_PERCENTAGE_INPUT).performTextInput("5")
        compose.onNodeWithTag(TestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilExists(hasText("A rule for Swiggy already exists"))

        compose.onNodeWithTag(TestTags.RULE_MERCHANT_INPUT).performTextReplacement("Blinkit")
        compose.onNodeWithTag(TestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilExists(hasText("5% cashback · On"))

        compose.onNodeWithText("Blinkit").performClick()
        compose.onNodeWithTag(TestTags.RULE_PERCENTAGE_INPUT).performTextReplacement("2.5")
        compose.onNodeWithTag(TestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilExists(hasText("2.5% cashback · On"))

        compose.onNodeWithTag(TestTags.ruleSwitch("Blinkit")).performClick()
        compose.waitUntilExists(hasText("2.5% cashback · Off"))

        compose.onNodeWithContentDescription("Delete Blinkit rule").performClick()
        compose.onNodeWithTag(TestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Blinkit")).fetchSemanticsNodes().isEmpty() }
    }

    private fun addSwiggyExpenseForRahul(amount: String) {
        compose.onNodeWithTag(TestTags.ADD_EXPENSE).performClick()
        compose.onNodeWithTag(TestTags.AMOUNT_INPUT).performTextInput(amount)
        compose.onNodeWithTag(TestTags.MERCHANT_INPUT).performTextInput("Swiggy")
        compose.onNodeWithTag(TestTags.ADD_PERSON).performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.NAME_INPUT).performTextInput("Rahul")
        compose.onNodeWithTag(TestTags.DIALOG_CONFIRM).performClick()

        // The form shows the card side and the udhaar side of the same payment.
        compose.waitForText(TestTags.BREAKDOWN_UDHAAR, "₹1,000")
        compose.assertText(TestTags.BREAKDOWN_ORIGINAL, "₹1,000")
        compose.assertText(TestTags.BREAKDOWN_CASHBACK, "−₹100")
        compose.assertText(TestTags.BREAKDOWN_EFFECTIVE, "₹900")
        compose.onNodeWithText("Cashback (10%)", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Rahul's udhaar", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag(TestTags.SAVE_EXPENSE).performScrollTo().performClick()
    }
}

private fun SemanticsNode.text(): String? =
    config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

private fun ComposeTestRule.waitUntilExists(matcher: SemanticsMatcher) =
    waitUntil(5_000) { onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

private fun ComposeTestRule.waitForText(tag: String, expected: String) =
    waitUntil(5_000) {
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().any { it.text() == expected }
    }

private fun ComposeTestRule.assertText(tag: String, expected: String) =
    assertEquals(expected, onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().text())
