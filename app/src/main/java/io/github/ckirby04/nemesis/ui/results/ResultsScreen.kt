package io.github.ckirby04.nemesis.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import io.github.ckirby04.nemesis.AppContainer
import io.github.ckirby04.nemesis.data.SavedRun
import io.github.ckirby04.nemesis.engine.Cell
import io.github.ckirby04.nemesis.engine.Positions
import io.github.ckirby04.nemesis.ui.ResultsRoute
import io.github.ckirby04.nemesis.ui.components.HeatmapLegend
import io.github.ckirby04.nemesis.ui.components.RangeHeatmap
import io.github.ckirby04.nemesis.ui.components.SectionCard
import io.github.ckirby04.nemesis.ui.components.SourceBadge
import io.github.ckirby04.nemesis.ui.components.VillainStats
import io.github.ckirby04.nemesis.ui.components.appViewModelFactory
import io.github.ckirby04.nemesis.ui.components.evColor
import io.github.ckirby04.nemesis.ui.components.evScaleBound
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class ResultsViewModel(c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val runId = handle.toRoute<ResultsRoute>().runId
    val run = c.runs.observe(runId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private enum class RankView(val label: String) { BEST("Best opens"), WORST("Worst opens"), NOISY("Too close to call") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    onOpenReport: (Long) -> Unit,
    vm: ResultsViewModel = viewModel(factory = appViewModelFactory(::ResultsViewModel)),
) {
    val run by vm.run.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Results") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            if (run != null) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenReport(vm.runId) },
                    icon = { Icon(Icons.Outlined.AutoAwesome, null) },
                    text = { Text(if (run?.report == null) "Advisor report" else "View report") },
                )
            }
        },
    ) { padding ->
        val r = run
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            ResultsContent(r, PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp))
        }
    }
}

@Composable
private fun ResultsContent(run: SavedRun, padding: PaddingValues) {
    var pos by rememberSaveable(run.id) { mutableStateOf(run.positions.first()) }
    var selected by rememberSaveable(run.id) { mutableStateOf<String?>("AA") }
    var rankView by rememberSaveable { mutableStateOf(RankView.BEST) }
    val cells = run.results[pos].orEmpty()
    val bound = remember(cells) { evScaleBound(cells.values) }
    val scale = Positions.evScale(pos, run.seats)
    val ranked = remember(cells, rankView) {
        val sorted = cells.entries.sortedByDescending { it.value.mean }
        when (rankView) {
            RankView.BEST -> sorted.filter { !it.value.crossesZero }.take(15)
            RankView.WORST -> sorted.reversed().filter { !it.value.crossesZero }.take(15)
            RankView.NOISY -> sorted.filter { it.value.crossesZero }.take(15)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = "Villain model") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(run.villain.describe().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    SourceBadge(run.parsedBy)
                }
                Text("“${run.description}”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VillainStats(run.villain)
                if (run.parseNote != null) {
                    Text("Read offline: ${run.parseNote}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (run.seatReads.isNotEmpty()) {
                    Text(
                        "Seat reads: " + run.seatReads.entries.joinToString(" · ") { (p, v) -> "$p ${v.describe()}" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "${run.handsPerCell} sims/hand · ${run.seats}-handed · " +
                        (if (run.rake.enabled) String.format(Locale.US, "rake %.0f%% cap %.0fbb", run.rake.pct * 100, run.rake.capBb) else "no rake") +
                        " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(run.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (run.positions.size > 1) {
            item {
                if (run.positions.size <= 4) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        run.positions.forEachIndexed { i, p ->
                            SegmentedButton(
                                selected = p == pos,
                                onClick = { pos = p },
                                shape = SegmentedButtonDefaults.itemShape(i, run.positions.size),
                            ) { Text(p) }
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(run.positions) { p -> FilterChip(selected = p == pos, onClick = { pos = p }, label = { Text(p) }) }
                    }
                }
            }
        }

        item {
            SectionCard(title = "EV of opening each hand from $pos") {
                RangeHeatmap(cells = cells, selected = selected, onSelect = { selected = it })
                HeatmapLegend(bound)
                if (cells.size < 169) {
                    Text(
                        "Grey cells weren't simulated (41-hand sample). Turn on the full grid in Settings to fill them in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            val hand = selected
            val cell = hand?.let { cells[it] }
            SectionCard(title = "Selected hand") {
                if (hand == null) {
                    Text("Tap a cell in the grid.", style = MaterialTheme.typography.bodyMedium)
                } else if (cell == null) {
                    Text("$hand wasn't simulated in this run.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    CellDetail(hand, cell, bound, scale)
                }
            }
        }

        item {
            SectionCard(title = "Ranked hands · $pos") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RankView.entries) { v -> FilterChip(selected = v == rankView, onClick = { rankView = v }, label = { Text(v.label) }) }
                }
                if (ranked.isEmpty()) {
                    Text("Nothing in this bucket.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ranked.forEachIndexed { i, (h, c) ->
                    RankRow(i + 1, h, c, bound, scale, onClick = { selected = h })
                }
            }
        }

        item {
            Text(
                "Average big blinds won each time you open the hand (0 = same as folding). Results are directional, not exact bb/100 — trust the ordering more than the magnitudes. A study tool for between sessions, not for use at the table.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun signed(x: Double, d: Int = 1) = String.format(Locale.US, "%+.${d}f", x)

@Composable
private fun CellDetail(hand: String, cell: Cell, bound: Double, scale: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val swatch = evColor(cell.mean, bound)
        Surface(shape = RoundedCornerShape(12.dp), color = swatch, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(hand, style = MaterialTheme.typography.titleMedium, color = if (swatch.luminance() > 0.45f) Color(0xFF15191C) else Color.White)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("${signed(cell.mean)} bb per open", style = MaterialTheme.typography.titleLarge)
            Text(
                "95% CI ${signed(cell.ciLow)} to ${signed(cell.ciHigh)} · ${cell.n} sims",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Verdict: ${cell.verdict(scale)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (cell.crossesZero) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Too close to call: the interval crosses zero, so the sign may be noise. Raise sims/hand in Settings to tighten it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RankRow(rank: Int, hand: String, cell: Cell, bound: Double, scale: Double, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = evColor(cell.mean, bound), modifier = Modifier.size(width = 6.dp, height = 22.dp)) {}
            Spacer(Modifier.width(10.dp))
            Text(hand, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(52.dp))
            Text(cell.verdict(scale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("${signed(cell.mean)} bb", style = MaterialTheme.typography.labelLarge)
                Text("± ${String.format(Locale.US, "%.1f", cell.halfCi)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
