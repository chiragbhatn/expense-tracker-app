package io.github.chiragbhatn.expensetracker.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chiragbhatn.expensetracker.ExpenseTrackerApp
import io.github.chiragbhatn.expensetracker.MainActivity
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Drives the real app (Compose UI, view models and Room) through the cashback
 * and udhaar flows.
 *
 * Under Robolectric a dialog window takes focus, so a text field inside it
 * blinks its cursor forever and Compose never goes idle. These tests therefore
 * avoid dialogs with text fields (people are added through the repository);
 * the emulator walkthrough in scripts/device_smoke_test.py covers those dialogs.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val container get() = ApplicationProvider.getApplicationContext<ExpenseTrackerApp>().container

    @Test
    fun payingForRahulOnSwiggy_cashbackIsYoursAndRahulOwesTheFullAmount() {
        addSwiggyExpenseForRahul()

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
        val rahul = addSwiggyExpenseForRahul()

        compose.onNodeWithTag(TestTags.navTab(Routes.UDHAAR)).performClick()
        compose.waitUntilExists(hasText("Rahul"))
        compose.onNodeWithText("Rahul").performClick()
        compose.waitForText(TestTags.PERSON_BALANCE, "₹1,000")

        runBlocking {
            container.udhaar.addEntry(rahul, UdhaarDirection.GOT, Money.rupees(400), LocalDate.now(), "UPI")
        }
        compose.waitForText(TestTags.PERSON_BALANCE, "₹600")

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag(TestTags.navTab(Routes.DASHBOARD)).performClick()
        compose.waitForText(TestTags.DASHBOARD_TO_RECEIVE, "₹600")
        compose.assertText(TestTags.DASHBOARD_EFFECTIVE, "₹900")
    }

    @Test
    fun switchingOnARuleAppliesItsCashback() {
        compose.onNodeWithTag(TestTags.navTab(Routes.RULES)).performClick()
        compose.onNodeWithTag(TestTags.ruleSwitch("Zomato")).assertIsOff().performClick()
        compose.onNodeWithTag(TestTags.ruleSwitch("Zomato")).assertIsOn()

        compose.onNodeWithTag(TestTags.navTab(Routes.DASHBOARD)).performClick()
        compose.onNodeWithTag(TestTags.ADD_EXPENSE).performClick()
        compose.onNodeWithTag(TestTags.AMOUNT_INPUT).performTextInput("500")
        compose.onNodeWithTag(TestTags.MERCHANT_INPUT).performTextInput("zomato")

        compose.waitForText(TestTags.BREAKDOWN_EFFECTIVE, "₹450")
        compose.assertText(TestTags.BREAKDOWN_ORIGINAL, "₹500")
        compose.assertText(TestTags.BREAKDOWN_CASHBACK, "−₹50")
    }

    @Test
    fun deletingARuleAsksForConfirmation() {
        compose.onNodeWithTag(TestTags.navTab(Routes.RULES)).performClick()
        compose.waitUntilExists(hasText("Amazon"))

        compose.onNodeWithContentDescription("Delete Amazon rule").performClick()
        compose.onNodeWithTag(TestTags.DIALOG_CONFIRM).performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Amazon")).fetchSemanticsNodes().isEmpty() }
    }

    /** Records ₹1,000 on Swiggy by card for Rahul and returns Rahul's id. */
    private fun addSwiggyExpenseForRahul(): Long {
        val rahul = runBlocking { container.udhaar.addPerson("Rahul") }

        compose.onNodeWithTag(TestTags.ADD_EXPENSE).performClick()
        compose.onNodeWithTag(TestTags.AMOUNT_INPUT).performTextInput("1000")
        compose.onNodeWithTag(TestTags.MERCHANT_INPUT).performTextInput("Swiggy")
        compose.waitUntilExists(hasTestTag(TestTags.paidFor("Rahul")))
        compose.onNodeWithTag(TestTags.paidFor("Rahul")).performScrollTo().performClick()

        // The form shows the card side and the udhaar side of the same payment.
        compose.waitForText(TestTags.BREAKDOWN_UDHAAR, "₹1,000")
        compose.assertText(TestTags.BREAKDOWN_ORIGINAL, "₹1,000")
        compose.assertText(TestTags.BREAKDOWN_CASHBACK, "−₹100")
        compose.assertText(TestTags.BREAKDOWN_EFFECTIVE, "₹900")
        compose.onNodeWithText("Cashback (10%)", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Rahul's udhaar", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag(TestTags.SAVE_EXPENSE).performScrollTo().performClick()
        return rahul
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
