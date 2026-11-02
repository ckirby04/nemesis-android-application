package io.github.ckirby04.nemesis.engine

/**
 * Preflop percentile strength (0 = worst, 1 = best) of each 169-class. The ordering is ported
 * from Nemesis' shipped high-sample `data/hand_ranking.json` and stored as the 169 hands from
 * weakest to strongest.
 *
 * Percentiles are weighted by combos (pair 6, suited 4, offsuit 12 of 1326), so "the top 15% of
 * hands" ([Villain.openRaisePct] etc.) means 15% of the hands actually dealt, not 15% of the 169
 * classes. Nemesis used class-count percentiles.
 */
object Ranking {
    private val ORDER = listOf(
        "32o", "42o", "62o", "72o", "52o", "43o", "32s", "53o", "63o", "73o", "42s", "82o", "62s",
        "83o", "64o", "72s", "52s", "43s", "54o", "92o", "74o", "84o", "63s", "53s", "93o", "73s",
        "82s", "94o", "65o", "75o", "83s", "T2o", "64s", "54s", "85o", "74s", "76o", "92s", "T3o",
        "84s", "95o", "65s", "93s", "T4o", "75s", "94s", "86o", "J2o", "T5o", "87o", "T2s", "96o",
        "85s", "J3o", "T3s", "76s", "J4o", "T6o", "97o", "86s", "95s", "T4s", "Q2o", "J5o", "96s",
        "T5s", "J2s", "87s", "J6o", "98o", "T7o", "J3s", "Q3o", "T6s", "97s", "Q4o", "J4s", "J5s",
        "J7o", "22", "Q5o", "J6s", "K2o", "T8o", "T7s", "Q6o", "Q3s", "Q2s", "98s", "K3o", "K4o",
        "Q7o", "Q4s", "J8o", "T9o", "Q5s", "T8s", "K2s", "J7s", "J9o", "K5o", "33", "Q7s", "T9s",
        "Q6s", "J8s", "K3s", "A2o", "K6o", "Q8o", "K4s", "JTo", "J9s", "K7o", "K5s", "A3o", "Q9o",
        "K8o", "Q8s", "K6s", "A4o", "QTo", "44", "A6o", "A2s", "K7s", "Q9s", "A3s", "K9o", "A5o",
        "QJo", "JTs", "A7o", "K8s", "A4s", "QTs", "55", "KTo", "QJs", "K9s", "A8o", "A9o", "A5s",
        "A6s", "KJo", "A7s", "KQo", "A8s", "KJs", "KTs", "ATo", "A9s", "66", "KQs", "AJo", "AQo",
        "ATs", "AJs", "AKo", "AQs", "77", "AKs", "88", "99", "TT", "JJ", "QQ", "KK", "AA",
    )

    private fun combos(hand: String) = when {
        hand.length == 2 -> 6
        hand[2] == 's' -> 4
        else -> 12
    }

    /** Combos strictly weaker than the hand, over the combos of every other hand: 32o = 0, AA = 1. */
    private val PERCENTILE: Map<String, Double> = buildMap {
        var weaker = 0
        for (h in ORDER) {
            put(h, weaker.toDouble() / (1326 - combos(h)))
            weaker += combos(h)
        }
    }

    operator fun get(hand: String): Double =
        PERCENTILE[hand] ?: throw InvalidHand("'$hand' is not a canonical 169-class")
}
