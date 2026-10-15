package com.example.pokerapp.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pokerapp.NemesisApplication
import com.example.pokerapp.data.SimState
import com.example.pokerapp.ui.analyze.AnalyzeScreen
import com.example.pokerapp.ui.history.HistoryScreen
import com.example.pokerapp.ui.report.ReportScreen
import com.example.pokerapp.ui.results.ResultsScreen
import com.example.pokerapp.ui.settings.SettingsScreen
import com.example.pokerapp.ui.table.TableScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object AnalyzeRoute
@Serializable object TableRoute
@Serializable object HistoryRoute
@Serializable object SettingsRoute
@Serializable data class ResultsRoute(val runId: Long)
@Serializable data class ReportRoute(val runId: Long)

private data class Tab(val route: Any, val type: KClass<*>, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab(AnalyzeRoute, AnalyzeRoute::class, "Analyze", Icons.Outlined.GridOn, Icons.Filled.GridOn),
    Tab(TableRoute, TableRoute::class, "Table", Icons.Outlined.TableRestaurant, Icons.Filled.TableRestaurant),
    Tab(HistoryRoute, HistoryRoute::class, "History", Icons.Outlined.History, Icons.Filled.History),
    Tab(SettingsRoute, SettingsRoute::class, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

private fun NavHostController.switchTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun NemesisApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = TABS.any { t -> destination?.hierarchy?.any { it.hasRoute(t.type) } == true }

    // A finished run opens its results from whichever tab the user is on.
    val simulation = (LocalContext.current.applicationContext as NemesisApplication).container.simulation
    val sim by simulation.state.collectAsStateWithLifecycle()
    LaunchedEffect(sim) {
        (sim as? SimState.Done)?.let {
            nav.navigate(ResultsRoute(it.runId))
            simulation.consume()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TABS.forEach { t ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(t.type) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(t.route) },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = AnalyzeRoute, modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding)) {
            composable<AnalyzeRoute> {
                AnalyzeScreen(onOpenSettings = { nav.switchTab(SettingsRoute) }, onOpenTable = { nav.switchTab(TableRoute) })
            }
            composable<TableRoute> { TableScreen() }
            composable<HistoryRoute> {
                HistoryScreen(onOpenRun = { nav.navigate(ResultsRoute(it)) }, onNewRun = { nav.switchTab(AnalyzeRoute) })
            }
            composable<SettingsRoute> { SettingsScreen() }
            composable<ResultsRoute> {
                ResultsScreen(onBack = { nav.popBackStack() }, onOpenReport = { nav.navigate(ReportRoute(it)) })
            }
            composable<ReportRoute> { ReportScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
