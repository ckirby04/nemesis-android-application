package io.github.ckirby04.nemesis

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.github.ckirby04.nemesis.data.SimState
import io.github.ckirby04.nemesis.sim.SimulationService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The foreground service follows the run's lifecycle and its notification action cancels it. */
@RunWith(AndroidJUnit4::class)
class SimulationServiceTest {
    private val app = FreshAppRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(app)

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION") // getRunningServices still reports the caller's own services
    private fun serviceIsForeground(): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(50)
            .any { it.service.className == SimulationService::class.java.name && it.foreground }

    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "condition not met within ${timeoutMs}ms" }
            Thread.sleep(50)
        }
    }

    @Test
    fun serviceRunsInForegroundDuringARun_andCancelActionStopsIt() = runBlocking {
        val sim = app.container.simulation
        app.container.settings.setHandsPerCell(5_000) // long enough to still be running when we cancel
        sim.start("loose sticky stations, 100bb deep", listOf("BTN", "CO", "HJ", "LJ"), useTableReads = false)

        withTimeout(10_000) { sim.state.first { it is SimState.Running } }
        waitFor { serviceIsForeground() }

        // Exactly what the notification's Cancel button sends.
        context.startService(Intent(context, SimulationService::class.java).setAction(SimulationService.ACTION_CANCEL))

        withTimeout(10_000) { sim.state.first { it == SimState.Idle } }
        waitFor { !serviceIsForeground() }
        assertTrue(app.container.runs.summaries.first().isEmpty()) // a cancelled run isn't saved
    }

    @Test
    fun serviceStopsOnItsOwnWhenARunFinishes() = runBlocking {
        val sim = app.container.simulation
        app.container.settings.setHandsPerCell(50)
        app.container.settings.setFullGrid(false)
        sim.start("tight passive, 150bb deep", listOf("BTN"), useTableReads = false)

        val done = withTimeout(60_000) { sim.state.first { it is SimState.Done } } as SimState.Done
        waitFor { !serviceIsForeground() }
        assertEquals(done.runId, app.container.runs.summaries.first().single().id)
        sim.consume()
    }
}
