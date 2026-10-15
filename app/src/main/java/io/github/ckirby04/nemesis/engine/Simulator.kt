package io.github.ckirby04.nemesis.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Exploitative best-response Monte-Carlo EV simulator, ported from Nemesis'
 * `engine.py`. NOT a GTO solver: villains play a FIXED parameterised strategy,
 * hero plays a defined open-and-value-bet line, and we measure hero's average
 * chip delta (bb) each time a hand is opened. Trust the relative ordering far
 * more than the exact numbers.
 *
 * The postflop model matches `engine.py`; preflop is more realistic than the original:
 * only the seats behind hero act (earlier seats folded), blinds count toward their raises
 * and calls, results are measured against folding (a posted blind is sunk), players facing
 * a 3-bet cold continue only with their 3-bet range, flatters fold to a 3-bet, and the BB
 * can't open (it acts last).
 */
object Simulator {
    // Postflop decision thresholds (made-strength fractions). ASSUMPTION: coarse by design.
    private const val HERO_VALUE = 0.55      // hero value-bets at/above this
    private const val HERO_CALL_LEAD = 0.50  // hero calls a villain lead down to this
    private const val HERO_CALL_RAISE = 0.66 // hero continues vs a raise only when this strong
    private const val V_VALUE_RAISE = 0.82   // villain raises hero's bet for value at/above this
    private const val V_RAISE_P = 0.50       // ...at this frequency when it has the goods
    private const val V_AIR = 0.35           // below this a villain's hand is "air"

    private class Player(val c1: Int, val c2: Int, var rem: Double, val model: Villain?) {
        var folded = false
        var strength = 0.0
    }

