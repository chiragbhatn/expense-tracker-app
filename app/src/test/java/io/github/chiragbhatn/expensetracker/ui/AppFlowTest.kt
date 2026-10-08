package io.github.chiragbhatn.expensetracker.ui

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chiragbhatn.expensetracker.ExpenseTrackerApp
import io.github.chiragbhatn.expensetracker.MainActivity
import io.github.chiragbhatn.expensetracker.domain.AppSettings
import io.github.chiragbhatn.expensetracker.domain.CashbackBreakdown
import io.github.chiragbhatn.expensetracker.domain.ExpenseInput
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.Split
import io.github.chiragbhatn.expensetracker.domain.ThemeMode
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Drives the real app (Compose UI, view models and Room) through the main
 * flows.
 *
 * Under Robolectric a focused text field inside a dialog blinks its cursor
 * forever and Compose never goes idle, so these tests avoid typing into
 * dialogs (people and amounts that would need one are set up through the
 * repositories); the emulator walkthrough in scripts/device_smoke_test.py
 * covers those dialogs.
 *
 * Saving goes through Room on a background thread, which Compose's idle
 * synchronisation does not wait for, so results are awaited with waitUntil.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<ExpenseTrackerApp>()
    private val container get() = app.container
    private val today get() = LocalDate.now()

    @Before
    fun resetSettings() = runBlocking {
        container.settings.update { AppSettings() }
    }

    private fun openAddMenu(item: String) {
        compose.waitUntilExists(hasTestTag(TestTags.ADD_MENU))
        compose.onNodeWithTag(TestTags.ADD_MENU).performClick()
        compose.onNodeWithTag(item).performClick()
    }

    /** Starts a card expense of [rupees] at [merchant] and adds [people] to it. */
    private fun startExpense(rupees: String, merchant: String, vararg people: String) {
        openAddMenu(TestTags.ADD_EXPENSE)
        compose.waitUntilExists(hasTestTag(TestTags.AMOUNT_INPUT))
        compose.onNodeWithTag(TestTags.AMOUNT_INPUT).performTextInput(rupees)
        compose.onNodeWithTag(TestTags.MERCHANT_INPUT).performTextInput(merchant)
        if (people.isNotEmpty()) {
            compose.onNodeWithTag(TestTags.SPLIT_ADD_PEOPLE).performScrollTo().performClick()
            people.forEach { name ->
                compose.waitUntilExists(hasTestTag(TestTags.pickPerson(name)))
                compose.onNodeWithTag(TestTags.pickPerson(name)).performClick()
            }
            compose.onNodeWithTag(TestTags.DIALOG_CONFIRM).performClick()
        }
    }

    private fun save() {
        compose.onNodeWithTag(TestTags.SAVE_EXPENSE).performScrollTo().performClick()
        compose.waitUntilExists(hasTestTag(TestTags.navTab(Routes.HOME)))
    }

    @Test
    fun swiggyFor200_rahulOwesTheAmountAfterCashback() {
        runBlocking { container.people.addPerson("Rahul") }

        startExpense("200", "Swiggy", "Rahul")
        // Paid entirely for Rahul: take your own share out of the split.
        compose.onNodeWithTag(TestTags.SPLIT_INCLUDE_ME).performScrollTo().performClick()

        compose.waitForText(TestTags.BREAKDOWN_EFFECTIVE, "₹180")
        compose.assertText(TestTags.BREAKDOWN_ORIGINAL, "₹200")
        compose.assertText(TestTags.BREAKDOWN_CASHBACK, "−₹20")
        compose.waitForText(TestTags.shareAmount("Rahul"), "₹180")
        compose.onNodeWithText("Cashback (10%)", useUnmergedTree = true).assertExists()
        save()

        compose.waitForText(TestTags.DASHBOARD_TO_RECEIVE, "₹180")
        compose.assertText(TestTags.DASHBOARD_EXPENSES, "₹200")
        compose.assertText(TestTags.DASHBOARD_CASHBACK, "₹20")
        compose.assertText(TestTags.DASHBOARD_EFFECTIVE, "₹180")
        compose.assertText(TestTags.DASHBOARD_CARD_SPENDING, "₹200")
    }

    @Test
    fun aSplitBetweenThreePeopleChargesEachAThird() {
        runBlocking {
            container.people.addPerson("Rahul")
            container.people.addPerson("Amit")
        }

        startExpense("1000", "Swiggy", "Rahul", "Amit")

        compose.waitForText(TestTags.BREAKDOWN_EFFECTIVE, "₹900")
        compose.waitForText(TestTags.shareAmount("Rahul"), "₹300")
        compose.assertText(TestTags.shareAmount("Amit"), "₹300")
        compose.onNodeWithText("Your share", useUnmergedTree = true).assertExists()
        save()

        compose.waitForText(TestTags.DASHBOARD_TO_RECEIVE, "₹600")
    }

    @Test
    fun customSharesMustAddUpBeforeSaving() {
        runBlocking { container.people.addPerson("Rahul") }

        startExpense("1000", "Swiggy", "Rahul")
        compose.onNodeWithTag(TestTags.SPLIT_INCLUDE_ME).performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.SPLIT_MODE_CUSTOM).performScrollTo().performClick()
        // Charging Rahul the full ₹1,000 is more than the ₹900 the expense cost after cashback.
        compose.waitUntilExists(hasTestTag(TestTags.shareInput("Rahul")))
        compose.onNodeWithTag(TestTags.shareInput("Rahul")).performScrollTo().performTextReplacement("1000")

        compose.waitUntilExists(hasTestTag(TestTags.SPLIT_ERROR))
        compose.onNodeWithTag(TestTags.SAVE_EXPENSE).performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.AMOUNT_INPUT).assertExists()
        assertEquals(0, runBlocking { container.loadData() }.expenses.size)

        compose.onNodeWithTag(TestTags.shareInput("Rahul")).performScrollTo().performTextReplacement("900")
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(TestTags.SPLIT_ERROR)).fetchSemanticsNodes().isEmpty() }
        save()
        compose.waitForText(TestTags.DASHBOARD_TO_RECEIVE, "₹900")
    }

    @Test
    fun cashbackIsOnlySubtractedFromCardPayments() {
        startExpense("750", "Swiggy")
        compose.onNodeWithTag(TestTags.paymentMethod(PaymentMethod.UPI)).performClick()

        compose.waitForText(TestTags.BREAKDOWN_EFFECTIVE, "₹750")
        compose.onNodeWithTag(TestTags.BREAKDOWN_CASHBACK, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Swiggy cashback applies to card payments only.", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag(TestTags.paymentMethod(PaymentMethod.CARD)).performClick()

        compose.waitForText(TestTags.BREAKDOWN_EFFECTIVE, "₹675")
        compose.assertText(TestTags.BREAKDOWN_ORIGINAL, "₹750")
        compose.assertText(TestTags.BREAKDOWN_CASHBACK, "−₹75")
    }

    @Test
    fun personProfileShowsBalanceAndSettlesInFull() {
        val rahul = addExpenseFor("Rahul", rupees = 500)

        openPerson("Rahul")
        compose.waitForText(TestTags.PERSON_HEADLINE, "Rahul owes you ₹450")
        compose.assertText(TestTags.PERSON_BALANCE, "₹450")
        compose.onNodeWithTag(TestTags.SHARE_BALANCE).assertExists()
        compose.onNodeWithTag(TestTags.RECORD_PAYMENT).assertExists()

        runBlocking { container.people.addEntry(rahul, LedgerType.PAYMENT_RECEIVED, UdhaarDirection.GOT, Money.rupees(300), today, "UPI") }
        compose.waitForText(TestTags.PERSON_HEADLINE, "Rahul owes you ₹150")
        compose.assertText(TestTags.TOTAL_DUE, "₹450")
        compose.assertText(TestTags.TOTAL_PAID, "₹300")

        compose.onNodeWithTag(TestTags.SETTLE).performScrollTo().performClick()
        compose.waitUntilExists(hasTestTag(TestTags.SETTLE_FULL))
        compose.onNodeWithTag(TestTags.SETTLE_FULL).performClick()
        compose.waitForText(TestTags.SETTLE_PREVIEW, "Full settlement. After this: outstanding ₹0.")
        compose.onNodeWithTag(TestTags.SETTLE_CONFIRM).performScrollTo().performClick()

        compose.waitForText(TestTags.PERSON_HEADLINE, "Account settled ✓")
    }

    @Test
    fun extraPaymentBecomesCredit() {
        addExpenseFor("Rahul", rupees = 500)

        openPerson("Rahul")
        compose.waitForText(TestTags.PERSON_HEADLINE, "Rahul owes you ₹450")
        // Rahul pays ₹500 against the ₹450 he owes.
        compose.onNodeWithTag(TestTags.SETTLE).performScrollTo().performClick()
        compose.waitUntilExists(hasTestTag(TestTags.SETTLE_PARTIAL))
        compose.onNodeWithTag(TestTags.SETTLE_PARTIAL).performClick()
        compose.onNodeWithTag(TestTags.SETTLE_AMOUNT).performTextInput("500")
        compose.waitForText(TestTags.SETTLE_PREVIEW, "Extra payment. After this: outstanding ₹0, Rahul has ₹50 credit.")
        compose.onNodeWithTag(TestTags.SETTLE_CONFIRM).performScrollTo().performClick()

        compose.waitForText(TestTags.PERSON_HEADLINE, "Rahul has ₹50 extra credit.")
        compose.assertText(TestTags.PERSON_BALANCE, "₹50 credit")
    }

    @Test
    fun shareBalanceSendsTheMessageThroughTheShareSheet() {
        val rahul = runBlocking { container.people.addPerson("Rahul") }
        runBlocking { container.people.addEntry(rahul, LedgerType.UDHAAR_GIVEN, UdhaarDirection.GAVE, Money.rupees(1_500), today, "") }

        openPerson("Rahul")
        compose.waitForText(TestTags.PERSON_HEADLINE, "Rahul owes you ₹1,500")
        compose.onNodeWithTag(TestTags.SHARE_BALANCE).performScrollTo().performClick()
        compose.waitUntilExists(hasTestTag(TestTags.SHARE_SEND))
        compose.onNodeWithTag(TestTags.SHARE_SEND).performScrollTo().performClick()

        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(
            "Hi Rahul, your current pending balance is ₹1,500. Please settle it when convenient. Thanks!",
            send.getStringExtra(Intent.EXTRA_TEXT),
        )
    }

    @Test
    fun switchingOnARuleAppliesItsCashback() {
        compose.onNodeWithTag(TestTags.navTab(Routes.SETTINGS)).performClick()
        compose.onNodeWithTag(TestTags.settingsRow("Cashback rules")).performScrollTo().performClick()
        compose.waitUntilExists(hasTestTag(TestTags.ruleSwitch("Zomato")))
        compose.onNodeWithTag(TestTags.ruleSwitch("Zomato")).assertIsOff().performClick()
        compose.waitUntilExists(hasTestTag(TestTags.ruleSwitch("Zomato")) and isOn())
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithTag(TestTags.navTab(Routes.HOME)).performClick()
        startExpense("500", "zomato")

        compose.waitForText(TestTags.BREAKDOWN_EFFECTIVE, "₹450")
        compose.assertText(TestTags.BREAKDOWN_ORIGINAL, "₹500")
        compose.assertText(TestTags.BREAKDOWN_CASHBACK, "−₹50")
    }

    @Test
    fun searchingAMerchantShowsItsTotals() {
        addExpenseFor("Rahul", rupees = 200)

        compose.onNodeWithContentDescription("Search").performClick()
        compose.waitUntilExists(hasTestTag(TestTags.SEARCH_INPUT))
        compose.onNodeWithTag(TestTags.SEARCH_INPUT).performTextInput("swiggy")

        compose.waitUntilExists(hasText("Total spending"))
        compose.onNodeWithText("People: Rahul", useUnmergedTree = true).assertExists()
    }

    @Test
    fun themeCanBeSetToDark() {
        compose.onNodeWithTag(TestTags.navTab(Routes.SETTINGS)).performClick()
        compose.onNodeWithTag(TestTags.THEME_SETTING).performClick()
        compose.onNodeWithText("Dark").performClick()

        compose.waitUntil(5_000) { runBlocking { container.settings.current() }.themeMode == ThemeMode.DARK }
        compose.waitUntilExists(hasText("Dark"))
    }

    /** Records a ₹[rupees] Swiggy card expense at 10% cashback paid entirely for [name]; returns their id. */
    private fun addExpenseFor(name: String, rupees: Long): Long = runBlocking {
        val id = container.people.addPerson(name)
        val amounts = CashbackBreakdown.calculate(Money.rupees(rupees), Percentage(1_000))
        container.expenses.save(
            null,
            ExpenseInput(amounts.originalAmount, amounts.cashbackPercentage, "Swiggy", PaymentMethod.CARD, Split.forPerson(id, amounts.effectiveAmount), today, "", "Food"),
        )
        id
    }

    private fun openPerson(name: String) {
        compose.onNodeWithTag(TestTags.navTab(Routes.UDHAAR)).performClick()
        compose.waitUntilExists(hasTestTag(TestTags.person(name)))
        compose.onNodeWithTag(TestTags.person(name)).performClick()
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
