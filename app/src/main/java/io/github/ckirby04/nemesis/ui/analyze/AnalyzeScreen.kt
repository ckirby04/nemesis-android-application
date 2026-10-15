package io.github.ckirby04.nemesis.ui.analyze

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ckirby04.nemesis.AppContainer
import io.github.ckirby04.nemesis.data.AppSettings
import io.github.ckirby04.nemesis.data.SimState
import io.github.ckirby04.nemesis.data.Source
import io.github.ckirby04.nemesis.engine.Villain
import io.github.ckirby04.nemesis.parsing.TableState
import io.github.ckirby04.nemesis.ui.components.POSITION_ORDER
import io.github.ckirby04.nemesis.ui.components.SectionCard
import io.github.ckirby04.nemesis.ui.components.SourceBadge
import io.github.ckirby04.nemesis.ui.components.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class AnalyzeViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    val description = handle.getStateFlow(KEY_DESC, "")
    val useTableReads = handle.getStateFlow(KEY_TABLE, true)
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val table = c.tables.table.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TableState(Villain()))
    val sim = c.simulation.state

    fun setDescription(s: String) { handle[KEY_DESC] = s }
    fun setUseTableReads(on: Boolean) { handle[KEY_TABLE] = on }

    fun togglePosition(pos: String) = viewModelScope.launch {
        val cur = settings.value.positions
        val next = if (pos in cur) cur - pos else cur + pos
        if (next.isNotEmpty()) c.settings.setPositions(next)
    }

    fun run() {
        val labels = table.value.labels
        val positions = POSITION_ORDER.filter { it in settings.value.positions && it in labels }
        if (description.value.isBlank() || positions.isEmpty()) return
        c.simulation.start(description.value.trim(), positions, useTableReads.value && table.value.seats.isNotEmpty())
    }

    fun cancel() = c.simulation.cancel()
    fun dismissError() = c.simulation.consume()

    private companion object {
        const val KEY_DESC = "description"
        const val KEY_TABLE = "use_table"
    }
}

private val EXAMPLES = listOf(
    "Loose & sticky" to "People raise light to 20 with KJo-type hands, call light, can't fold top pair, 100bb deep",
    "Nitty regs" to "Tight passive rocks, open 5bb, fold to aggression, 150bb deep",
    "Aggro maniacs" to "Aggressive maniacs, 3-bet a lot, bluff rivers, open 8bb, 200bb deep",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalyzeScreen(
    onOpenSettings: () -> Unit,
    onOpenTable: () -> Unit,
    vm: AnalyzeViewModel = viewModel(factory = appViewModelFactory(::AnalyzeViewModel)),
) {
    val description by vm.description.collectAsStateWithLifecycle()
    val useTable by vm.useTableReads.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val table by vm.table.collectAsStateWithLifecycle()
    val sim by vm.sim.collectAsStateWithLifecycle()
    val busy = sim is SimState.Parsing || sim is SimState.Running
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Ask once for notification permission (Android 13+) so the progress notification shows;
    // the run starts whatever the answer.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.run() }
    val startRun = {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.run()
    }

    LaunchedEffect(sim) {
        (sim as? SimState.Failed)?.let {
            snackbar.showSnackbar("Simulation failed: ${it.message}")
            vm.dismissError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nemesis") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Describe your table in plain English. Nemesis models the pool and simulates which hands make money against it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(title = "Your table") {
                OutlinedTextField(
                    value = description,
                    onValueChange = vm::setDescription,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("e.g. loose, raises 20bb light, sticky stations, 100bb deep") },
                    enabled = !busy,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXAMPLES.forEach { (label, text) ->
                        SuggestionChip(onClick = { vm.setDescription(text) }, label = { Text(label) }, enabled = !busy)
                    }
                }
            }

            SectionCard(title = "Positions to simulate") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    POSITION_ORDER.filter { it in table.labels }.forEach { pos ->
                        FilterChip(
                            selected = pos in settings.positions,
                            onClick = { vm.togglePosition(pos) },
                            label = { Text(pos) },
                            enabled = !busy,
                        )
                    }
                }
                Text(
                    "The first selected position (late seats first) is the advisor's reference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Live table reads") {
                if (table.seats.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "No seat reads yet. Add notes on specific players to model them individually.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onOpenTable) { Text("Open table") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Use ${table.seats.size} seat read${if (table.seats.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                table.seats.keys.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = useTable, onCheckedChange = vm::setUseTableReads, enabled = !busy)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append("${settings.handsPerCell} sims/hand · ")
                        append(if (settings.fullGrid) "full 169-hand grid" else "41-hand sample")
                        append(" · ")
                        append(if (settings.rake.enabled) String.format(Locale.US, "rake %.0f%%, cap %.0fbb", settings.rake.pct * 100, settings.rake.capBb) else "no rake")
                        append(" · ${table.nSeats}-handed")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenSettings) { Text("Change") }
            }

            AnimatedContent(targetState = sim, contentKey = { it::class }, label = "run") { s ->
                when (s) {
                    is SimState.Parsing -> RunProgress("Reading your table…", null, null, vm::cancel)
                    is SimState.Running -> RunProgress("Simulating · ${s.villainTag}", s.progress, s.source, vm::cancel)
                    else -> Button(
                        onClick = startRun,
                        enabled = description.isNotBlank() && POSITION_ORDER.any { it in settings.positions && it in table.labels },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run simulation")
                    }
                }
            }

            if (!settings.claudeEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Offline mode: descriptions are read with keyword heuristics. Add an Anthropic API key for Claude-powered reads and reports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenSettings) {
                        Text("Add key")
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RunProgress(label: String, progress: Float?, source: Source?, onCancel: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (source != null) SourceBadge(source)
        }
        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (progress == null) "Turning your description into a villain model" else "${(progress * 100).toInt()}% of hands simulated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