    /**
     * Simulate ONE hand where hero open-raises [heroHand] from [heroPos] (any seat but the BB).
     * Returns hero's result in big blinds relative to folding preflop: 0 = same as folding, so a
     * blind hero already posted isn't counted as a loss.
     */
    fun simulateHand(
        heroHand: String,
        heroPos: String,
        v: Villain,
        rng: Random,
        nSeats: Int = 9,
        openSize: Double = 4.0,
        seatModels: Map<String, Villain>? = null,
        rake: Rake? = null,
    ): Double {
        var dead = 0L
        val hero = Hands.toCombo(heroHand, dead, rng) ?: return 0.0
        dead = dead or Cards.bit(hero[0]) or Cards.bit(hero[1])

        val labels = Positions.forSeats(nSeats)
        val heroIdx = labels.indexOf(heroPos)
        require(heroIdx >= 0) { "$heroPos isn't a seat at a $nSeats-handed table" }
        require(heroPos != "BB") { "The big blind acts last preflop, so it can't open" }

        // Blinds are already in the pot. A raise from the SB includes its posted 0.5.
        val heroBlind = blindOf(heroPos)
        var pot = 1.5 // SB 0.5 + BB 1.0
        var heroInvested = openSize
        pot += openSize - heroBlind
        var curBet = openSize

        // Hero is opening, so every seat before hero folded. Only the seats behind act, in order;
        // each seat may use its own model.
        var callers = ArrayList<Pair<IntArray, Villain>>()
        var threebet: Pair<IntArray, Villain>? = null
        val coldCallers = ArrayList<Pair<IntArray, Villain>>()
        for (seat in labels.subList(heroIdx + 1, labels.size)) {
            val m = seatModels?.get(seat) ?: v
            val vc = Hands.toCombo(Hands.ALL_169.random(rng), dead, rng) ?: continue
            val vClass = Hands.classOf(vc[0], vc[1])
            val blind = blindOf(seat)
            if (threebet == null) {
                if (m.will3bet(vClass) && rng.nextDouble() < 0.8) {
                    threebet = vc to m
                    dead = dead or Cards.bit(vc[0]) or Cards.bit(vc[1])
                    curBet = openSize * 3
                    pot += curBet - blind
                } else if (m.will3bet(vClass) || m.willCallOpen(vClass)) {
                    // A 3-bet hand that doesn't 3-bet (the other 20%) flats rather than folding.
                    callers += vc to m
                    dead = dead or Cards.bit(vc[0]) or Cards.bit(vc[1])
                    pot += openSize - blind
                }
            } else if (m.will3bet(vClass)) {
                // ASSUMPTION: facing a 3-bet cold, a player continues only with its own 3-bet range (and calls).
                coldCallers += vc to m
                dead = dead or Cards.bit(vc[0]) or Cards.bit(vc[1])
                pot += curBet - blind
            }
        }

        if (threebet != null) {
            // ASSUMPTION: players who flatted the open fold to the 3-bet (their range sits below a 3-bet
            // range); their calls stay in the pot. Hero continues only with the top ~8% of hands.
            if (Ranking[heroHand] >= 0.92) {
                val call = curBet - openSize
                heroInvested += call
                pot += call
                callers = arrayListOf(threebet)
                callers += coldCallers
            } else {
                return heroBlind - heroInvested
            }
        }
        if (callers.isEmpty()) return pot - heroInvested + heroBlind // everyone folded to hero's open

        // ---- postflop ----
        val board = dealBoard(dead, rng)
        val heroP = Player(hero[0], hero[1], v.stackBb - heroInvested, null)
        val villains = callers.map { (c, m) ->
            val invested = if (threebet == null) openSize else curBet
            Player(c[0], c[1], m.stackBb - invested, m)
        }

        for (n in intArrayOf(3, 4, 5)) {
            val live = villains.filter { !it.folded }
            if (live.isEmpty()) break
            val hs = Evaluator.strengthFraction(heroP.c1, heroP.c2, board, n)
            for (p in live) p.strength = Evaluator.strengthFraction(p.c1, p.c2, board, n)

            if (hs > HERO_VALUE && heroP.rem > 0) {
                // hero value-bets 66% pot
                val bet = min(0.66 * pot, heroP.rem)
                heroP.rem -= bet; heroInvested += bet; pot += bet

                // A villain may check-raise for value, or as a bluff with air.
                val raiser = live.firstOrNull { p ->
                    val valueRaise = p.strength >= V_VALUE_RAISE && rng.nextDouble() < V_RAISE_P
                    val bluffRaise = p.strength < V_AIR && rng.nextDouble() < p.model!!.bluffFreq
                    (valueRaise || bluffRaise) && p.rem > bet
                }
                if (raiser == null) {
                    // villains call down by their OWN station tendency, else fold
                    for (p in live) {
                        val callThresh = 0.62 - 0.30 * p.model!!.station
                        val pay = min(bet, p.rem)
                        if (p.strength >= callThresh && pay > 0) { p.rem -= pay; pot += pay } else p.folded = true
                    }
                } else {
                    for (p in live) if (p !== raiser) p.folded = true
                    val raiseTotal = min(2.5 * bet, raiser.rem)
                    raiser.rem -= raiseTotal; pot += raiseTotal
                    val toCall = max(0.0, raiseTotal - bet)
                    // ASSUMPTION: hero gives up vs the raise unless strong.
                    if (hs >= HERO_CALL_RAISE && heroP.rem > 0) {
                        val call = min(toCall, heroP.rem)
                        heroP.rem -= call; heroInvested += call; pot += call
                    } else {
                        return heroBlind - heroInvested
                    }
                }
            } else {
                // hero checks; a villain may lead (cbetFreq for value, bluffFreq with air)
                val leader = live.firstOrNull { p ->
                    val valueLead = p.strength > HERO_VALUE && rng.nextDouble() < p.model!!.cbetFreq
                    val bluffLead = p.strength < V_AIR && rng.nextDouble() < p.model!!.bluffFreq
                    (valueLead || bluffLead) && p.rem > 0
                }
                if (leader != null) {
                    val lead = min(0.60 * pot, leader.rem)
                    leader.rem -= lead; pot += lead
                    for (p in live) if (p !== leader) p.folded = true
                    // ASSUMPTION: hero calls a lead only with a decent made hand
                    if (hs >= HERO_CALL_LEAD && heroP.rem > 0) {
                        val pay = min(lead, heroP.rem)
                        heroP.rem -= pay; heroInvested += pay; pot += pay
                    } else {
                        return heroBlind - heroInvested
                    }
                }
            }
        }

        // showdown — a flop was dealt, so "no flop no drop" doesn't apply
        pot -= (rake ?: Rake(enabled = false)).take(pot, sawFlop = true)
        val heroRank = Evaluator.evaluate(heroP.c1, heroP.c2, board)
        val ranks = villains.filter { !it.folded }.map { Evaluator.evaluate(it.c1, it.c2, board) }
        val best = minOf(heroRank, ranks.minOrNull() ?: Int.MAX_VALUE)
        if (heroRank != best) return heroBlind - heroInvested
        val winners = 1 + ranks.count { it == best }
        return pot / winners - heroInvested + heroBlind
    }

