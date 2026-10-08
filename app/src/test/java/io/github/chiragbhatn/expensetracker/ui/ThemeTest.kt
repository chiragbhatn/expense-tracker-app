package io.github.chiragbhatn.expensetracker.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chiragbhatn.expensetracker.domain.ThemeMode
import io.github.chiragbhatn.expensetracker.ui.theme.AmountColors
import io.github.chiragbhatn.expensetracker.ui.theme.DarkAmountColors
import io.github.chiragbhatn.expensetracker.ui.theme.DarkChartColors
import io.github.chiragbhatn.expensetracker.ui.theme.DarkColors
import io.github.chiragbhatn.expensetracker.ui.theme.ExpenseTrackerTheme
import io.github.chiragbhatn.expensetracker.ui.theme.LightAmountColors
import io.github.chiragbhatn.expensetracker.ui.theme.LightChartColors
import io.github.chiragbhatn.expensetracker.ui.theme.LightColors
import io.github.chiragbhatn.expensetracker.ui.theme.LocalAmountColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ThemeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun schemeFor(mode: ThemeMode): Pair<ColorScheme, AmountColors> {
        var scheme: ColorScheme? = null
        var amounts: AmountColors? = null
        compose.setContent {
            ExpenseTrackerTheme(themeMode = mode) {
                scheme = MaterialTheme.colorScheme
                amounts = LocalAmountColors.current
            }
        }
        compose.waitForIdle()
        return scheme!! to amounts!!
    }

    @Test
    fun darkSettingUsesTheDarkPaletteEverywhere() {
        val (scheme, amounts) = schemeFor(ThemeMode.DARK)
        assertEquals(DarkColors.background, scheme.background)
        assertEquals(DarkColors.surfaceContainerHighest, scheme.surfaceContainerHighest)
        assertEquals(DarkAmountColors, amounts)
    }

    @Test
    fun lightSettingUsesTheLightPalette() {
        val (scheme, amounts) = schemeFor(ThemeMode.LIGHT)
        assertEquals(LightColors.background, scheme.background)
        assertEquals(LightAmountColors, amounts)
    }

    @Test
    @Config(qualifiers = "night")
    fun systemSettingFollowsThePhone() {
        assertEquals(DarkColors.background, schemeFor(ThemeMode.SYSTEM).first.background)
    }

    @Test
    fun textIsReadableOnEverySurface() {
        for ((scheme, amounts) in listOf(LightColors to LightAmountColors, DarkColors to DarkAmountColors)) {
            val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
            for (surface in surfaces) {
                for (text in listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, scheme.error, amounts.positive, amounts.negative, amounts.credit)) {
                    assertTrue("contrast ${contrast(text, surface)} for $text on $surface", contrast(text, surface) >= 4.5)
                }
            }
            listOf(
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onErrorContainer to scheme.errorContainer,
                scheme.onError to scheme.error,
            ).forEach { (text, background) -> assertTrue("contrast for $text on $background", contrast(text, background) >= 4.5) }
        }
    }

    @Test
    fun chartMarksStandOutFromCards() {
        listOf(LightChartColors to LightColors, DarkChartColors to DarkColors).forEach { (chart, scheme) ->
            chart.series.forEach { color ->
                assertTrue("chart colour $color on cards", contrast(color, scheme.surfaceContainerHighest) >= 3.0)
                assertTrue("chart colour $color on the page", contrast(color, scheme.background) >= 3.0)
            }
        }
    }
}
