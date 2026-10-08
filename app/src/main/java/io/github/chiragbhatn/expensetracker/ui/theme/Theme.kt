package io.github.chiragbhatn.expensetracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.chiragbhatn.expensetracker.domain.ThemeMode

// A full teal palette for each mode, so every surface, container and text
// colour is defined here rather than borrowed from the default (purple) scheme.
internal val LightColors = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF2E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF3F5F78),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCAE6FF),
    onTertiaryContainer = Color(0xFF001E30),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFB),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFAFDFB),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    inverseSurface = Color(0xFF2D3130),
    inverseOnSurface = Color(0xFFEFF1EF),
    inversePrimary = Color(0xFF82D5C8),
    surfaceTint = Color(0xFF006A60),
    surfaceBright = Color(0xFFFAFDFB),
    surfaceDim = Color(0xFFD8DBD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F7F5),
    surfaceContainer = Color(0xFFEEF1EF),
    surfaceContainerHigh = Color(0xFFE8ECEA),
    surfaceContainerHighest = Color(0xFFE2E6E4),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9EF2E4),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFA7CAE8),
    onTertiary = Color(0xFF0B344C),
    tertiaryContainer = Color(0xFF274B64),
    onTertiaryContainer = Color(0xFFCAE6FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    inverseSurface = Color(0xFFE0E3E1),
    inverseOnSurface = Color(0xFF2D3130),
    inversePrimary = Color(0xFF006A60),
    surfaceTint = Color(0xFF82D5C8),
    surfaceBright = Color(0xFF363A39),
    surfaceDim = Color(0xFF101413),
    surfaceContainerLowest = Color(0xFF0B0F0E),
    surfaceContainerLow = Color(0xFF191C1B),
    surfaceContainer = Color(0xFF1D2120),
    surfaceContainerHigh = Color(0xFF272B2A),
    surfaceContainerHighest = Color(0xFF323635),
)

/**
 * Colours for money: [positive] for cashback and money coming to you,
 * [negative] for money you owe or gave, [credit] for credit someone holds.
 */
@Immutable
data class AmountColors(val positive: Color, val negative: Color, val credit: Color)

internal val LightAmountColors = AmountColors(positive = Color(0xFF176E33), negative = Color(0xFFBA1A1A), credit = Color(0xFF3F5F78))
internal val DarkAmountColors = AmountColors(positive = Color(0xFF7BDA8F), negative = Color(0xFFFFB4AB), credit = Color(0xFFA7CAE8))

val LocalAmountColors = staticCompositionLocalOf { LightAmountColors }

/**
 * Chart colours. [series] is a fixed-order categorical palette (slot 1 first,
 * never cycled) checked for colour-blind separation, chroma, lightness and
 * contrast against the card surface of each mode; [grid] is for hairlines.
 */
@Immutable
data class ChartColors(val series: List<Color>, val grid: Color)

internal val LightChartColors = ChartColors(series = listOf(Color(0xFF00897B), Color(0xFFE65100)), grid = Color(0xFFBEC9C6))
internal val DarkChartColors = ChartColors(series = listOf(Color(0xFF26A69A), Color(0xFFD95926)), grid = Color(0xFF3F4947))

val LocalChartColors = staticCompositionLocalOf { LightChartColors }

/** Whether the app is showing its dark theme, given the setting and the system's choice. */
@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ExpenseTrackerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = isAppInDarkTheme(themeMode)
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalAmountColors provides if (darkTheme) DarkAmountColors else LightAmountColors,
        LocalChartColors provides if (darkTheme) DarkChartColors else LightChartColors,
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
