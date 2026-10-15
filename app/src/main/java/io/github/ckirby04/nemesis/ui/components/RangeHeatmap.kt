package io.github.ckirby04.nemesis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.ckirby04.nemesis.engine.Cell
import io.github.ckirby04.nemesis.engine.Hands
import io.github.ckirby04.nemesis.ui.theme.EvScale
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Symmetric color scale for a set of cells. Uses the 95th percentile of |EV| rather than the max,
 * so a few premiums at +40bb don't wash every other cell out to yellow.
 */
fun evScaleBound(cells: Collection<Cell>): Double {
    val mags = cells.map { abs(it.mean) }.sorted()
    if (mags.isEmpty()) return 1.0
    return max(1.0, mags[((mags.size - 1) * 0.95).toInt()])
}

/** RdYlGn color for [ev] on a symmetric ±[bound] scale. */
fun evColor(ev: Double, bound: Double): Color {
    val t = ((ev / bound).coerceIn(-1.0, 1.0) + 1) / 2 * (EvScale.size - 1)
    val i = floor(t).toInt().coerceAtMost(EvScale.size - 2)
    return lerp(EvScale[i], EvScale[i + 1], (t - i).toFloat())
}

/**
 * The 13x13 starting-hand grid (pairs on the diagonal, suited above, offsuit below), colored by EV.
 * Cells whose 95% CI crosses zero are dimmed and hatched — the same noise flag as the CLI/dashboard.
 */
@Composable
fun RangeHeatmap(
    cells: Map<String, Cell>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val bound = remember(cells) { evScaleBound(cells.values) }
    val missing = MaterialTheme.colorScheme.surfaceVariant
    val missingText = MaterialTheme.colorScheme.onSurfaceVariant
    val selection = MaterialTheme.colorScheme.onBackground
    // Hatched cells are dimmed toward the card, so their labels follow the theme's text color.
    val noisyText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val side = maxWidth
        Canvas(
            Modifier
                .size(side)
                .semantics { contentDescription = "13 by 13 starting hand heatmap. Tap a cell for details." }
                .pointerInput(cells) {
                    detectTapGestures { o ->
                        val cell = size.width / 13f
                        val col = (o.x / cell).toInt().coerceIn(0, 12)
                        val row = (o.y / cell).toInt().coerceIn(0, 12)
                        onSelect(Hands.gridHand(row, col))
                    }
                },
        ) {
            val cell = size.width / 13f
            val gap = 1.5.dp.toPx()
            val radius = CornerRadius(3.dp.toPx())
            val labelStyle = TextStyle(fontSize = (cell * 0.30f).toSp(), fontWeight = FontWeight.Medium)

            for (row in 0 until 13) for (col in 0 until 13) {
                val hand = Hands.gridHand(row, col)
                val c = cells[hand]
                val topLeft = Offset(col * cell + gap / 2, row * cell + gap / 2)
                val sz = Size(cell - gap, cell - gap)
                val fill = c?.let { evColor(it.mean, bound) } ?: missing
                val noisy = c?.crossesZero == true

                drawRoundRect(fill.copy(alpha = if (noisy) 0.55f else 1f), topLeft, sz, radius)
                if (noisy) hatch(topLeft, sz)
                if (hand == selected) drawRoundRect(selection, topLeft, sz, radius, style = Stroke(2.dp.toPx()))

                val textColor = when {
                    c == null -> missingText
                    noisy -> noisyText
                    fill.luminance() > 0.45f -> Color(0xFF15191C)
                    else -> Color.White
                }
                val layout = measurer.measure(hand, labelStyle.copy(color = textColor))
                drawText(
                    layout,
                    topLeft = Offset(
                        topLeft.x + (sz.width - layout.size.width) / 2,
                        topLeft.y + (sz.height - layout.size.height) / 2,
                    ),
                )
            }
        }
    }
}

private fun DrawScope.hatch(topLeft: Offset, size: Size) {
    val step = 5.dp.toPx()
    val stroke = 1.2.dp.toPx()
    clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
        var x = topLeft.x - size.height
        while (x < topLeft.x + size.width) {
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, topLeft.y + size.height), Offset(x + size.height, topLeft.y), stroke)
            x += step
        }
    }
}

/** Gradient legend for [bound] plus the noise-hatch key. */
@Composable
fun HeatmapLegend(bound: Double, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(EvScale)),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val b = String.format(Locale.US, "%.0f", bound)
            Text("−$b bb", style = MaterialTheme.typography.labelSmall, color = muted)
            Text("0", style = MaterialTheme.typography.labelSmall, color = muted)
            Text("+$b bb", style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(14.dp)) {
                drawRoundRect(EvScale[4].copy(alpha = 0.55f), cornerRadius = CornerRadius(2.dp.toPx()))
                hatch(Offset.Zero, size)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Hatched = 95% CI crosses 0 (noise — don't trust the sign)",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
    }
}
