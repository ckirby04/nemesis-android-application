package io.github.ckirby04.nemesis

import io.github.ckirby04.nemesis.engine.Cards
import io.github.ckirby04.nemesis.engine.Cell
import io.github.ckirby04.nemesis.engine.Evaluator
import io.github.ckirby04.nemesis.engine.Hands
import io.github.ckirby04.nemesis.engine.InvalidHand
import io.github.ckirby04.nemesis.engine.Rake
import io.github.ckirby04.nemesis.engine.Ranking
import io.github.ckirby04.nemesis.engine.Simulator
import io.github.ckirby04.nemesis.engine.Villain
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

    /** 3,000 random 5/6/7-card hands ranked by the real Python treys 0.1.8 (fixture in test/resources). */
    @Test fun evaluator_matchesTreysOnRandomHands() {
        val lines = javaClass.getResource("/treys_ranks.txt")!!.readText().lines().filter { it.isNotBlank() }
        assertTrue(lines.size > 3000)
        for (line in lines) {
            val parts = line.split(" ")
            assertEquals(line, parts.last().toInt(), Evaluator.evaluate(cards(*parts.dropLast(1).toTypedArray())))
        }
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

    // ---- preflop action order and blind accounting (beyond the Python original) ----

    /** Continues only with AA: `threebetPct`/`callOpenPct` 0 leave just the 1.0-percentile hand. */
    private val foldsAlmostEverything = Villain(threebetPct = 0.0, callOpenPct = 0.0)

    private fun ev(hand: String, pos: String, seats: Int = 9, models: Map<String, Villain>? = null, n: Int = 3000) =
        runBlocking {
            Simulator.runStats(foldsAlmostEverything, listOf(pos), n, nSeats = seats, subset = listOf(hand),
                seatModels = models, rake = Rake(enabled = false), seed = 3)
        }.getValue(pos).getValue(hand).mean

    @Test fun onlySeatsBehindHeroAct() {
        // Stations sit UTG..CO, but they acted before the button and folded, so a BTN open only faces the blinds.
        val stations = listOf("UTG", "UTG+1", "MP", "LJ", "HJ", "CO").associateWith { Villain(callOpenPct = 0.7, station = 0.97) }
        assertEquals(1.5, ev("72o", "BTN", models = stations), 0.2) // steals the blinds ~99% of the time
    }

    @Test fun smallBlindOpen_isMeasuredAgainstFolding() {
        // Heads-up SB steal: wins the BB's 1.0 instead of losing the posted 0.5 -> +1.5 versus folding.
        assertEquals(1.5, ev("72o", "SB", seats = 2), 0.2)
    }

    @Test(expected = IllegalArgumentException::class) fun bigBlind_cannotOpen() {
        Simulator.simulateHand("AA", "BB", Villain(), Random(1))
    }
}
