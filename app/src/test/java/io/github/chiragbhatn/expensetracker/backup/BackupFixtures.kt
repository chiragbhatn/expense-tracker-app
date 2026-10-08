package io.github.chiragbhatn.expensetracker.backup

import io.github.chiragbhatn.expensetracker.domain.AppSettings
import io.github.chiragbhatn.expensetracker.domain.CashbackBreakdown
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.DefaultCategories
import io.github.chiragbhatn.expensetracker.domain.Frequency
import io.github.chiragbhatn.expensetracker.domain.IntervalUnit
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.PaymentMethod
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.ThemeMode
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import java.time.LocalDate

/** A small but complete data set that touches every table and tricky value. */
object BackupFixtures {
    const val T0 = 1_790_000_000_000L

    fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    val rahul = PersonRecord("p-rahul", "Rahul", phone = "98765 43210", tags = listOf("college", "flatmate"), createdAt = T0, updatedAt = T0 + 1)
    val amit = PersonRecord("p-amit", "Amit", email = "amit@example.com", createdAt = T0, updatedAt = T0)
    val neha = PersonRecord("p-neha", "Neha", address = "12, MG Road\nBengaluru", notes = "Says \"hi\", always late", createdAt = T0, updatedAt = T0)

    val hdfc = CardRecord("c-hdfc", "Millennia", "HDFC", "1234", Money.rupees(1_00_000), 20, 5, "Main card", true, 3, T0, T0)
    val sbi = CardRecord("c-sbi", "SimplyClick", "SBI", "0042", Money.rupees(50_000), 1, 21, "", false, null, T0, T0)

    val netflix = RecurringRecord(
        "r-netflix", "Netflix", Money.rupees(649), "Subscription", PaymentMethod.CARD, "c-hdfc", date(1, 15), Frequency.MONTHLY, 1, IntervalUnit.MONTHS,
        date(10, 15), 9, null, true, 2, "", T0, T0,
    )
    val rent = RecurringRecord(
        "r-rent", "Rent", Money.rupees(20_000), "Rent", PaymentMethod.UPI, null, date(1, 1), Frequency.CUSTOM, 1, IntervalUnit.MONTHS,
        date(11, 1), 10, date(12, 31), true, null, "Flat 4B", T0, T0,
    )

    private fun expense(
        uuid: String,
        date: LocalDate,
        merchant: String,
        paise: Long,
        bps: Int,
        method: PaymentMethod,
        card: String?,
        category: String = "Food",
        note: String = "",
        recurring: String? = null,
    ): ExpenseRecord {
        val amounts = CashbackBreakdown.calculate(Money(paise), Percentage(bps))
        return ExpenseRecord(uuid, date, merchant, category, method, card, amounts.originalAmount, amounts.cashbackPercentage, amounts.cashbackAmount, amounts.effectiveAmount, note, recurring, T0, T0 + 5)
    }

    val swiggyForRahul = expense("e-1", date(10, 3), "Swiggy", 200_00, 1_000, PaymentMethod.CARD, "c-hdfc")
    val swiggySplit = expense("e-2", date(10, 5), "Swiggy", 1_000_00, 1_000, PaymentMethod.CARD, "c-hdfc", note = "Team dinner, Friday")
    val dmart = expense("e-3", date(10, 6), "DMart", 1_234_56, 0, PaymentMethod.UPI, null, category = "Groceries", note = "=SUM(A1)")
    val legacy = expense("e-4", date(9, 10), "Swiggy", 1_000_00, 1_000, PaymentMethod.CARD, null)
    val netflixCharge = expense("e-5", date(9, 15), "Netflix", 649_00, 0, PaymentMethod.CARD, "c-hdfc", category = "Subscription", recurring = "r-netflix")

    private fun entry(uuid: String, person: String, date: LocalDate, type: LedgerType, paise: Long, expense: String? = null, direction: UdhaarDirection? = null, note: String = "") =
        LedgerRecord(uuid, person, date, type, direction ?: type.fixedDirection!!, Money(paise), expense, note, T0, T0 + 2)

