package com.example.pokerapp

import com.example.pokerapp.engine.Cards
import com.example.pokerapp.engine.Cell
import com.example.pokerapp.engine.Evaluator
import com.example.pokerapp.engine.Hands
import com.example.pokerapp.engine.InvalidHand
import com.example.pokerapp.engine.Rake
import com.example.pokerapp.engine.Ranking
import com.example.pokerapp.engine.Simulator
import com.example.pokerapp.engine.Villain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Parity checks ported from Nemesis' pytest suite (test_smoke / test_engine_features). */
class EngineTest {
    private fun cards(vararg s: String) = s.map(Cards::parse).toIntArray()

    @Test fun evaluator_matchesTreysExtremes() {
        assertEquals(1, Evaluator.evaluate(cards("As", "Ks", "Qs", "Js", "Ts")))
        assertEquals(Evaluator.WORST, Evaluator.evaluate(cards("7s", "5h", "4d", "3c", "2s")))
        assertEquals(10, Evaluator.evaluate(cards("5d", "4d", "3d", "2d", "Ad")))   // steel wheel = worst straight flush
        assertEquals(1600, Evaluator.evaluate(cards("As", "Kh", "Qd", "Jc", "Ts"))) // best straight
    }

    @Test fun evaluator_picksBestFiveOfSeven() {
        val quads = Evaluator.evaluate(cards("Ah", "Ad", "As", "Ac", "Kd", "2c", "3h"))
        assertEquals(11, quads) // AAAA-K is the best quads
        assertTrue(Evaluator.evaluate(cards("Kh", "Kd", "2s", "2c", "9d", "7c", "4h")) in 2468..3325) // two pair
    }

    @Test fun normalize_acceptsFriendlyForms() {
        assertEquals("87s", Hands.normalize("78s"))
        assertEquals("AKo", Hands.normalize("ako"))
        assertEquals("JJ", Hands.normalize("jj"))
        assertEquals("AKo", Hands.normalize("AK"))
    }

    @Test(expected = InvalidHand::class) fun normalize_rejectsBadRank() { Hands.normalize("AX") }
    @Test(expected = InvalidHand::class) fun normalize_rejectsSuitedPair() { Hands.normalize("QQs") }
    @Test(expected = InvalidHand::class) fun normalize_rejectsTooShort() { Hands.normalize("A") }

    @Test fun grid_has169DistinctHands() {
        assertEquals(169, Hands.ALL_169.toSet().size)
        assertEquals("AA", Hands.gridHand(0, 0))
        assertEquals("AKs", Hands.gridHand(0, 1))
        assertEquals("AKo", Hands.gridHand(1, 0))
    }

    @Test fun ranking_isMonotonicAtTheExtremes() {
        assertTrue(Ranking["AA"] > Ranking["72o"])
        assertTrue(Ranking["AA"] >= 0.99)
        assertTrue(Ranking["72o"] <= 0.05)
    }

    @Test fun dealing_respectsDeadCards() {
        val rng = Random(1)
        repeat(200) {
            val a = Hands.toCombo("AKs", 0L, rng)!!
            val dead = Cards.bit(a[0]) or Cards.bit(a[1])
            val b = Hands.toCombo("AKo", dead, rng)!!
            assertTrue(b.none { it == a[0] || it == a[1] })
        }
    }

    @Test fun rake_noFlopNoDrop() {
        val rk = Rake(pct = 0.1, capBb = 3.0)
        assertEquals(0.0, rk.take(50.0, sawFlop = false), 0.0)
        assertEquals(3.0, rk.take(50.0, sawFlop = true), 0.0)
        assertEquals(1.0, rk.take(10.0, sawFlop = true), 1e-9)
        assertEquals(0.0, Rake(enabled = false).take(50.0, sawFlop = true), 0.0)
    }

    @Test fun cell_crossesZeroFlag() {
        assertTrue(Cell(mean = 0.1, se = 1.0, n = 100).crossesZero)
        assertFalse(Cell(mean = 50.0, se = 0.5, n = 100).crossesZero)
    }

    private fun run(v: Villain = Villain(), hands: List<String> = listOf("AA", "72o"), n: Int = 1500, rake: Rake = Rake(), seed: Long = 7) =
        runBlocking { Simulator.runStats(v, listOf("BTN"), n, subset = hands, rake = rake, seed = seed) }.getValue("BTN")

    @Test fun seed_makesRunsReproducible() {
        assertEquals(run(seed = 42), run(seed = 42))
    }

    @Test fun aces_beatSevenDeuce() {
        val r = run()
        assertTrue(r.getValue("AA").mean > r.getValue("72o").mean)
        assertTrue(r.getValue("AA").ciLow > 0)
    }

    @Test fun rake_lowersEvButPreservesOrdering() {
        val free = run(rake = Rake(enabled = false))
        val raked = run(rake = Rake())
        assertTrue(raked.getValue("AA").mean < free.getValue("AA").mean)
        assertTrue(raked.getValue("AA").mean > raked.getValue("72o").mean)
    }

    @Test fun postflopVillainAggression_isLive() {
        val hands = listOf("AA", "87s")
        val passive = run(Villain(bluffFreq = 0.0, cbetFreq = 0.0), hands, n = 4000)
        val aggro = run(Villain(bluffFreq = 0.5, cbetFreq = 0.9), hands, n = 4000)
        assertNotEquals(passive, aggro)
        assertTrue(aggro.getValue("87s").mean < passive.getValue("87s").mean)
        assertTrue(aggro.getValue("AA").mean > aggro.getValue("87s").mean)
    }

    @Test fun benchmark_fullGridOnePosition() {
        val t0 = System.nanoTime()
        val r = runBlocking { Simulator.runStats(Villain(), listOf("BTN"), nHands = 400, seed = 1) }
        val ms = (System.nanoTime() - t0) / 1e6
        println("BENCH full 169-hand grid x 400 sims, 1 position: ${"%.0f".format(ms)} ms")
        assertEquals(169, r.getValue("BTN").size)
    }
}
