package io.github.ckirby04.nemesis

import io.github.ckirby04.nemesis.advisor.Advisor
import io.github.ckirby04.nemesis.advisor.KnowledgeBase
import io.github.ckirby04.nemesis.advisor.TfidfRetriever
import io.github.ckirby04.nemesis.engine.Cell
import io.github.ckirby04.nemesis.engine.Villain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import io.github.ckirby04.nemesis.engine.Positions
import org.junit.Assert.assertFalse
import org.junit.Test

class AdvisorTest {
    private val retriever = TfidfRetriever(KnowledgeBase.PRINCIPLES)

    @Test fun terms_dropStopWordsAndAddBigrams() {
        assertEquals(listOf("calling", "station", "calling station"), TfidfRetriever.terms("the calling station"))
    }

    @Test fun retriever_findsStationPrincipleForStickyTable() {
        val hits = retriever.retrieve("sticky calling stations who can't fold, value bet thin", k = 3)
        assertEquals("value-vs-stations", hits.first().principle.id)
        assertTrue(hits.zipWithNext().all { (a, b) -> a.score >= b.score })
    }

    @Test fun retriever_findsSetMiningForSmallPairs() {
        val hits = retriever.retrieve("small pairs set mining implied odds", k = 1)
        assertEquals("implied-odds-setmining", hits.first().principle.id)
    }

    @Test fun retriever_unknownQueryScoresZero() {
        assertTrue(retriever.retrieve("zzzz qqqq", k = 3).all { it.score == 0.0 })
    }

    private fun fakeResults(ev: (String) -> Double) =
        mapOf("BTN" to Advisor.CLASSES.values.flatten().associateWith { Cell(ev(it), 0.5, 400) })

    @Test fun profile_flagsStationAndDepth() {
        val v = Villain(station = 0.85, openRaisePct = 0.32, stackBb = 100.0)
        val p = Advisor.profile(fakeResults { if (it == "AA") 30.0 else -2.0 }, v, "sticky table")
        assertEquals("BTN", p.refPos)
        assertTrue(p.flags.any { "calling stations" in it })
        assertTrue(p.flags.any { "around 100bb" in it })
        assertTrue(p.flags.any { "suited connectors are losing" in it })
        assertTrue(p.query.startsWith("sticky table. "))
    }

    @Test fun offlineReport_hasSectionsAndGrounding() {
        val p = Advisor.profile(fakeResults { 2.0 }, Villain(station = 0.85), "sticky table")
        val retrieved = Advisor.retrieve(p)
        val report = Advisor.offlineReport(p, retrieved)
        listOf("## Table read", "## What the numbers say", "## How to play it", "## Leaks to avoid", "stop bluffing")
            .forEach { assertTrue("missing '$it'", it in report) }
        assertTrue(retrieved.first().title in report)
        assertTrue("+2.0 bb/open" in report)
    }

    @Test fun missingClassesRenderAsNotApplicable() {
        val partial = mapOf("BTN" to mapOf("AA" to Cell(20.0, 1.0, 100)))
        val p = Advisor.profile(partial, Villain(), "desc")
        assertTrue(p.classEv.getValue("trash").isNaN())
        assertTrue("n/a bb/open" in Advisor.offlineReport(p, Advisor.retrieve(p)))
    }

    @Test fun followupGroundingMergesWithoutDuplicates() {
        val p = Advisor.profile(fakeResults { 1.0 }, Villain(), "desc")
        val g = Advisor.followupGrounding("should I 3bet the maniac?", Advisor.retrieve(p))
        assertEquals(g.size, g.map { it.id }.toSet().size)
        assertTrue(g.size <= 6)
    }

    // ---- thresholds scale with the players left to act behind the reference seat ----

    private fun classResults(pos: String, premiums: Double) =
        mapOf(pos to Advisor.CLASSES.values.flatten().associateWith { Cell(if (it in Advisor.CLASSES.getValue("premiums")) premiums else 0.0, 0.5, 400) })

    @Test fun evScale_isOneOnTheButton() {
        assertEquals(1.0, Positions.evScale("BTN", 9), 1e-9)   // 2 behind: the blinds
        assertEquals(2.0, Positions.evScale("UTG", 9), 1e-9)   // 8 behind
        assertEquals(Math.sqrt(0.5), Positions.evScale("SB", 9), 1e-9)
        assertEquals(Positions.evScale("SB", 9), Positions.evScale("SB", 2), 1e-9) // heads-up SB still has 1 behind
    }

    @Test fun premiumFlag_scalesWithPlayersBehind() {
        val flag = "premium hands print heavily"
        assertTrue(Advisor.profile(classResults("BTN", 7.0), Villain(), "d", nSeats = 9).flags.any { flag in it })
        assertFalse(Advisor.profile(classResults("UTG", 7.0), Villain(), "d", nSeats = 9).flags.any { flag in it }) // UTG needs ~12
    }

    @Test fun cellVerdict_scalesWithPlayersBehind() {
        assertEquals("PRINTS", Cell(5.0, 0.5, 400).verdict(Positions.evScale("BTN", 9)))
        assertEquals("play", Cell(5.0, 0.5, 400).verdict(Positions.evScale("UTG", 9)))
        assertEquals("FOLD", Cell(-4.0, 0.5, 400).verdict(Positions.evScale("BTN", 9)))
        assertEquals("leak", Cell(-4.0, 0.5, 400).verdict(Positions.evScale("UTG", 9)))
    }

    @Test fun metrics_tellClaudeHowManyPlayersAreBehind() {
        val p = Advisor.profile(classResults("CO", 5.0), Villain(), "d", nSeats = 9)
        assertTrue("Players left to act behind CO: 3" in Advisor.metricsBlob(p))
    }
}
