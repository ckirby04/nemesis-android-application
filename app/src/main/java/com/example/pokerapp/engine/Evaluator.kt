package com.example.pokerapp.engine

/**
 * Five-to-seven card hand evaluator returning treys-compatible ranks:
 * 1 = royal flush … 7462 = 7-high. Lower is better, exactly like the Python
 * `treys.Evaluator` the original engine used, so made-strength fractions match.
 *
 * Tables are built once on first use, the same way treys builds its
 * `LookupTable`: flushes keyed by a 13-bit rank mask, everything else keyed by
 * the product of one prime per rank (unique per rank multiset).
 */
object Evaluator {
    const val WORST = 7462

    private val PRIMES = intArrayOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41)

    private val flushRank = IntArray(1 shl 13)
    private val unsuitedRank = HashMap<Int, Int>(8192)

    init {
        build()
    }

    private fun build() {
        // Straight masks from A-high down to 6-high, then the wheel (5-4-3-2-A).
        val straights = IntArray(10)
        for ((i, top) in (12 downTo 4).withIndex()) straights[i] = 0b11111 shl (top - 4)
        straights[9] = (1 shl 12) or 0b1111
        val straightSet = straights.toHashSet()

        // Every 5-distinct-rank mask that isn't a straight, best first.
        val highCards = (0 until (1 shl 13))
            .filter { Integer.bitCount(it) == 5 && it !in straightSet }
            .sortedDescending()

        var rank = 1
        for (m in straights) flushRank[m] = rank++                       // straight flush 1..10
        for (q in 12 downTo 0) for (k in 12 downTo 0) if (k != q)          // quads 11..166
            unsuitedRank[pow(PRIMES[q], 4) * PRIMES[k]] = rank++
        for (t in 12 downTo 0) for (p in 12 downTo 0) if (p != t)          // full house 167..322
            unsuitedRank[pow(PRIMES[t], 3) * pow(PRIMES[p], 2)] = rank++
        for (m in highCards) flushRank[m] = rank++                       // flush 323..1599
        for (m in straights) unsuitedRank[maskProduct(m)] = rank++         // straight 1600..1609
        for (t in 12 downTo 0)                                             // trips 1610..2467
            for (k1 in 12 downTo 0) for (k2 in k1 - 1 downTo 0)
                if (k1 != t && k2 != t)
                    unsuitedRank[pow(PRIMES[t], 3) * PRIMES[k1] * PRIMES[k2]] = rank++
        for (hp in 12 downTo 0) for (lp in hp - 1 downTo 0)                // two pair 2468..3325
            for (k in 12 downTo 0) if (k != hp && k != lp)
                unsuitedRank[pow(PRIMES[hp], 2) * pow(PRIMES[lp], 2) * PRIMES[k]] = rank++
        for (p in 12 downTo 0)                                             // one pair 3326..6185
            for (k1 in 12 downTo 0) for (k2 in k1 - 1 downTo 0) for (k3 in k2 - 1 downTo 0)
                if (k1 != p && k2 != p && k3 != p)
                    unsuitedRank[pow(PRIMES[p], 2) * PRIMES[k1] * PRIMES[k2] * PRIMES[k3]] = rank++
        for (m in highCards) unsuitedRank[maskProduct(m)] = rank++         // high card 6186..7462
        check(rank - 1 == WORST) { "evaluator table has ${rank - 1} classes" }
    }

    private fun pow(b: Int, e: Int): Int { var r = 1; repeat(e) { r *= b }; return r }

    private fun maskProduct(mask: Int): Int {
        var p = 1
        for (r in 0..12) if (mask and (1 shl r) != 0) p *= PRIMES[r]
        return p
    }

    fun evaluate5(a: Int, b: Int, c: Int, d: Int, e: Int): Int {
        val s = Cards.suit(a)
        if (Cards.suit(b) == s && Cards.suit(c) == s && Cards.suit(d) == s && Cards.suit(e) == s) {
            val mask = (1 shl Cards.rank(a)) or (1 shl Cards.rank(b)) or (1 shl Cards.rank(c)) or
                (1 shl Cards.rank(d)) or (1 shl Cards.rank(e))
            return flushRank[mask]
        }
        val product = PRIMES[Cards.rank(a)] * PRIMES[Cards.rank(b)] * PRIMES[Cards.rank(c)] *
            PRIMES[Cards.rank(d)] * PRIMES[Cards.rank(e)]
        return unsuitedRank.getValue(product)
    }

    /** Best rank over every 5-card subset of [cards] (5, 6 or 7 cards). */
    fun evaluate(cards: IntArray): Int {
        val n = cards.size
        require(n in 5..7) { "need 5-7 cards, got $n" }
        var best = Int.MAX_VALUE
        for (i in 0 until n - 4) for (j in i + 1 until n - 3) for (k in j + 1 until n - 2)
            for (l in k + 1 until n - 1) for (m in l + 1 until n) {
                val r = evaluate5(cards[i], cards[j], cards[k], cards[l], cards[m])
                if (r < best) best = r
            }
        return best
    }

    /** Hole cards + board, as treys' `evaluate(board, hand)`. */
    fun evaluate(hole1: Int, hole2: Int, board: IntArray, boardSize: Int = board.size): Int {
        val all = IntArray(boardSize + 2)
        all[0] = hole1; all[1] = hole2
        System.arraycopy(board, 0, all, 2, boardSize)
        return evaluate(all)
    }

    /** Made-hand strength as a 0..1 fraction (1 = best possible class). */
    fun strengthFraction(hole1: Int, hole2: Int, board: IntArray, boardSize: Int): Double =
        1.0 - (evaluate(hole1, hole2, board, boardSize) - 1) / (WORST - 1).toDouble()
}