    val data = BackupData(
        settings = AppSettings(themeMode = ThemeMode.DARK, appLock = true).toMap(),
        people = listOf(rahul, amit, neha),
        categories = DefaultCategories.expense.mapIndexed { i, name -> CategoryRecord("cat-e$i", name, CategoryKind.EXPENSE, true, T0, T0) } +
            DefaultCategories.income.mapIndexed { i, name -> CategoryRecord("cat-i$i", name, CategoryKind.INCOME, true, T0, T0) } +
            CategoryRecord("cat-pets", "Pets", CategoryKind.EXPENSE, false, T0, T0),
        cards = listOf(hdfc, sbi),
        cashbackRules = listOf(
            CashbackRuleRecord("rule-swiggy", "Swiggy", Percentage(1_000), true, null, T0, T0),
            CashbackRuleRecord("rule-amazon", "Amazon", Percentage(500), true, "c-hdfc", T0, T0),
            CashbackRuleRecord("rule-zomato", "Zomato", Percentage(1_050), false, null, T0, T0),
        ),
        expenses = listOf(swiggyForRahul, swiggySplit, dmart, legacy, netflixCharge),
        incomes = listOf(
            IncomeRecord("i-1", date(10, 1), "Salary", "Salary", Money.rupees(60_000), "", T0, T0),
            IncomeRecord("i-2", date(10, 2), "Client, Inc.", "Freelance", Money(5_000_50), "Invoice #12", T0, T0),
        ),
        ledger = listOf(
            entry("l-1", "p-rahul", date(10, 3), LedgerType.EXPENSE_SHARE, 180_00, "e-1"),
            entry("l-2", "p-rahul", date(10, 5), LedgerType.EXPENSE_SHARE, 300_00, "e-2"),
            entry("l-3", "p-amit", date(10, 5), LedgerType.EXPENSE_SHARE, 300_00, "e-2"),
            entry("l-4", "p-neha", date(9, 10), LedgerType.EXPENSE_SHARE, 1_000_00, "e-4"),
            entry("l-5", "p-rahul", date(10, 7), LedgerType.PAYMENT_RECEIVED, 300_00, note = "GPay"),
            entry("l-6", "p-amit", date(10, 7), LedgerType.SETTLEMENT, 300_00, direction = UdhaarDirection.GOT),
            entry("l-7", "p-neha", date(9, 20), LedgerType.UDHAAR_TAKEN, 500_00),
            entry("l-8", "p-neha", date(9, 25), LedgerType.PAYMENT_MADE, 200_00),
            entry("l-9", "p-rahul", date(10, 8), LedgerType.ADJUSTMENT, 20_00, direction = UdhaarDirection.GAVE, note = "rounding"),
        ),
        cardPayments = listOf(CardPaymentRecord("cp-1", "c-hdfc", date(9, 25), Money.rupees(500), "", T0, T0)),
        recurring = listOf(netflix, rent),
        reminders = listOf(ReminderRecord("rem-1", "Ask Rahul for trip money", "", date(10, 10), "p-rahul", false, T0, T0)),
        attachments = listOf(AttachmentRecord("a-1", "e-3", null, "a-1.jpg", "image/jpeg", 123_456, T0, T0)),
    )

    /** Every list sorted by uuid, so data sets can be compared regardless of order. */
    fun BackupData.normalized() = copy(
        people = people.sortedBy { it.uuid },
        categories = categories.sortedBy { it.uuid },
        cards = cards.sortedBy { it.uuid },
        cashbackRules = cashbackRules.sortedBy { it.uuid },
        expenses = expenses.sortedBy { it.uuid },
        incomes = incomes.sortedBy { it.uuid },
        ledger = ledger.sortedBy { it.uuid },
        cardPayments = cardPayments.sortedBy { it.uuid },
        recurring = recurring.sortedBy { it.uuid },
        reminders = reminders.sortedBy { it.uuid },
        attachments = attachments.sortedBy { it.uuid },
    )
}
