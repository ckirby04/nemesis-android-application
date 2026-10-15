package io.github.ckirby04.nemesis.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ckirby04.nemesis.AppContainer
import io.github.ckirby04.nemesis.data.Source
import io.github.ckirby04.nemesis.data.db.RunSummary
import io.github.ckirby04.nemesis.ui.components.EmptyState
import io.github.ckirby04.nemesis.ui.components.SourceBadge
import io.github.ckirby04.nemesis.ui.components.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class HistoryViewModel(private val c: AppContainer, @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle) : ViewModel() {
    /** Rows hidden while their delete can still be undone. */
    private val pendingDelete = MutableStateFlow<Set<Long>>(emptySet())

    val runs = combine(c.runs.summaries, pendingDelete) { rows, hidden -> rows.filter { it.id !in hidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun hide(id: Long) { pendingDelete.value += id }
    fun undo(id: Long) { pendingDelete.value -= id }
    fun commitDelete(id: Long) = viewModelScope.launch {
        c.runs.delete(id)
        pendingDelete.value -= id
    }

    fun deleteAll() = viewModelScope.launch { c.runs.deleteAll() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenRun: (Long) -> Unit,
    onNewRun: () -> Unit,
    vm: HistoryViewModel = viewModel(factory = appViewModelFactory(::HistoryViewModel)),
) {
    val runs by vm.runs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    fun delete(id: Long) {
        vm.hide(id)
        scope.launch {
            var undone = false
            try {
                undone = snackbar.showSnackbar("Run deleted", actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed
            } finally {
                // Also runs when leaving the screen cancels the snackbar: the delete still goes through.
                if (undone) vm.undo(id) else vm.commitDelete(id)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = !runs.isNullOrEmpty()) {
                        Icon(Icons.Outlined.DeleteSweep, "Delete all runs")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = runs
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                Icons.Outlined.History,
                "No runs yet",
                "Every simulation you run is saved here with its heatmaps, report, and follow-ups.",
                Modifier.padding(padding),
            ) { Button(onClick = onNewRun) { Text("Run your first sim") } }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { run ->
                    SwipeToDelete(onDelete = { delete(run.id) }) { RunRow(run, onClick = { onOpenRun(run.id) }) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all runs?") },
            text = { Text("This removes every saved simulation, report, and follow-up. It can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmClear = false }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
    ) { content() }
}

@Composable
private fun RunRow(run: RunSummary, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(run.description, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    run.positionsCsv.replace(",", " · ") + " · ${run.handsPerCell} sims · " +
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(run.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (run.hasReport) {
                    Icon(Icons.Outlined.AutoAwesome, "Has report", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                }
                SourceBadge(Source.valueOf(run.parsedBy))
            }
        }
    }
}
