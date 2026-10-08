package io.github.chiragbhatn.expensetracker.domain

import java.util.UUID

// Stored by name, so constants must not be renamed.
enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Colours from the wallpaper on Android 12+, instead of the app's own palette. */
    val dynamicColor: Boolean = false,
    val currency: Currency = Currency.INR,
    /** Ask for the fingerprint, face or screen lock when the app opens. */
    val appLock: Boolean = false,
    val shareTemplates: ShareTemplates = ShareTemplates(),
    val reminders: ReminderSettings = ReminderSettings(),
) {
    /** Flat key/value form, used for the backup's Settings sheet. */
    fun toMap(): Map<String, String> = linkedMapOf(
        KEY_THEME to themeMode.name,
        KEY_DYNAMIC_COLOR to dynamicColor.toString(),
        KEY_CURRENCY to currency.code,
        KEY_APP_LOCK to appLock.toString(),
        KEY_SHARE_OWES to shareTemplates.owes,
        KEY_SHARE_SETTLED to shareTemplates.settled,
        KEY_SHARE_CREDIT to shareTemplates.credit,
        KEY_SHARE_YOU_OWE to shareTemplates.youOwe,
        KEY_REMINDERS to reminders.enabled.toString(),
        KEY_CARD_DAYS to reminders.cardDaysBefore.toString(),
        KEY_RECURRING_DAYS to reminders.recurringDaysBefore.toString(),
        KEY_UDHAAR_DAYS to reminders.udhaarAfterDays.toString(),
    )

    companion object {
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_CURRENCY = "currency"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_SHARE_OWES = "share_template_owes"
        const val KEY_SHARE_SETTLED = "share_template_settled"
        const val KEY_SHARE_CREDIT = "share_template_credit"
        const val KEY_SHARE_YOU_OWE = "share_template_you_owe"
        const val KEY_REMINDERS = "reminders_enabled"
        const val KEY_CARD_DAYS = "reminder_card_days_before"
        const val KEY_RECURRING_DAYS = "reminder_recurring_days_before"
        const val KEY_UDHAAR_DAYS = "reminder_udhaar_after_days"

        /** Reads settings back; missing or unreadable values keep their defaults. */
        fun fromMap(values: Map<String, String>): AppSettings {
            val defaults = AppSettings()
            fun text(key: String, default: String) = values[key]?.takeIf { it.isNotBlank() } ?: default
            fun flag(key: String, default: Boolean) = values[key]?.trim()?.lowercase()?.toBooleanStrictOrNull() ?: default
            fun days(key: String, default: Int) = values[key]?.trim()?.toIntOrNull()?.takeIf { it in 0..365 } ?: default
            return AppSettings(
                themeMode = ThemeMode.entries.firstOrNull { it.name == values[KEY_THEME]?.trim() } ?: defaults.themeMode,
                dynamicColor = flag(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
                currency = values[KEY_CURRENCY]?.let(Currency::fromCode) ?: defaults.currency,
                appLock = flag(KEY_APP_LOCK, defaults.appLock),
                shareTemplates = ShareTemplates(
                    owes = text(KEY_SHARE_OWES, ShareTemplates.DEFAULT_OWES),
                    settled = text(KEY_SHARE_SETTLED, ShareTemplates.DEFAULT_SETTLED),
                    credit = text(KEY_SHARE_CREDIT, ShareTemplates.DEFAULT_CREDIT),
                    youOwe = text(KEY_SHARE_YOU_OWE, ShareTemplates.DEFAULT_YOU_OWE),
                ),
                reminders = ReminderSettings(
                    enabled = flag(KEY_REMINDERS, defaults.reminders.enabled),
                    cardDaysBefore = days(KEY_CARD_DAYS, defaults.reminders.cardDaysBefore),
                    recurringDaysBefore = days(KEY_RECURRING_DAYS, defaults.reminders.recurringDaysBefore),
                    udhaarAfterDays = days(KEY_UDHAAR_DAYS, defaults.reminders.udhaarAfterDays),
                ),
            )
        }
    }
}

/** A new random identifier that stays with a record across devices, backups and restores. */
fun newUuid(): String = UUID.randomUUID().toString()

/**
 * Expenses saved by V1, where a person owed the full original amount even
 * when the card gave cashback. V2 charges them the effective amount instead.
 * Nothing changes until the user reviews and applies these.
 */
data class LegacyShareFix(
    val expense: Expense,
    val person: Person,
    val oldShare: Money,
    val newShare: Money,
) {
    val difference: Money get() = oldShare - newShare
}

object LegacyShares {
    fun find(expenses: List<Expense>): List<LegacyShareFix> =
        expenses
            .filter { it.amounts.hasCashback && it.shares.size == 1 && it.shares.single().amount == it.amounts.originalAmount }
            .map { LegacyShareFix(it, it.shares.single().person, it.amounts.originalAmount, it.amounts.effectiveAmount) }
            .sortedWith(compareByDescending<LegacyShareFix> { it.expense.date }.thenByDescending { it.expense.id })
}
