package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ShareMessagesTest {

    private val rahul = Person(1, "Rahul")
    private val templates = ShareTemplates()

    private fun entry(type: LedgerType, rupees: Long, date: LocalDate, merchant: String? = null, note: String = "", direction: UdhaarDirection? = null) =
        UdhaarEntry(
            id = 0,
            personId = rahul.id,
            direction = direction ?: type.fixedDirection!!,
            amount = Money.rupees(rupees),
            date = date,
            note = note,
            expenseId = if (merchant != null) 1 else null,
            expenseMerchant = merchant,
            type = type,
        )

    private fun summary(vararg entries: UdhaarEntry) = LedgerSummary.of(entries.toList())

    @Test
    fun `when they owe you`() {
        val owes = summary(entry(LedgerType.UDHAAR_GIVEN, 1_500, LocalDate.of(2026, 10, 1)))

        assertEquals("Rahul owes you ₹1,500", ShareMessages.headline("Rahul", owes, Currency.INR))
        assertEquals(
            "Hi Rahul, your current pending balance is ₹1,500. Please settle it when convenient. Thanks!",
            ShareMessages.currentBalance("Rahul", owes, templates, Currency.INR),
        )
    }

    @Test
    fun `when the account is settled`() {
        val settled = summary(
            entry(LedgerType.UDHAAR_GIVEN, 1_500, LocalDate.of(2026, 10, 1)),
            entry(LedgerType.PAYMENT_RECEIVED, 1_500, LocalDate.of(2026, 10, 2)),
        )

        assertEquals("Account settled ✓", ShareMessages.headline("Rahul", settled, Currency.INR))
        assertEquals(
            "Hi Rahul, your account is fully settled. Current balance: ₹0. Thanks!",
            ShareMessages.currentBalance("Rahul", settled, templates, Currency.INR),
        )
        assertEquals(
            "Hi Rahul, your account is fully settled. Current balance: ₹0. Thanks!",
            ShareMessages.currentBalance("Rahul", LedgerSummary.EMPTY, templates, Currency.INR),
        )
    }

    @Test
    fun `when they have credit`() {
        val credit = summary(
            entry(LedgerType.EXPENSE_SHARE, 1_000, LocalDate.of(2026, 10, 1), merchant = "Swiggy"),
            entry(LedgerType.PAYMENT_RECEIVED, 1_500, LocalDate.of(2026, 10, 2)),
        )

        assertEquals("Rahul has ₹500 extra credit.", ShareMessages.headline("Rahul", credit, Currency.INR))
        assertEquals(
            "Hi Rahul, you've paid ₹500 extra. You currently have a ₹500 credit balance with me, " +
                "which will be adjusted against your next expense.",
            ShareMessages.currentBalance("Rahul", credit, templates, Currency.INR),
        )
    }

    @Test
    fun `when you owe them`() {
        val youOwe = summary(entry(LedgerType.UDHAAR_TAKEN, 2_000, LocalDate.of(2026, 10, 1)))

        assertEquals("You owe Rahul ₹2,000", ShareMessages.headline("Rahul", youOwe, Currency.INR))
        assertEquals("Hi Rahul, I owe you ₹2,000. I'll settle it soon. Thanks!", ShareMessages.currentBalance("Rahul", youOwe, templates, Currency.INR))
    }

    @Test
    fun `wording can be changed`() {
        val owes = summary(entry(LedgerType.UDHAAR_GIVEN, 1_500, LocalDate.of(2026, 10, 1)))
        val custom = templates.copy(owes = "Hey {name}! {amount} pending 🙂")

        assertEquals("Hey Rahul! ₹1,500 pending 🙂", ShareMessages.currentBalance("Rahul", owes, custom, Currency.INR))
        assertEquals("Hey Rahul! $1,500 pending 🙂", ShareMessages.currentBalance("Rahul", owes, custom, Currency.USD))
    }

    @Test
    fun `detailed statement lists every entry and the totals`() {
        val entries = listOf(
            entry(LedgerType.PAYMENT_RECEIVED, 300, LocalDate.of(2026, 10, 5), note = "GPay"),
            entry(LedgerType.EXPENSE_SHARE, 450, LocalDate.of(2026, 10, 1), merchant = "Swiggy"),
        )

        val text = ShareMessages.detailedStatement("Rahul", entries, LocalDate.of(2026, 10, 8), Currency.INR)

        assertEquals(
            """
            Statement for Rahul
            As of 8 Oct 2026

            • 1 Oct 2026 — Swiggy (expense share): +₹450
            • 5 Oct 2026 — Payment received, GPay: −₹300

            Total charged: ₹450
            Total paid: ₹300
            Balance: Rahul owes you ₹150
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `monthly summary shows opening and closing balances`() {
        val entries = listOf(
            entry(LedgerType.UDHAAR_GIVEN, 1_000, LocalDate.of(2026, 9, 20)),
            entry(LedgerType.EXPENSE_SHARE, 450, LocalDate.of(2026, 10, 1), merchant = "Swiggy"),
            entry(LedgerType.PAYMENT_RECEIVED, 300, LocalDate.of(2026, 10, 5)),
            entry(LedgerType.UDHAAR_GIVEN, 999, LocalDate.of(2026, 11, 2)),
        )

        val text = ShareMessages.monthlySummary("Rahul", entries, YearMonth.of(2026, 10), Currency.INR)

        assertEquals(
            """
            Rahul — October 2026
            Opening balance: Rahul owes you ₹1,000
            Added this month: ₹450
            Paid this month: ₹300

            • 1 Oct 2026 — Swiggy (expense share): +₹450
            • 5 Oct 2026 — Payment received: −₹300

            Closing balance: Rahul owes you ₹1,150
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `settlements read naturally in either direction`() {
        assertEquals("Settlement received", ShareMessages.describe(entry(LedgerType.SETTLEMENT, 100, LocalDate.of(2026, 10, 1), direction = UdhaarDirection.GOT)))
        assertEquals("Settlement paid", ShareMessages.describe(entry(LedgerType.SETTLEMENT, 100, LocalDate.of(2026, 10, 1), direction = UdhaarDirection.GAVE)))
    }
}
