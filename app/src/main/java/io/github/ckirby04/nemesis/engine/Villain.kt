package io.github.ckirby04.nemesis.engine

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/** A parameterised opponent pool. Ranges are "top X% of hands". */
@Serializable
data class Villain(
    val openRaisePct: Double = 0.15,  // opens/raises the top 15% of hands
    val threebetPct: Double = 0.03,   // 3-bets the top 3%
    val callOpenPct: Double = 0.35,   // calls a raise with the top 35% ("calls light" => wider)
    val openSizeBb: Double = 4.0,     // how big they open
    val station: Double = 0.55,       // postflop calldown propensity 0..1 (1 = never folds)
    val cbetFreq: Double = 0.5,       // how often they bet when checked to / lead
    val bluffFreq: Double = 0.10,     // how often they fire with air
    val stackBb: Double = 100.0,
) {
    fun willOpen(h: String): Boolean = Ranking[h] >= 1 - openRaisePct
    fun will3bet(h: String): Boolean = Ranking[h] >= 1 - threebetPct
    fun willCallOpen(h: String): Boolean = Ranking[h] >= 1 - callOpenPct && !will3bet(h)

    /** Keep parameters inside the ranges the model was designed for. */
    fun clamped(): Villain = copy(
        openRaisePct = openRaisePct.coerceIn(0.05, 0.6),
        threebetPct = threebetPct.coerceIn(0.0, 0.2),
        callOpenPct = callOpenPct.coerceIn(0.05, 0.7),
        station = station.coerceIn(0.0, 0.97),
        cbetFreq = cbetFreq.coerceIn(0.0, 1.0),
        bluffFreq = bluffFreq.coerceIn(0.0, 0.6),
        openSizeBb = openSizeBb.coerceIn(2.0, 30.0),
        stackBb = stackBb.coerceIn(20.0, 500.0),
    )

    /** Short human tag, e.g. "loose, sticky, calls wide". */
    fun describe(): String = buildList {
        add(if (openRaisePct >= 0.28) "loose" else if (openRaisePct <= 0.14) "tight" else "std")
        if (station >= 0.7) add("sticky") else if (station <= 0.35) add("foldy")
        if (bluffFreq >= 0.25) add("aggro/bluffy")
        if (callOpenPct >= 0.45) add("calls wide")
    }.joinToString(", ")
}

/**
 * Live-style rake: a percentage of the pot, capped, with "no flop no drop".
 * Lowers small-pot EV; numbers stay DIRECTIONAL, not exact bb/100.
 */
@Serializable
data class Rake(
    val pct: Double = 0.05,
    val capBb: Double = 3.0,
    val enabled: Boolean = true,
) {
    fun take(pot: Double, sawFlop: Boolean): Double =
        if (!enabled || !sawFlop) 0.0 else minOf(pct * pot, capBb, pot)
}

/**
 * Per-hand EV estimate with a standard error and 95% CI. `crossesZero` flags cells
 * we can't tell apart from break-even at this sample size.
 */
@Serializable
data class Cell(val mean: Double, val se: Double, val n: Int) {
    val ciLow: Double get() = mean - 1.96 * se
    val ciHigh: Double get() = mean + 1.96 * se
    val halfCi: Double get() = 1.96 * se
    val crossesZero: Boolean get() = ciLow <= 0.0 && 0.0 <= ciHigh

    /**
     * Verdict for an open from a seat whose EV scale is [scale] ([Positions.evScale]). Thresholds are
     * in BTN units, so a +5bb open "PRINTS" on the button but is just "play" from UTG.
     */
    fun verdict(scale: Double = 1.0): String = when {
        mean >= 3.5 * scale -> "PRINTS"
        mean >= 0.5 * scale -> "play"
        mean > -0.5 * scale -> "marginal"
        mean > -3.0 * scale -> "leak"
        else -> "FOLD"
    }
}

object Positions {
    /** Full-ring seats in action order. */
    val ALL = listOf("UTG", "UTG+1", "MP", "LJ", "HJ", "CO", "BTN", "SB", "BB")

    /**
     * Labels in play at an [nSeats]-handed table. Short-handed tables drop the EARLY seats, so the
     * button and blinds always exist (6-max = LJ HJ CO BTN SB BB; heads-up = SB BB).
     */
    fun forSeats(nSeats: Int): List<String> = ALL.takeLast(nSeats.coerceIn(2, 9))

    /** Players left to act behind an opener at [pos] (BTN = 2, the blinds; SB = 1). */
    fun behind(pos: String, nSeats: Int): Int {
        val labels = forSeats(nSeats)
        val i = labels.indexOf(pos)
        return if (i < 0) 0 else labels.size - 1 - i
    }

    /**
     * How big EV numbers get from [pos]: an open's EV grows roughly with sqrt(players behind)
     * (measured across loose, nitty, aggressive, sticky and default pools). 1.0 on the BTN, 2.0 at
     * UTG 9-handed, 0.71 in the SB. Verdict and advisor thresholds are in BTN units times this.
     */
    fun evScale(pos: String, nSeats: Int): Double = sqrt(behind(pos, nSeats).coerceAtLeast(1) / 2.0)

    /** Canonical label for user/LLM spellings: "utg1", "UTG +1", "btn" -> "UTG+1", "BTN". Null if unknown. */
    fun canonical(label: String): String? {
        val up = label.uppercase().replace(" ", "")
        val fixed = if (up == "UTG1") "UTG+1" else up
        return fixed.takeIf { it in ALL }
    }
}