    private fun blindOf(pos: String) = when (pos) { "SB" -> 0.5; "BB" -> 1.0; else -> 0.0 }

    private fun dealBoard(dead: Long, rng: Random): IntArray {
        val deck = IntArray(52 - java.lang.Long.bitCount(dead))
        var i = 0
        for (c in 0 until 52) if (dead and Cards.bit(c) == 0L) deck[i++] = c
        // partial Fisher-Yates: only the first five cards are needed
        for (k in 0 until 5) {
            val j = k + rng.nextInt(deck.size - k)
            val t = deck[k]; deck[k] = deck[j]; deck[j] = t
        }
        return deck.copyOf(5)
    }

    /**
     * EV of opening each hand from each position, as `pos -> hand -> Cell`.
     *
     * Cells run in parallel on [Dispatchers.Default]; each gets its own RNG derived
     * from [seed], so a seeded run is reproducible regardless of thread scheduling.
     * Cancelling the calling coroutine stops the run.
     */
    suspend fun runStats(
        villain: Villain,
        positions: List<String>,
        nHands: Int = 600,
        openSize: Double = villain.openSizeBb,
        nSeats: Int = 9,
        subset: List<String>? = null,
        seatModels: Map<String, Villain>? = null,
        rake: Rake = Rake(),
        seed: Long? = null,
        onProgress: (Float) -> Unit = {},
    ): Map<String, Map<String, Cell>> = coroutineScope {
        val hands = (subset ?: Hands.ALL_169).map(Hands::normalize).distinct()
        val base = seed ?: Random.nextLong()
        val total = positions.size * hands.size
        val done = AtomicInteger()
        val jobs = positions.flatMapIndexed { pi, pos ->
            hands.mapIndexed { hi, h ->
                async(Dispatchers.Default) {
                    val rng = Random(base * 31 + pi * 1009L + hi)
                    var sum = 0.0
                    var sumSq = 0.0
                    for (i in 0 until nHands) {
                        if (i % 64 == 0) ensureActive()
                        val x = simulateHand(h, pos, villain, rng, nSeats, openSize, seatModels, rake)
                        sum += x
                        sumSq += x * x
                    }
                    val mean = sum / nHands
                    val se = if (nHands > 1) {
                        sqrt(max((sumSq - nHands * mean * mean) / (nHands - 1), 0.0) / nHands)
                    } else Double.POSITIVE_INFINITY
                    onProgress(done.incrementAndGet().toFloat() / total)
                    Triple(pos, h, Cell(mean, se, nHands))
                }
            }
        }
        val out = LinkedHashMap<String, LinkedHashMap<String, Cell>>()
        for (p in positions) out[p] = LinkedHashMap()
        for ((pos, h, cell) in jobs.awaitAll()) out.getValue(pos)[h] = cell
        out
    }
}
