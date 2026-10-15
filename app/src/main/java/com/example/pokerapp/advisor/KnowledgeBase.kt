package com.example.pokerapp.advisor

data class Principle(val id: String, val title: String, val tags: String, val text: String)

/** The RAG corpus: vetted exploitative-poker principles, ported verbatim from Nemesis. */
object KnowledgeBase {
    val PRINCIPLES = listOf(
        Principle(
            id = "implied-odds-setmining",
            title = "Set-mining and implied-odds thresholds",
            tags = "small pairs speculative depth implied odds set mining cold call",
            text = "A pocket pair flops a set about 11.8% of the time (~1 in 8.5). To cold-call a raise purely to set-mine, you want to be able to win roughly 10-15x your call when you hit, because you don't always get paid and sometimes hit and lose. At 100bb effective, calling a large raise (e.g. 20bb) leaves under 9:1 behind, which is below the threshold -- so set-mining small pairs as a cold-call is a small loser at 100bb against standard sizing. You generally need ~150bb+ for the call to clear comfortably. Small pairs improve sharply as effective stacks deepen and as the table pays off big hands.",
        ),
        Principle(
            id = "speculation-tax",
            title = "Open size taxes speculation",
            tags = "open size raise size speculative suited connectors price preflop tax",
            text = "Large preflop opens overprice speculative hands. When the standard open is 8-12x the big blind, suited connectors and small pairs that rely on flopping big are paying a premium to draw, and most of the time they whiff and surrender the cost. The bigger the typical open, the tighter you should be with hands whose value is mostly implied/speculative, and the more you should lean on hands with high-card and made-hand equity. Open size is information: very large opens are a signal to fold speculative holdings rather than pay the tax.",
        ),
        Principle(
            id = "position-realization",
            title = "Position and equity realization",
            tags = "position button cutoff late position in position realization speculative",
            text = "Speculative hands need position to realize their equity. In position you control pot size, see opponents act first, and can take free cards or value-bet thinly. The same suited connector that is a fold from early position is often a profitable play on the button, especially multiway and deep. Widen meaningfully in late position and tighten from early position. Equity realization, not raw equity, is what determines whether a marginal hand is +EV to play.",
        ),
        Principle(
            id = "value-vs-stations",
            title = "Beating calling stations: value, not bluffs",
            tags = "station sticky cant fold calldown value bet thin bluff overfold pool",
            text = "Against a pool that calls down too wide and can't fold top pair, fold equity is low, so bluffing is the leak -- it lights money on fire on average. The correct exploit is to value-bet thinner and bigger than feels comfortable and to bluff much less. Your profit comes from getting paid off by worse made hands, not from forcing folds. The sticky opponent is a feature to be milked with value, not a target to be blown off a hand. Triple-barreling bluffs into stations is the classic recency-bias trap.",
        ),
        Principle(
            id = "rake-impact",
            title = "Rake at low stakes",
            tags = "rake low stakes live limp small pots raise to thin field cost",
            text = "Live low-stakes rake is large relative to the stakes (often a fixed drop per pot plus a jackpot drop), which is a big percentage of a small pot. This punishes a high-volume limping/calling style: paying to limp marginal hands into multiway raked pots is a slow leak because rake eats the small wins and you only get paid on rare big hands. Rake rewards raising to thin the field and play fewer, larger, less-rake-dominated pots. It is an argument both for aggression and for tightening.",
        ),
        Principle(
            id = "multiway-adjust",
            title = "Multiway pot adjustments",
            tags = "multiway many players limped pot implied odds nut hands suited connectors",
            text = "Multiway pots shift value toward hands that make the nuts (sets, nut flushes, nut straights) and away from thin top-pair hands, because someone usually has a piece. Implied odds for suited connectors and small pairs go up multiway when entry is cheap, because the rare big hand gets paid by multiple opponents. But bluffing collapses multiway -- value and nut-equity are what print. Enter cheap, in position, and be willing to fold non-nut made hands to heavy multiway action.",
        ),
        Principle(
            id = "isolation-limpers",
            title = "Isolating limpers instead of over-limping",
            tags = "limp isolate raise limpers position initiative iso",
            text = "Over-limping behind raises poorly when the table frequently raises, because you get blown off the pot or end up calling out of position. Prefer to isolate weak limpers with a raise when you have position and a hand that flops well, taking initiative and dead money. Reserve over-limping for cheap, in-position, multiway spots that close the action; if your limps routinely get raised, fold those hands preflop rather than limp-calling out of position.",
        ),
        Principle(
            id = "range-advantage-cbet",
            title = "Range advantage and c-betting",
            tags = "range advantage cbet high board preflop raiser AK king high initiative",
            text = "The preflop raiser holds more strong top-pair and overpair combos than a caller on many high boards, giving a structural range advantage that supports profitable continuation betting. With a hand like AK on a King-high board you are betting into that edge and can extract value across streets, especially against opponents who can't fold a worse King. Initiative plus range advantage is a core source of the aggressor's profit -- but the value comes from being called, not from folds, when the pool is sticky.",
        ),
        Principle(
            id = "3bet-vs-aggro",
            title = "3-betting against aggressive openers",
            tags = "3bet aggressive maniac light openers KJo wide opens punish reraise",
            text = "When opponents open very wide (e.g. raising offsuit broadways and worse light), their opening range is weak and uncapped only at the very top, so a tighter, linear value 3-bet range punishes them: you isolate the weak portion of their range with initiative and position. Against light openers, 3-betting for value with strong hands and folding the trash they raise is more profitable than flat-calling wide and playing bloated pots out of position. Add light 3-bets only if they actually fold; against stations, keep 3-bets value-heavy.",
        ),
        Principle(
            id = "tag-baseline",
            title = "Tight-aggressive as the low-stakes baseline",
            tags = "TAG tight aggressive baseline strategy value weighted range discipline",
            text = "The standard winning profile for loose-passive low-stakes games is tight-aggressive: a disciplined, value-weighted range played with initiative. Tight-vs-loose and aggressive-vs-passive are independent axes; the goal is a tight range played aggressively, with speculation included only when cheap, in position, and deep. This is not a contrarian discovery -- it is the textbook answer, and matching it is a sign a read is sound rather than variance-driven.",
        ),
        Principle(
            id = "variance-sample",
            title = "Variance, sample size, and tracking",
            tags = "variance sample size database HUD recency bias win rate tracking results",
            text = "Poker results are extremely noisy over small samples, and reads like 'they never fold' feel truest right after a good run. A framework that aligns with theory is reassuring, but only volume plus a database/HUD confirms whether specific spots (e.g. speculative opens) are actually break-even-or-better. Track win rate by position and by hand class before trusting feel; let the data, not recent sessions, decide whether to widen or tighten.",
        ),
        Principle(
            id = "suited-ace-blockers",
            title = "Suited aces and blocker value",
            tags = "suited ace A5s A2s blocker nut flush wheel semibluff value",
            text = "Suited aces carry nut-flush potential and ace blockers, making them stronger than their raw rank suggests, especially in position and multiway where the nut flush gets paid. Small suited aces (A5s-A2s) add wheel-straight and semi-bluff equity. They play better as raising/isolating hands than as passive calls, and their value rises against pools that pay off flushes.",
        ),
    )
}
