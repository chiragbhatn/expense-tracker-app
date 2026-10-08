package io.github.chiragbhatn.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.domain.Currency
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.MonthPoint
import io.github.chiragbhatn.expensetracker.ui.theme.LocalChartColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/** One bar of a [HorizontalBars] list. */
data class BarItem(val label: String, val value: Money, val detail: String? = null, val onClick: (() -> Unit)? = null)

/**
 * A single-series bar list for comparing amounts across named things
 * (categories, merchants, cards). Every bar takes chart slot 1; the label
 * and the exact amount are always written out, so colour is never needed to
 * read a value.
 */
@Composable
fun HorizontalBars(items: List<BarItem>, modifier: Modifier = Modifier) {
    val color = LocalChartColors.current.series[0]
    val max = items.maxOfOrNull { it.value.paise }?.coerceAtLeast(1) ?: 1
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (item.onClick != null) Modifier.clickable(onClick = item.onClick) else Modifier),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.label,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(item.value.format(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                val fraction = (item.value.paise.toFloat() / max).coerceIn(if (item.value.isPositive) 0.01f else 0f, 1f)
                Box(Modifier.fillMaxWidth().height(8.dp)) {
                    // Square at the baseline, rounded at the data end.
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(color),
                    )
                }
                item.detail?.let { Hint(it) }
            }
        }
    }
}

private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

/**
 * Spending after cashback and income per month, as grouped columns on one
 * axis. Tapping a month selects it; the selected month's figures are written
 * out below the chart, and [MonthTable] gives every value as text.
 */
@Composable
fun MonthColumns(points: List<MonthPoint>, selected: YearMonth, onSelect: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    val chart = LocalChartColors.current
    val spendingColor = chart.series[0]
    val incomeColor = chart.series[1]
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
    val measurer = rememberTextMeasurer()
    val maxValue = points.maxOfOrNull { maxOf(it.effectiveExpenses.paise, it.income.paise) }?.coerceAtLeast(1) ?: 1
    val step = niceStep(maxValue)
    val top = step * ceil(maxValue.toDouble() / step).toLong().coerceAtLeast(1)
    val description = points.joinToString("; ") {
        "${it.month.format(shortMonth)}: spending ${it.effectiveExpenses.format()}, income ${it.income.format()}"
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendKey("Spending after cashback", spendingColor)
            LegendKey("Income", incomeColor)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .semantics { contentDescription = "Monthly spending and income. $description" }
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val plotLeft = 48.dp.toPx()
                        val groupWidth = (size.width - plotLeft) / points.size.coerceAtLeast(1)
                        val index = ((offset.x - plotLeft) / groupWidth).toInt()
                        points.getOrNull(index)?.let { onSelect(it.month) }
                    }
                },
        ) {
            val plotLeft = 48.dp.toPx()
            val plotBottom = size.height - 2.dp.toPx()
            val plotTop = 8.dp.toPx()
            val plotHeight = plotBottom - plotTop
            fun y(value: Long) = plotBottom - (value.toFloat() / top) * plotHeight

            // Recessive solid hairlines at clean values, labelled in muted text.
            var tick = 0L
            while (tick <= top) {
                val ty = y(tick)
                drawLine(chart.grid, Offset(plotLeft, ty), Offset(size.width, ty), strokeWidth = 1.dp.toPx())
                val label = measurer.measure(compactMoney(Money(tick)), labelStyle)
                drawText(label, topLeft = Offset(plotLeft - label.size.width - 6.dp.toPx(), ty - label.size.height / 2f))
                tick += step
            }

            val groupWidth = (size.width - plotLeft) / points.size.coerceAtLeast(1)
            val gap = 2.dp.toPx()
            val barWidth = minOf(24.dp.toPx(), (groupWidth * 0.7f - gap) / 2f)
            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            points.forEachIndexed { index, point ->
                val center = plotLeft + groupWidth * index + groupWidth / 2f
                listOf(point.effectiveExpenses.paise to spendingColor, point.income.paise to incomeColor).forEachIndexed { series, (value, color) ->
                    if (value <= 0) return@forEachIndexed
                    val left = if (series == 0) center - gap / 2f - barWidth else center + gap / 2f
                    val barTop = y(value)
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left,
                                top = barTop,
                                right = left + barWidth,
                                bottom = plotBottom,
                                topLeftCornerRadius = radius,
                                topRightCornerRadius = radius,
                                bottomLeftCornerRadius = CornerRadius.Zero,
                                bottomRightCornerRadius = CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(path, color)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 48.dp)) {
            points.forEach { point ->
                Text(
                    text = point.month.format(shortMonth),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(point.month) },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (point.month == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (point.month == selected) MaterialTheme.colorScheme.onSurface else muted,
                )
            }
        }
    }
}

@Composable
private fun LegendKey(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The table view of [MonthColumns]: every month's figures as text. */
@Composable
fun MonthTable(points: List<MonthPoint>, selected: YearMonth, modifier: Modifier = Modifier) {
    val headerStyle = MaterialTheme.typography.labelMedium
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text("Month", Modifier.weight(1f), style = headerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf("Income", "Cashback", "Spending").forEach {
                Text(it, Modifier.weight(1.2f), style = headerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
        points.asReversed().forEach { point ->
            val weight = if (point.month == selected) FontWeight.Bold else FontWeight.Normal
            Row {
                Text(point.month.format(shortMonth) + " " + point.month.year % 100, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = weight)
                listOf(point.income, point.cashback, point.effectiveExpenses).forEach { value ->
                    Text(value.format(), Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, fontWeight = weight, textAlign = TextAlign.End)
                }
            }
        }
    }
}

/** A step of 1, 2 or 5 × 10ⁿ paise that splits [max] into at most four gridlines. */
internal fun niceStep(max: Long): Long {
    val rough = max / 4.0
    val magnitude = 10.0.pow(log10(rough.coerceAtLeast(1.0)).toInt().toDouble())
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
    return step.toLong().coerceAtLeast(100)
}

/** "₹950", "₹12K", "₹1.5L", "₹2.3Cr" (or K/M/B outside India), for axis labels. */
fun compactMoney(amount: Money, currency: Currency = Currency.display): String {
    val rupees = amount.paise / 100.0
    fun fmt(value: Double, suffix: String) = (if (value < 10) "%.1f".format(Locale.ENGLISH, value).removeSuffix(".0") else "%.0f".format(Locale.ENGLISH, value)) + suffix
    val text = if (currency.indianGrouping) {
        when {
            rupees >= 1_00_00_000 -> fmt(rupees / 1_00_00_000, "Cr")
            rupees >= 1_00_000 -> fmt(rupees / 1_00_000, "L")
            rupees >= 1_000 -> fmt(rupees / 1_000, "K")
            else -> "%.0f".format(Locale.ENGLISH, rupees)
        }
    } else {
        when {
            rupees >= 1e9 -> fmt(rupees / 1e9, "B")
            rupees >= 1e6 -> fmt(rupees / 1e6, "M")
            rupees >= 1e3 -> fmt(rupees / 1e3, "K")
            else -> "%.0f".format(Locale.ENGLISH, rupees)
        }
    }
    return currency.symbol + text
}
