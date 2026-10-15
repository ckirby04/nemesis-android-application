package io.github.ckirby04.nemesis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ckirby04.nemesis.AppContainer
import io.github.ckirby04.nemesis.data.AppSettings
import io.github.ckirby04.nemesis.data.KeySource
import io.github.ckirby04.nemesis.engine.Rake
import io.github.ckirby04.nemesis.ui.components.SectionCard
import io.github.ckirby04.nemesis.ui.components.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

sealed interface KeyTest {
    data object Idle : KeyTest
    data object Testing : KeyTest
    data object Ok : KeyTest
    data class Failed(val message: String) : KeyTest
}

class SettingsViewModel(private val c: AppContainer, @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _keyTest = MutableStateFlow<KeyTest>(KeyTest.Idle)
    val keyTest = _keyTest.asStateFlow()

    fun saveKey(key: String) = viewModelScope.launch { c.settings.setApiKey(key); _keyTest.value = KeyTest.Idle }
    fun setModel(m: String) = viewModelScope.launch { c.settings.setModel(m); _keyTest.value = KeyTest.Idle }
    fun setHands(n: Int) = viewModelScope.launch { c.settings.setHandsPerCell(n) }
    fun setFullGrid(on: Boolean) = viewModelScope.launch { c.settings.setFullGrid(on) }
    fun setRake(r: Rake) = viewModelScope.launch { c.settings.setRake(r) }

    fun testKey() = viewModelScope.launch {
        _keyTest.value = KeyTest.Testing
        _keyTest.value = c.coach.testKey().fold({ KeyTest.Ok }, { KeyTest.Failed(it.message ?: "Failed") })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(factory = appViewModelFactory(::SettingsViewModel))) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val keyTest by vm.keyTest.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ClaudeCard(s, keyTest, vm)
            SimulationCard(s, vm)
            RakeCard(s.rake, vm::setRake)
            SectionCard(title = "About") {
                Text(
                    "Nemesis is an exploitative best-response simulator, not a GTO solver. Villains play the fixed strategy " +
                        "you describe; hero plays a defined open-and-value-bet line. Results are directional, not exact " +
                        "bb/100 — a study tool for between sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ClaudeCard(s: AppSettings, keyTest: KeyTest, vm: SettingsViewModel) {
    var draft by rememberSaveable(s.savedKey) { mutableStateOf(s.savedKey) }
    var reveal by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = "Claude") {
        Text(
            "Optional. With a key, Claude reads your table descriptions and seat notes and writes the advisor report. " +
                "Without one, everything still works using offline heuristics and templates.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Anthropic API key") },
            placeholder = { Text("sk-ant-…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (reveal) "Hide key" else "Show key")
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.saveKey(draft) }, enabled = draft.trim() != s.savedKey) { Text("Save") }
            if (s.savedKey.isNotEmpty()) TextButton(onClick = { draft = ""; vm.saveKey("") }) { Text("Remove") }
            if (s.claudeEnabled) TextButton(onClick = vm::testKey, enabled = keyTest != KeyTest.Testing) { Text("Test") }
        }
        when (keyTest) {
            KeyTest.Idle -> Text(
                when (s.keySource) {
                    KeySource.SAVED -> "Key saved on this device only (excluded from backups)."
                    KeySource.DEVELOPER -> "Using the developer key from local.properties (debug build). A key saved here takes priority."
                    KeySource.NONE -> "Offline mode"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KeyTest.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Contacting Claude…", style = MaterialTheme.typography.bodySmall)
            }
            KeyTest.Ok -> StatusLine(Icons.Outlined.CheckCircle, "Key works with ${s.model}", MaterialTheme.colorScheme.primary)
            is KeyTest.Failed -> StatusLine(Icons.Outlined.ErrorOutline, keyTest.message, MaterialTheme.colorScheme.error)
        }

        Text("Model", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        AppSettings.MODELS.forEach { (id, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = s.model == id, onClick = { vm.setModel(id) }, role = Role.RadioButton)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s.model == id, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun SimulationCard(s: AppSettings, vm: SettingsViewModel) {
    // Local slider state so dragging doesn't write DataStore on every frame.
    var hands by remember(s.handsPerCell) { mutableFloatStateOf(s.handsPerCell.toFloat()) }
    SectionCard(title = "Simulation") {
        val snapped = (hands / 100).roundToInt() * 100
        LabeledValue("Sims per hand", "$snapped")
        Slider(
            value = hands,
            onValueChange = { hands = it },
            onValueChangeFinished = { vm.setHands(snapped) },
            valueRange = 200f..3000f,
        )
        Text(
            "More sims shrink the confidence intervals (fewer hatched cells) but take longer. 1500+ gives a smooth grid.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow("Full 169-hand grid", "Off: simulate a 41-hand cross-section (faster)", s.fullGrid, vm::setFullGrid)
    }
}

@Composable
private fun RakeCard(rake: Rake, onChange: (Rake) -> Unit) {
    var pct by remember(rake.pct) { mutableFloatStateOf((rake.pct * 100).toFloat()) }
    var cap by remember(rake.capBb) { mutableFloatStateOf(rake.capBb.toFloat()) }
    SectionCard(title = "Rake") {
        SwitchRow("Subtract rake", "No flop, no drop", rake.enabled) { onChange(rake.copy(enabled = it)) }
        if (rake.enabled) {
            LabeledValue("Rake", String.format(Locale.US, "%.0f%% of pot", pct))
            Slider(value = pct, onValueChange = { pct = it }, onValueChangeFinished = { onChange(rake.copy(pct = pct.roundToInt() / 100.0)) },
                valueRange = 0f..10f, steps = 9)
            LabeledValue("Cap", String.format(Locale.US, "%.0f bb", cap))
            Slider(value = cap, onValueChange = { cap = it }, onValueChangeFinished = { onChange(rake.copy(capBb = cap.roundToInt().toDouble())) },
                valueRange = 1f..10f, steps = 8)
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
