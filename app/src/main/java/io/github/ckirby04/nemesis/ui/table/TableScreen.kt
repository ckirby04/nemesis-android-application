package io.github.ckirby04.nemesis.ui.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ckirby04.nemesis.AppContainer
import io.github.ckirby04.nemesis.data.Source
import io.github.ckirby04.nemesis.engine.Positions
import io.github.ckirby04.nemesis.engine.Villain
import io.github.ckirby04.nemesis.parsing.Archetypes
import io.github.ckirby04.nemesis.parsing.TableState
import io.github.ckirby04.nemesis.ui.components.SectionCard
import io.github.ckirby04.nemesis.ui.components.VillainStats
import io.github.ckirby04.nemesis.ui.components.appViewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class TableViewModel(private val c: AppContainer, @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle) : ViewModel() {
    val table = c.tables.table.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TableState(Villain()))
    private val _applying = MutableStateFlow(false)
    val applying = _applying.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun addNote(text: String) {
        if (text.isBlank() || _applying.value) return
        viewModelScope.launch {
            _applying.value = true
            try {
                val r = c.tables.applyNote(text)
                val via = if (r.source == Source.CLAUDE) "Claude" else "offline"
                val hero = c.tables.current().heroPos
                _messages.send(
                    if (r.seats.isEmpty() && Positions.canonical(r.update.target) == hero) "$hero is your seat — name an opponent's seat instead."
                    else if (r.seats.isEmpty()) "Couldn't tell which seat “${r.update.target}” means — try a position like CO or “my left”."
                    else "Updated ${r.seats.joinToString(", ")} ($via)" + (r.update.archetype?.let { ": $it" } ?: ""),
                )
            } finally {
                _applying.value = false
            }
        }
    }

    fun setHero(pos: String) = viewModelScope.launch { c.tables.setHeroPos(pos) }
    fun setSeatCount(n: Int) = viewModelScope.launch { c.tables.setSeatCount(n) }
    fun setArchetype(pos: String, archetype: String) = viewModelScope.launch { c.tables.setArchetype(pos, archetype) }
    fun clearSeat(pos: String) = viewModelScope.launch { c.tables.clearSeat(pos) }
    fun reset() = viewModelScope.launch { c.tables.reset() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TableScreen(vm: TableViewModel = viewModel(factory = appViewModelFactory(::TableViewModel))) {
    val table by vm.table.collectAsStateWithLifecycle()
    val applying by vm.applying.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var note by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live table") },
                actions = {
                    IconButton(onClick = { confirmReset = true }, enabled = table.seats.isNotEmpty()) {
                        Icon(Icons.Outlined.RestartAlt, "Clear all reads")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.testTag("table_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Add a read") {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. the guy on my left calls everything") },
                        enabled = !applying,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.addNote(note); note = "" }),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Name a seat (CO, BB…), “my left/right”, or “everyone”.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { vm.addNote(note); note = "" }, enabled = note.isNotBlank() && !applying) {
                            if (applying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Apply")
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Table setup") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Dropdown("You sit", table.heroPos, table.labels, vm::setHero, Modifier.weight(1f))
                        Dropdown("Players", "${table.nSeats}", (2..9).map { "$it" }, { vm.setSeatCount(it.toInt()) }, Modifier.weight(1f))
                    }
                    Text(
                        "Seats without a read use the pool model from your last table description.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(table.labels, key = { it }) { pos ->
                SeatRow(pos, table, onClick = { if (pos != table.heroPos) editing = pos })
            }
        }
    }

    editing?.let { pos ->
        val current = table.seats[pos]
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Seat $pos") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (current != null) VillainStats(current)
                    Text("Snap to an archetype:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Archetypes.PRESETS.keys.forEach { a ->
                            FilterChip(selected = false, onClick = { vm.setArchetype(pos, a); editing = null }, label = { Text(a) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Done") } },
            dismissButton = {
                if (current != null) TextButton(onClick = { vm.clearSeat(pos); editing = null }) { Text("Clear read") }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Clear all reads?") },
            text = { Text("Every seat goes back to the default pool model. Your seating setup is kept.") },
            confirmButton = { TextButton(onClick = { vm.reset(); confirmReset = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SeatRow(pos: String, table: TableState, onClick: () -> Unit) {
    val hero = pos == table.heroPos
    val read = table.seats[pos]
    Surface(
        onClick = onClick,
        modifier = Modifier.testTag("seat_$pos"),
        enabled = !hero,
        shape = RoundedCornerShape(14.dp),
        color = if (hero) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pos, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
            Column(Modifier.weight(1f)) {
                when {
                    hero -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("You", style = MaterialTheme.typography.bodyLarge)
                    }
                    read != null -> {
                        Text(read.describe().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            String.format(Locale.US, "opens %.0f%% · calls %.0f%% · station %.2f · bluffs %.0f%%",
                                read.openRaisePct * 100, read.callOpenPct * 100, read.station, read.bluffFreq * 100),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        table.notes[pos]?.let {
                            Text("“${it.joinToString(" / ")}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> Text("Default read", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, value: String, options: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); expanded = false }) }
        }
    }
}
