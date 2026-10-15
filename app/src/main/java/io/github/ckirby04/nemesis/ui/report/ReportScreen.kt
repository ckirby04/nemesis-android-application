package io.github.ckirby04.nemesis.ui.report

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import io.github.ckirby04.nemesis.AppContainer
import io.github.ckirby04.nemesis.advisor.Advisor
import io.github.ckirby04.nemesis.advisor.Principle
import io.github.ckirby04.nemesis.advisor.TableProfile
import io.github.ckirby04.nemesis.data.Followup
import io.github.ckirby04.nemesis.data.SavedRun
import io.github.ckirby04.nemesis.data.Source
import io.github.ckirby04.nemesis.ui.ReportRoute
import io.github.ckirby04.nemesis.ui.components.MarkdownText
import io.github.ckirby04.nemesis.ui.components.SectionCard
import io.github.ckirby04.nemesis.ui.components.SourceBadge
import io.github.ckirby04.nemesis.ui.components.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReportViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val runId = handle.toRoute<ReportRoute>().runId
    val run = c.runs.observe(runId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _generating = MutableStateFlow(false)
    val generating = _generating.asStateFlow()
    private val _asking = MutableStateFlow<String?>(null)
    /** The question currently awaiting an answer, shown optimistically in the thread. */
    val asking = _asking.asStateFlow()

    init {
        viewModelScope.launch {
            if (run.filterNotNull().first().report == null) generate()
        }
    }

    private fun profileOf(r: SavedRun): TableProfile =
        Advisor.profile(r.results, r.villain, r.advisorDescription, r.positions.first())

    fun generate() {
        if (_generating.value) return
        viewModelScope.launch {
            val r = run.value ?: return@launch
            _generating.value = true
            try {
                val profile = profileOf(r)
                val retrieved = Advisor.retrieve(profile)
                c.runs.setReport(r.id, c.coach.report(profile, retrieved), retrieved)
            } finally {
                _generating.value = false
            }
        }
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _asking.value != null) return
        viewModelScope.launch {
            val r = run.value ?: return@launch
            _asking.value = q
            try {
                val retrieved = r.retrieved.ifEmpty { Advisor.retrieve(profileOf(r)) }
                // Only real Claude answers go back as conversation history.
                val history = r.followups.filter { it.source == Source.CLAUDE }.map { it.question to it.answer }
                val answer = c.coach.followup(q, profileOf(r), retrieved, history)
                c.runs.setFollowups(r.id, r.followups + Followup(q, answer.value, answer.source))
            } finally {
                _asking.value = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(onBack: () -> Unit, vm: ReportViewModel = viewModel(factory = appViewModelFactory(::ReportViewModel))) {
    val run by vm.run.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val asking by vm.asking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var question by rememberSaveable { mutableStateOf("") }
    var openPrinciple by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val report = run?.report

    LaunchedEffect(run?.followups?.size, asking) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0 && (asking != null || (run?.followups?.isNotEmpty() == true))) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advisor") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (report != null) {
                        IconButton(onClick = vm::generate, enabled = !generating) { Icon(Icons.Outlined.Refresh, "Regenerate report") }
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Nemesis table report")
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(send, "Share report"))
                        }) { Icon(Icons.Outlined.Share, "Share report") }
                    }
                },
            )
        },
        bottomBar = {
            if (report != null && !generating) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it },
                            placeholder = { Text("Ask a follow-up…") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = asking == null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { vm.ask(question); question = "" }),
                        )
                        IconButton(onClick = { vm.ask(question); question = "" }, enabled = question.isNotBlank() && asking == null) {
                            Icon(Icons.AutoMirrored.Outlined.Send, "Send")
                        }
                    }
                }
            }
        },
    ) { padding ->
        val r = run
        if (r == null || report == null || generating) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.size(16.dp))
                    Text("Writing your table report…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reference: ${r.positions.first()}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        r.reportSource?.let { SourceBadge(it) }
                    }
                    MarkdownText(report)
                }
            }
            if (r.retrieved.isNotEmpty()) {
                item { GroundingCard(r.retrieved, onOpen = { openPrinciple = it }) }
            }
            items(r.followups) { f -> FollowupBubble(f.question, f.answer, f.source) }
            asking?.let { q -> item { FollowupBubble(q, null, null) } }
        }
    }

    openPrinciple?.let { id ->
        val p = run?.retrieved?.firstOrNull { it.id == id }
        if (p != null) {
            AlertDialog(
                onDismissRequest = { openPrinciple = null },
                confirmButton = { TextButton(onClick = { openPrinciple = null }) { Text("Close") } },
                title = { Text(p.title) },
                text = { Text(p.text) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroundingCard(retrieved: List<Principle>, onOpen: (String) -> Unit) {
    SectionCard(title = "Grounded in") {
        Text(
            "The principles retrieved for this table. Tap one to read it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            retrieved.forEach { p -> AssistChip(onClick = { onOpen(p.id) }, label = { Text(p.title) }) }
        }
    }
}

@Composable
private fun FollowupBubble(question: String, answer: String?, source: Source?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f).width(48.dp))
            Surface(shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(question, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Surface(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (answer == null) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    MarkdownText(answer)
                    source?.let { SourceBadge(it) }
                }
            }
        }
    }
}
