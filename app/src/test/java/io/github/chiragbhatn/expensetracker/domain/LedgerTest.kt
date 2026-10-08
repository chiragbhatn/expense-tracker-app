package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerTest {

    private val rahul = Person(7, "Rahul")
    private val amit = Person(8, "Amit")
    private val neha = Person(9, "Neha")
    private val today = LocalDate.of(2026, 10, 8)
    private val tenPercent = Percentage(1_000)

    /** You paid by card for something that was entirely for Rahul. */
    private fun paidForRahul(rupees: Long, percentage: Percentage = tenPercent): LedgerPosting {
        val effective = CashbackBreakdown.calculate(Money.rupees(rupees), percentage).effectiveAmount
        return ExpenseInput(
            originalAmount = Money.rupees(rupees),
            cashbackPercentage = percentage,
            merchant = "Swiggy",
            paymentMethod = PaymentMethod.CARD,
            split = Split.forPerson(rahul.id, effective),
            date = today,
            note = "",
        ).toLedgerPosting()
    }

    private fun entry(person: Person, type: LedgerType, rupees: Long, date: LocalDate = today, id: Long = 0, direction: UdhaarDirection? = null) =
        UdhaarEntry(
            id = id,
            personId = person.id,
            direction = direction ?: type.fixedDirection!!,
            amount = Money.rupees(rupees),
            date = date,
            note = "",
            expenseId = null,
            expenseMerchant = null,
            type = type,
        )

    @Test
    fun `200 with 10 percent cashback - Rahul owes the effective 180`() {
        val posting = paidForRahul(200)

        assertEquals(Money.rupees(200), posting.expense.originalAmount)
        assertEquals(tenPercent, posting.expense.cashbackPercentage)
        assertEquals(Money.rupees(20), posting.expense.cashbackAmount)
        assertEquals(Money.rupees(180), posting.expense.effectiveAmount)
        assertEquals(listOf(Share(rahul.id, Money.rupees(180))), posting.shares)
        assertTrue(posting.problems.isEmpty())

        val summary = LedgerSummary.of(listOf(entry(rahul, LedgerType.EXPENSE_SHARE, 180)))
        assertEquals(Money.rupees(180), summary.receivable)
        assertEquals(BalanceState.OWES_YOU, summary.state)
    }

    @Test
    fun `500 with 10 percent cashback - Rahul owes the effective 450`() {
        val posting = paidForRahul(500)

        assertEquals(Money.rupees(500), posting.expense.originalAmount)
        assertEquals(Money.rupees(50), posting.expense.cashbackAmount)
        assertEquals(Money.rupees(450), posting.expense.effectiveAmount)
        assertEquals(Money.rupees(450), posting.shares.single().amount)
    }

    @Test
    fun `a share of the original amount no longer adds up`() {
        val posting = ExpenseInput(
            originalAmount = Money.rupees(200),
            cashbackPercentage = tenPercent,
            merchant = "Swiggy",
            paymentMethod = PaymentMethod.CARD,
            split = Split.forPerson(rahul.id, Money.rupees(200)),
            date = today,
            note = "",
        ).toLedgerPosting()

        val mismatch = posting.problems.single() as SplitProblem.Mismatch
        assertEquals(Money.rupees(-20), mismatch.remaining)
    }

    @Test
    fun `partial settlement leaves the rest outstanding`() {
        val owes = LedgerSummary.of(listOf(entry(rahul, LedgerType.EXPENSE_SHARE, 450)))
        assertEquals(SettlementKind.PARTIAL, Settlement.kind(owes, Money.rupees(300)))
        assertEquals(UdhaarDirection.GOT, Settlement.directionFor(owes))

        val after = LedgerSummary.of(
            listOf(entry(rahul, LedgerType.EXPENSE_SHARE, 450), entry(rahul, LedgerType.PAYMENT_RECEIVED, 300)),
        )

        assertEquals(Money.rupees(150), after.receivable)
        assertEquals(Money.ZERO, after.credit)
        assertEquals(Money.rupees(450), after.totalDue)
        assertEquals(Money.rupees(300), after.totalPaid)
        assertEquals(after, Settlement.preview(owes, Money.rupees(300)))
    }

    @Test
    fun `paying more than owed becomes credit, not a negative expense`() {
        val owes = LedgerSummary.of(listOf(entry(rahul, LedgerType.EXPENSE_SHARE, 450)))
        assertEquals(SettlementKind.EXTRA, Settlement.kind(owes, Money.rupees(500)))

        val after = Settlement.preview(owes, Money.rupees(500))

        assertEquals(Money.ZERO, after.receivable)
        assertEquals(Money.rupees(50), after.credit)
        assertEquals(Money.ZERO, after.payable)
        assertEquals(BalanceState.HAS_CREDIT, after.state)
        assertEquals("Rahul has ₹50 extra credit.", ShareMessages.headline("Rahul", after, Currency.INR))
    }

    @Test
    fun `credit is used up by the next expense`() {
        val summary = LedgerSummary.of(
            listOf(
                entry(rahul, LedgerType.EXPENSE_SHARE, 450, today.minusDays(3)),
                entry(rahul, LedgerType.SETTLEMENT, 500, today.minusDays(2), direction = UdhaarDirection.GOT),
                entry(rahul, LedgerType.EXPENSE_SHARE, 180, today),
            ),
        )

        assertEquals(Money.rupees(130), summary.receivable)
        assertEquals(Money.ZERO, summary.credit)
    }

    @Test
    fun `full settlement settles the account`() {
        val owes = LedgerSummary.of(listOf(entry(rahul, LedgerType.EXPENSE_SHARE, 450)))
        assertEquals(Money.rupees(450), Settlement.fullAmount(owes))
        assertEquals(SettlementKind.FULL, Settlement.kind(owes, Money.rupees(450)))
        assertEquals(BalanceState.SETTLED, Settlement.preview(owes, Money.rupees(450)).state)
    }

    @Test
    fun `900 split equally between three people is 300 each`() {
        val split = Split.equal(Money.rupees(900), includeMe = true, personIds = listOf(rahul.id, amit.id))

        assertEquals(Money.rupees(300), split.myShare)
        assertEquals(listOf(Share(rahul.id, Money.rupees(300)), Share(amit.id, Money.rupees(300))), split.shares)
        assertTrue(split.isValidFor(Money.rupees(900)))

        val friendsOnly = Split.equal(Money.rupees(900), includeMe = false, personIds = listOf(rahul.id, amit.id, neha.id))
        assertEquals(Money.ZERO, friendsOnly.myShare)
        assertEquals(List(3) { Money.rupees(300) }, friendsOnly.shares.map { it.amount })
    }

    @Test
    fun `odd paise go to the first people so the split still adds up`() {
        val split = Split.equal(Money(10_000), includeMe = true, personIds = listOf(rahul.id, amit.id))

        assertEquals(Money(3_334), split.myShare)
        assertEquals(listOf(Money(3_333), Money(3_333)), split.shares.map { it.amount })
        assertEquals(Money(10_000), split.allocated)
    }

    @Test
    fun `custom shares must add up to the effective amount`() {
        val effective = Money.rupees(900)
        val short = Split(Money.rupees(300), listOf(Share(rahul.id, Money.rupees(300)), Share(amit.id, Money.rupees(200))))
        assertEquals(listOf(SplitProblem.Mismatch(Money.rupees(800), effective)), short.problems(effective))
        assertEquals(Money.rupees(100), (short.problems(effective).single() as SplitProblem.Mismatch).remaining)

        val custom = Split(Money.rupees(100), listOf(Share(rahul.id, Money.rupees(500)), Share(amit.id, Money.rupees(300))))
        assertTrue(custom.isValidFor(effective))

        val twice = Split(Money.ZERO, listOf(Share(rahul.id, Money.rupees(450)), Share(rahul.id, Money.rupees(450))))
        assertEquals(listOf(SplitProblem.DuplicatePerson), twice.problems(effective))

        val negative = Split(Money.rupees(1_000), listOf(Share(rahul.id, Money.rupees(-100))))
        assertTrue(SplitProblem.NegativeShare in negative.problems(effective))
    }

    @Test
    fun `nobody else involved means the whole effective amount is yours`() {
        val split = Split.mine(Money.rupees(180))
        assertTrue(split.shares.isEmpty())
        assertTrue(split.isValidFor(Money.rupees(180)))
    }

    @Test
    fun `money you borrowed is payable, money they overpaid is credit`() {
        val borrowed = LedgerSummary.of(listOf(entry(amit, LedgerType.UDHAAR_TAKEN, 1_000)))
        assertEquals(Money.rupees(1_000), borrowed.payable)
        assertEquals(Money.ZERO, borrowed.credit)
        assertEquals(BalanceState.YOU_OWE, borrowed.state)
        assertEquals(UdhaarDirection.GAVE, Settlement.directionFor(borrowed))

        val partlyRepaid = LedgerSummary.of(
            listOf(entry(amit, LedgerType.UDHAAR_TAKEN, 1_000, id = 1), entry(amit, LedgerType.PAYMENT_MADE, 400, id = 2)),
        )
        assertEquals(Money.rupees(600), partlyRepaid.payable)

        // Repaid in full, after they had paid ₹50 extra earlier: only the credit is left.
        val creditThenLoan = LedgerSummary.of(
            listOf(
                entry(amit, LedgerType.EXPENSE_SHARE, 450, today.minusDays(4)),
                entry(amit, LedgerType.PAYMENT_RECEIVED, 500, today.minusDays(3)),
                entry(amit, LedgerType.UDHAAR_TAKEN, 1_000, today.minusDays(2)),
                entry(amit, LedgerType.PAYMENT_MADE, 1_000, today.minusDays(1)),
            ),
        )
        assertEquals(Money.ZERO, creditThenLoan.payable)
        assertEquals(Money.rupees(50), creditThenLoan.credit)
    }

    @Test
    fun `debts that cancel out stop counting as borrowed`() {
        val summary = LedgerSummary.of(
            listOf(
                entry(amit, LedgerType.UDHAAR_TAKEN, 100, today.minusDays(2)),
                entry(amit, LedgerType.EXPENSE_SHARE, 100, today.minusDays(1)),
                entry(amit, LedgerType.PAYMENT_RECEIVED, 100, today),
            ),
        )

        assertEquals(Money.ZERO, summary.payable)
        assertEquals(Money.rupees(100), summary.credit)
    }

    @Test
    fun `V1 entries map to ledger types`() {
        assertEquals(LedgerType.PAYMENT_RECEIVED, LedgerType.fromV1(UdhaarDirection.GOT, null))
        assertEquals(LedgerType.EXPENSE_SHARE, LedgerType.fromV1(UdhaarDirection.GAVE, 12))
        assertEquals(LedgerType.UDHAAR_GIVEN, LedgerType.fromV1(UdhaarDirection.GAVE, null))
    }

    @Test
    fun `everyone's position, people with balances first`() {
        fun v1Entry(person: Person, direction: UdhaarDirection, rupees: Long) =
            UdhaarEntry(0, person.id, direction, Money.rupees(rupees), today, "", null, null)

        val result = balances(
            people = listOf(neha, rahul, amit),
            entries = listOf(
                v1Entry(rahul, UdhaarDirection.GAVE, 1_000),
                v1Entry(rahul, UdhaarDirection.GOT, 400),
                v1Entry(amit, UdhaarDirection.GOT, 250),
                entry(neha, LedgerType.UDHAAR_TAKEN, 300),
                entry(neha, LedgerType.PAYMENT_MADE, 300),
            ),
        )

        assertEquals(listOf(amit, rahul, neha), result.map { it.person })
        assertEquals(listOf(Money.rupees(-250), Money.rupees(600), Money.ZERO), result.map { it.balance })
        assertEquals(Money.rupees(600), result.moneyToReceive())
        assertEquals(Money.rupees(250), result.moneyToGive())
    }

    @Test
    fun `only entries up to the as-of date count`() {
        val result = balances(
            people = listOf(rahul),
            entries = listOf(
                entry(rahul, LedgerType.EXPENSE_SHARE, 450, LocalDate.of(2026, 9, 10)),
                entry(rahul, LedgerType.PAYMENT_RECEIVED, 450, LocalDate.of(2026, 10, 2)),
            ),
            asOf = LocalDate.of(2026, 9, 30),
        )

        assertEquals(Money.rupees(450), result.single().summary.receivable)
        assertEquals(LocalDate.of(2026, 9, 10), result.single().lastActivity)
    }

    @Test
    fun `people who have owed money for a while are overdue`() {
        val result = balances(
            people = listOf(rahul, amit),
            entries = listOf(
                entry(rahul, LedgerType.EXPENSE_SHARE, 450, today.minusDays(45)),
                entry(amit, LedgerType.EXPENSE_SHARE, 450, today.minusDays(5)),
            ),
        )

        assertEquals(listOf(rahul), result.overdue(today, days = 30).map { it.person })
    }

    @Test
    fun `dashboard separates card spending, cashback and effective expenses`() {
        fun expense(id: Long, rupees: Long, percentage: Percentage, method: PaymentMethod) = Expense(
            id = id,
            merchant = "Swiggy",
            paymentMethod = method,
            amounts = CashbackBreakdown.calculate(Money.rupees(rupees), percentage),
            shares = emptyList(),
            date = today,
            note = "",
        )

        val summary = SpendingSummary.of(
            listOf(expense(1, 1_000, tenPercent, PaymentMethod.CARD), expense(2, 200, Percentage.ZERO, PaymentMethod.UPI)),
        )

        assertEquals(Money.rupees(1_000), summary.cardSpending)
        assertEquals(Money.rupees(200), summary.otherSpending)
        assertEquals(Money.rupees(100), summary.cashbackReceived)
        assertEquals(Money.rupees(1_100), summary.effectiveExpenses)
        assertEquals(summary.originalExpenses - summary.cashbackReceived, summary.effectiveExpenses)
        assertEquals(2, summary.transactionCount)
    }

    @Test
    fun `my share is what is left after others' shares`() {
        val expense = Expense(
            id = 1,
            merchant = "Swiggy",
            paymentMethod = PaymentMethod.CARD,
            amounts = CashbackBreakdown.calculate(Money.rupees(900), Percentage.ZERO),
            shares = listOf(PersonShare(rahul, Money.rupees(300)), PersonShare(amit, Money.rupees(300))),
            date = today,
            note = "",
        )

        assertEquals(Money.rupees(600), expense.othersShare)
        assertEquals(Money.rupees(300), expense.myShare)
        assertEquals(listOf(rahul, amit), expense.paidFor)
        assertTrue(expense.split.isValidFor(expense.amounts.effectiveAmount))
    }

    @Test
    fun `V1 expenses where the person owed the original amount are found for review`() {
        val v1 = Expense(
            id = 1,
            merchant = "Swiggy",
            paymentMethod = PaymentMethod.CARD,
            amounts = CashbackBreakdown.calculate(Money.rupees(1_000), tenPercent),
            shares = listOf(PersonShare(rahul, Money.rupees(1_000))),
            date = today,
            note = "",
        )
        val v2 = v1.copy(id = 2, shares = listOf(PersonShare(rahul, Money.rupees(900))))
        val noCashback = v1.copy(id = 3, amounts = CashbackBreakdown.calculate(Money.rupees(1_000), Percentage.ZERO))

        val fixes = LegacyShares.find(listOf(v1, v2, noCashback))

        assertEquals(listOf(LegacyShareFix(v1, rahul, Money.rupees(1_000), Money.rupees(900))), fixes)
        assertEquals(Money.rupees(100), fixes.single().difference)
        // The V1 expense cannot be saved again until its share is corrected.
        assertTrue(v1.split.problems(v1.amounts.effectiveAmount).isNotEmpty())
    }

    @Test
    fun `a month covers its first to last day`() {
        val days = Period.Month(YearMonth.of(2026, 2)).epochDays

        assertEquals(LocalDate.of(2026, 2, 1).toEpochDay(), days.first)
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), days.last)
    }
}
