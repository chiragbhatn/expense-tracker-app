package io.github.chiragbhatn.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `settings survive a round trip through the backup`() {
        val settings = AppSettings(
            themeMode = ThemeMode.DARK,
            dynamicColor = true,
            currency = Currency.USD,
            appLock = true,
            shareTemplates = ShareTemplates(owes = "Pay {amount}, {name}"),
            reminders = ReminderSettings(enabled = false, cardDaysBefore = 5, recurringDaysBefore = 0, udhaarAfterDays = 14),
        )

        assertEquals(settings, AppSettings.fromMap(settings.toMap()))
    }

    @Test
    fun `missing or unreadable values keep their defaults`() {
        val settings = AppSettings.fromMap(
            mapOf(
                AppSettings.KEY_THEME to "PURPLE",
                AppSettings.KEY_APP_LOCK to "maybe",
                AppSettings.KEY_CARD_DAYS to "-3",
                AppSettings.KEY_SHARE_OWES to "  ",
                AppSettings.KEY_CURRENCY to "eur",
            ),
        )

        assertEquals(AppSettings(currency = Currency.EUR), settings)
        assertEquals(AppSettings(), AppSettings.fromMap(emptyMap()))
    }
}
