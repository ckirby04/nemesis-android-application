package io.github.ckirby04.nemesis.engine

import kotlin.random.Random

/** Raised when a hand string can't be parsed into a 169-class; carries a user-friendly message. */
class InvalidHand(message: String) : IllegalArgumentException(message)

/** 169-class starting-hand utilities ('AA', 'AKs', 'AKo', '72o'). */
object Hands {
    private const val RANKS = Cards.RANKS
    private val GRID_ORDER = RANKS.reversed() // A K Q … 2

    /** Hand label at a cell of the standard 13x13 grid (suited above the diagonal). */
    fun gridHand(row: Int, col: Int): String {
        val hi = GRID_ORDER[row]
        val lo = GRID_ORDER[col]
        return when {
            row == col -> "$hi$lo"
            row < col -> "$hi${lo}s"
            else -> "$lo${hi}o"
        }
    }

    val ALL_169: List<String> = buildList {
        for (r in 0 until 13) for (c in 0 until 13) add(gridHand(r, c))
    }

    val GRID_LABELS: List<Char> = GRID_ORDER.toList()

    /**
     * The 41-hand default subset from the CLI: a cross-section of every hand class
     * that runs fast enough for an interactive phone session.
     */
    val DEFAULT_SUBSET = listOf(
        "AA", "KK", "QQ", "JJ", "TT", "99", "88", "77", "55", "22",
        "AKs", "AQs", "AJs", "ATs", "KQs", "KJs", "QJs", "JTs", "T9s", "98s", "87s", "76s", "65s", "54s",
        "A5s", "A2s", "AKo", "AQo", "AJo", "KQo", "KJo", "QJo", "JTo", "T9o",
        "A9o", "K9o", "Q9o", "J9o", "72o", "J4o", "T6o",
    )

    /** Accept user-friendly forms ('78s', 't9o', 'JJ') -> canonical ('87s', 'T9o', 'JJ'). */
    fun normalize(hand: String): String {
        val s = hand.trim()
        if (s.length < 2) throw InvalidHand("'$hand': a hand needs two ranks, e.g. 'AKs', 'TT', '76o'")
        var r1 = s[0].uppercaseChar()
        var r2 = s[1].uppercaseChar()
        if (r1 !in RANKS || r2 !in RANKS)
            throw InvalidHand("'$hand': unknown rank — use 2-9, T, J, Q, K, A (e.g. 'AKs', '99')")
        if (RANKS.indexOf(r1) < RANKS.indexOf(r2)) r1 = r2.also { r2 = r1 }
        val suffix = if (s.length > 2) s[2].lowercaseChar().toString() else ""
        if (r1 == r2) {
            if (suffix == "s") throw InvalidHand("'$hand': a pair can't be suited (drop the 's')")
            if (suffix != "" && suffix != "o") throw InvalidHand("'$hand': a pair takes no suit suffix")
            return "$r1$r2"
        }
        if (suffix != "" && suffix != "s" && suffix != "o")
            throw InvalidHand("'$hand': suffix must be 's' (suited) or 'o' (offsuit)")
        return "$r1$r2${suffix.ifEmpty { "o" }}"
    }

    /** Sample a concrete 2-card combo for a 169-class avoiding [dead]; null if none is free. */
    fun toCombo(hand: String, dead: Long, rng: Random): IntArray? {
        val r1 = RANKS.indexOf(hand[0])
        val r2 = RANKS.indexOf(hand[1])
        val opts = ArrayList<IntArray>(12)
        when {
            hand.length == 2 ->
                for (s1 in 0 until 4) for (s2 in s1 + 1 until 4) opts += intArrayOf(Cards.of(r1, s1), Cards.of(r2, s2))
            hand[2] == 's' -> for (s in 0 until 4) opts += intArrayOf(Cards.of(r1, s), Cards.of(r2, s))
            else -> for (s1 in 0 until 4) for (s2 in 0 until 4) if (s1 != s2)
                opts += intArrayOf(Cards.of(r1, s1), Cards.of(r2, s2))
        }
        opts.shuffle(rng)
        return opts.firstOrNull { dead and Cards.bit(it[0]) == 0L && dead and Cards.bit(it[1]) == 0L }
    }

    /** Map two concrete cards back to their 169-class. */
    fun classOf(c1: Int, c2: Int): String {
        val hi = maxOf(Cards.rank(c1), Cards.rank(c2))
        val lo = minOf(Cards.rank(c1), Cards.rank(c2))
        if (hi == lo) return "${RANKS[hi]}${RANKS[lo]}"
        return "${RANKS[hi]}${RANKS[lo]}${if (Cards.suit(c1) == Cards.suit(c2)) "s" else "o"}"
    }
}
