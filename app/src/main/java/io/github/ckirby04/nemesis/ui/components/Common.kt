package io.github.ckirby04.nemesis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.ckirby04.nemesis.AppContainer
import io.github.ckirby04.nemesis.NemesisApplication
import io.github.ckirby04.nemesis.data.Source
import io.github.ckirby04.nemesis.engine.Villain
import java.util.Locale

/** ViewModel factory with access to the app's [AppContainer] and the destination's [SavedStateHandle]. */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NemesisApplication
        create(app.container, createSavedStateHandle())
    }
}

/** Late positions first: the reference position for the advisor is the first one selected. */
val POSITION_ORDER = listOf("BTN", "CO", "HJ", "LJ", "MP", "UTG+1", "UTG", "SB", "BB")

@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Text(
                    title.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/** "Claude" / "Offline" pill showing who produced a result. */
@Composable
fun SourceBadge(source: Source, modifier: Modifier = Modifier) {
    val claude = source == Source.CLAUDE
    Surface(
        modifier,
        shape = RoundedCornerShape(50),
        color = if (claude) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (claude) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (claude) Icons.Outlined.AutoAwesome else Icons.Outlined.CloudOff, null, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (claude) "Claude" else "Offline", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The parsed villain model as a grid of compact stats. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VillainStats(v: Villain, modifier: Modifier = Modifier) {
    fun pct(x: Double) = String.format(Locale.US, "%.0f%%", x * 100)
    fun num(x: Double) = String.format(Locale.US, "%.2f", x)
    fun bb(x: Double) = String.format(Locale.US, "%.0fbb", x)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("Opens", pct(v.openRaisePct))
        Stat("Calls opens", pct(v.callOpenPct))
        Stat("3-bets", pct(v.threebetPct))
        Stat("Station", num(v.station))
        Stat("Bluffs", pct(v.bluffFreq))
        Stat("Open size", bb(v.openSizeBb))
        Stat("Stacks", bb(v.stackBb))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.size(16.dp))
        action()
    }
}
