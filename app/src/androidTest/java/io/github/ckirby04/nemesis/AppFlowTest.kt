package io.github.ckirby04.nemesis

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.ckirby04.nemesis.data.Source
import io.github.ckirby04.nemesis.data.Sourced
import io.github.ckirby04.nemesis.engine.Cell
import io.github.ckirby04.nemesis.engine.Rake
import io.github.ckirby04.nemesis.engine.Villain
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** End-to-end flows through the real UI, against fresh offline app state. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    private val app = FreshAppRule()
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(app)
        .around(compose)

    private fun tab(label: String) =
        compose.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag("nav_bar"))).performClick()

    /** Wait for [text] to appear: screens load asynchronously and tab switches cross-fade (slow on CI emulators). */
    private fun awaitText(text: String, timeoutMillis: Long = 10_000) = compose.waitUntil(timeoutMillis) {
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun bottomNavigation_reachesEveryTab() {
        awaitText("Run simulation")
        tab("Table")
        awaitText("Live table")
        tab("History")
        awaitText("No runs yet")
        tab("Settings")
        awaitText("Anthropic API key")
        tab("Analyze")
        awaitText("Run simulation")
    }

    @Test
    fun runSimulation_showsHeatmapThenOfflineReportAndFollowup() {
        compose.onNodeWithText("Loose & sticky").performClick()
        compose.onNodeWithText("Run simulation").performScrollTo().performClick()

        // Finishing navigates to Results on its own.
        compose.waitUntilExactlyOneExists(hasText("Results"), timeoutMillis = 90_000)
        compose.onNodeWithText("Loose, sticky, calls wide").assertExists()
        compose.onNode(hasContentDescription("13 by 13", substring = true)).assertExists()
        compose.onNodeWithText("BTN").assertExists()
        compose.onNodeWithText("CO").assertExists()

        compose.onNodeWithText("Advisor report", useUnmergedTree = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Table read"), timeoutMillis = 30_000)
        compose.onNodeWithText("Reference: BTN").assertExists()

        compose.onNodeWithText("Ask a follow-up…").performTextInput("should I set mine small pairs?")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Most relevant principle", substring = true), timeoutMillis = 30_000)
    }

    @Test
    fun history_opensASavedRun() {
        runBlocking {
            app.container.runs.save(
                description = "Saved table for the history test",
                villain = Sourced(Villain(station = 0.85), Source.OFFLINE),
                positions = listOf("BTN"),
                handsPerCell = 100,
                seats = 9,
                rake = Rake(),
                results = mapOf("BTN" to mapOf("AA" to Cell(20.0, 1.0, 100), "72o" to Cell(-5.0, 1.0, 100))),
                seatReads = emptyMap(),
            )
        }
        tab("History")
        compose.waitUntilExactlyOneExists(hasText("Saved table for the history test"))
        compose.onNodeWithText("Saved table for the history test").performClick()
        compose.waitUntilExactlyOneExists(hasText("Results"))
        compose.onNodeWithText("“Saved table for the history test”").assertExists()
    }

    /** Repeated Enter on a focused Back arrow (it keeps focus while the screen fades out) must pop only once. */
    @Test
    fun repeatedBackKeyPresses_neverLeaveABlankScreen() {
        runBlocking {
            app.container.runs.save(
                description = "Saved table for the back-key test",
                villain = Sourced(Villain(), Source.OFFLINE),
                positions = listOf("BTN"),
                handsPerCell = 100,
                seats = 9,
                rake = Rake(),
                results = mapOf("BTN" to mapOf("AA" to Cell(20.0, 1.0, 100))),
                seatReads = emptyMap(),
            )
        }
        tab("History")
        compose.waitUntilExactlyOneExists(hasText("Saved table for the back-key test"))
        compose.onNodeWithText("Saved table for the back-key test").performClick()
        compose.waitUntilExactlyOneExists(hasText("Results"))

        // Freeze the transition so the fading Results screen (and its focused Back arrow) stays put.
        compose.mainClock.autoAdvance = false
        val back = compose.onNodeWithContentDescription("Back")
        compose.activityRule.scenario.onActivity { it.window.decorView.requestFocus() }
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        back.requestFocus().assertIsFocused()
        repeat(3) { back.performKeyInput { pressKey(Key.Enter) } }
        compose.mainClock.autoAdvance = true

        // One pop: back on History with the bottom bar, not an empty NavHost.
        awaitText("Saved table for the back-key test")
        compose.onNodeWithTag("nav_bar").assertExists()
    }

    @Test
    fun liveTable_noteUpdatesTheSeatOnHerosLeft() {
        tab("Table")
        compose.onNodeWithText("e.g. the guy on my left calls everything").performTextInput("the guy on my left calls everything")
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Updated SB", substring = true), timeoutMillis = 10_000)
        compose.onNodeWithTag("table_list").performScrollToNode(hasText("“the guy on my left calls everything”"))
    }

    @Test
    fun liveTable_nineHandedHasNineSeats() {
        tab("Table")
        listOf("UTG", "UTG+1", "MP", "LJ", "HJ", "CO", "BTN", "SB", "BB").forEach {
            compose.onNodeWithTag("table_list").performScrollToNode(hasTestTag("seat_$it"))
        }
    }

    @Test
    fun settings_saveAndRemoveApiKey() {
        tab("Settings")
        compose.onNodeWithText("Offline mode").assertExists()
        compose.onNodeWithText("Anthropic API key").performTextReplacement("sk-ant-test-not-a-real-key")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntilExactlyOneExists(hasText("Key saved on this device only", substring = true))
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntilExactlyOneExists(hasText("Offline mode"))
    }
}
