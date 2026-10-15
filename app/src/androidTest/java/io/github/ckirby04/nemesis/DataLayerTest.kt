package io.github.ckirby04.nemesis

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ckirby04.nemesis.advisor.KnowledgeBase
import io.github.ckirby04.nemesis.data.Followup
import io.github.ckirby04.nemesis.data.KeySource
import io.github.ckirby04.nemesis.data.Source
import io.github.ckirby04.nemesis.data.Sourced
import io.github.ckirby04.nemesis.engine.Cell
import io.github.ckirby04.nemesis.engine.Rake
import io.github.ckirby04.nemesis.engine.Villain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room + DataStore repositories on a real device, each test on fresh storage. */
@RunWith(AndroidJUnit4::class)
class DataLayerTest {
    private lateinit var c: AppContainer

    @Before fun setUp() {
        c = TestNemesisApplication.freshContainer(ApplicationProvider.getApplicationContext())
    }

    private suspend fun saveRun(desc: String = "sticky table") = c.runs.save(
        description = desc,
        villain = Sourced(Villain(station = 0.9), Source.OFFLINE, "No API key set"),
        positions = listOf("BTN", "CO"),
        handsPerCell = 300,
        seats = 9,
        rake = Rake(pct = 0.04, capBb = 2.0),
        results = mapOf(
            "BTN" to mapOf("AA" to Cell(31.5, 1.2, 300)),
            "CO" to mapOf("AA" to Cell(28.0, 1.3, 300)),
        ),
        seatReads = mapOf("SB" to Villain(station = 0.97)),
    )

    @Test fun runs_roundTripEveryField() = runTest {
        val id = saveRun()
        val run = c.runs.get(id)!!
        assertEquals("sticky table", run.description)
        assertEquals(0.9, run.villain.station, 0.0)
        assertEquals(listOf("BTN", "CO"), run.positions)
        assertEquals(Rake(pct = 0.04, capBb = 2.0), run.rake)
        assertEquals(Cell(31.5, 1.2, 300), run.results.getValue("BTN").getValue("AA"))
        assertEquals(Source.OFFLINE, run.parsedBy)
        assertEquals("No API key set", run.parseNote)
        assertEquals(0.97, run.seatReads.getValue("SB").station, 0.0)
        assertTrue("Seat reads: SB=" in run.advisorDescription)
        assertNull(run.report)
    }

    @Test fun runs_reportAndFollowupsPersist_andRegeneratingClearsFollowups() = runTest {
        val id = saveRun()
        val principles = KnowledgeBase.PRINCIPLES.take(2)
        c.runs.setReport(id, Sourced("# Report", Source.OFFLINE), principles)
        c.runs.setFollowups(id, listOf(Followup("q?", "a.", Source.OFFLINE)))
        c.runs.get(id)!!.let {
            assertEquals("# Report", it.report)
            assertEquals(principles, it.retrieved)
            assertEquals(1, it.followups.size)
        }
        assertTrue(c.runs.summaries.first().single().hasReport)

        c.runs.setReport(id, Sourced("# New", Source.CLAUDE), principles)
        c.runs.get(id)!!.let {
            assertEquals(Source.CLAUDE, it.reportSource)
            assertTrue(it.followups.isEmpty())
        }
    }

    @Test fun runs_summariesNewestFirst_andDelete() = runTest {
        val first = saveRun("first")
        Thread.sleep(5)
        saveRun("second")
        assertEquals(listOf("second", "first"), c.runs.summaries.first().map { it.description })
        c.runs.delete(first)
        assertEquals(listOf("second"), c.runs.summaries.first().map { it.description })
        c.runs.deleteAll()
        assertTrue(c.runs.summaries.first().isEmpty())
    }

    @Test fun table_offlineNotesTargetTheRightSeats() = runTest {
        val left = c.tables.applyNote("the guy on my left calls everything")
        assertEquals(listOf("SB"), left.seats)
        assertEquals(Source.OFFLINE, left.source)
        assertEquals(listOf("UTG+1"), c.tables.applyNote("utg1 is a nit").seats)
        val t = c.tables.current()
        assertTrue(t.seats.getValue("SB").station > Villain().station)
        assertTrue(t.seats.getValue("UTG+1").openRaisePct < Villain().openRaisePct)
        assertEquals(8, c.tables.applyNote("everyone bluffs rivers").seats.size) // whole table minus hero
    }

    @Test fun table_shortHandedKeepsBlindsAndDropsEarlySeats() = runTest {
        c.tables.applyNote("UTG is a nit")
        c.tables.setSeatCount(6)
        val t = c.tables.current()
        assertEquals(listOf("LJ", "HJ", "CO", "BTN", "SB", "BB"), t.labels)
        assertEquals("BTN", t.heroPos)
        assertFalse("UTG" in t.seats)
    }

    @Test fun table_heroLeavingTheTableMovesToTheBigBlind() = runTest {
        c.tables.setHeroPos("UTG")
        c.tables.setSeatCount(6)
        assertEquals("BB", c.tables.current().heroPos)
    }

    @Test fun settings_savedKeyWinsOverDeveloperKey() = runTest {
        assertEquals(KeySource.NONE, c.settings.current().keySource)
        c.settings.setApiKey("  sk-ant-x  ")
        c.settings.current().let {
            assertEquals("sk-ant-x", it.apiKey)
            assertEquals(KeySource.SAVED, it.keySource)
        }
    }

    /** A pasted smart quote used to surface as "Couldn't read Claude's answer" (OkHttp rejects the header). */
    @Test fun nonAsciiApiKey_fallsBackOfflineWithAClearReason() = runTest {
        c.settings.setApiKey("sk-ant-\u201Cpasted\u201D")
        val parsed = c.coach.parseDescription("loose and sticky")
        assertEquals(Source.OFFLINE, parsed.source)
        assertEquals("API key contains invalid characters", parsed.note)
    }
}
