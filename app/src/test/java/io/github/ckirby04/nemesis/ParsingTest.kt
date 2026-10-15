package io.github.ckirby04.nemesis

import io.github.ckirby04.nemesis.engine.Positions
import io.github.ckirby04.nemesis.engine.Villain
import io.github.ckirby04.nemesis.parsing.TableParser
import io.github.ckirby04.nemesis.parsing.TableState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seat labels and the offline parsers (ported from Nemesis' test_table_state / parsing behaviour). */
class ParsingTest {
    @Test fun nineHanded_hasNineSeats() {
        assertEquals(listOf("UTG", "UTG+1", "MP", "LJ", "HJ", "CO", "BTN", "SB", "BB"), Positions.forSeats(9))
    }

    @Test fun shortHanded_keepsButtonAndBlinds() {
        assertEquals(listOf("LJ", "HJ", "CO", "BTN", "SB", "BB"), Positions.forSeats(6))
        assertEquals(listOf("SB", "BB"), Positions.forSeats(2))
        assertEquals(9, Positions.forSeats(12).size)
    }

    @Test fun canonical_acceptsLooseSpellings() {
        assertEquals("UTG+1", Positions.canonical("utg1"))
        assertEquals("UTG+1", Positions.canonical("UTG +1"))
        assertEquals("BTN", Positions.canonical("btn"))
        assertNull(Positions.canonical("dealer"))
    }

    @Test fun resolve_relativeSeats() {
        val t = TableState(Villain(), heroPos = "BTN")
        assertEquals(listOf("SB"), t.resolve("left"))
        assertEquals(listOf("CO"), t.resolve("right"))
        assertEquals(listOf("UTG+1"), t.resolve("utg1"))
        assertEquals(8, t.resolve("all").size)
        assertTrue("BTN" !in t.resolve("all"))
        assertEquals(emptyList<String>(), t.resolve("nonsense seat"))
        assertEquals(emptyList<String>(), TableState(Villain(), nSeats = 6).resolve("UTG")) // not at a 6-max table
    }

    @Test fun parseUpdate_prefersUtgPlusOneOverUtg() {
        assertEquals("UTG+1", TableParser.parseUpdate("UTG+1 3-bets a lot").target)
        assertEquals("UTG+1", TableParser.parseUpdate("utg 1 is a nit").target)
        assertEquals("UTG", TableParser.parseUpdate("UTG is a nit").target)
        assertEquals("BB", TableParser.parseUpdate("the BB never folds").target)
    }

    @Test fun parseUpdate_doesNotReadStackSizesAsTheBigBlind() {
        assertEquals("all", TableParser.parseUpdate("everyone is 100bb deep and sticky").target)
    }

    @Test fun parseUpdate_behavioursBecomeDeltas() {
        val u = TableParser.parseUpdate("guy on my left calls everything and bluffs rivers")
        assertEquals("left", u.target)
        assertTrue(u.deltas.getValue("station") > 0)
        assertTrue(u.deltas.getValue("bluffFreq") > 0)
    }

    @Test fun parseDescription_separatesOpenSizeFromStack() {
        val v = TableParser.parseDescription("loose, raises 20bb light, sticky stations, 100bb deep")
        assertEquals(20.0, v.openSizeBb, 0.0)
        assertEquals(100.0, v.stackBb, 0.0)
        assertTrue(v.station >= 0.85)
        assertTrue(v.openRaisePct > Villain().openRaisePct)
    }
}
