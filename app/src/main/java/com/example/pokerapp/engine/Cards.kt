package com.example.pokerapp.engine

/**
 * Cards are ints 0..51: `rank * 4 + suit`, rank 0..12 = 2..A, suit 0..3 = s,h,d,c.
 * Sets of cards (dead cards, decks) are 52-bit masks in a Long.
 */
object Cards {
    const val RANKS = "23456789TJQKA"
    const val SUITS = "shdc"

    fun rank(card: Int): Int = card shr 2
    fun suit(card: Int): Int = card and 3
    fun of(rank: Int, suit: Int): Int = rank * 4 + suit

    fun parse(text: String): Int {
        val r = RANKS.indexOf(text[0].uppercaseChar())
        val s = SUITS.indexOf(text[1].lowercaseChar())
        require(r >= 0 && s >= 0) { "bad card '$text'" }
        return of(r, s)
    }

    fun name(card: Int): String = "${RANKS[rank(card)]}${SUITS[suit(card)]}"

    fun bit(card: Int): Long = 1L shl card
}
