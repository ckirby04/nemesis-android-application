package io.github.ckirby04.nemesis.parsing

import io.github.ckirby04.nemesis.engine.Positions
import io.github.ckirby04.nemesis.engine.Villain
import kotlinx.serialization.Serializable

/** A parsed in-session note about one seat (or the whole table). */
@Serializable
data class SeatUpdate(
    val target: String = "all",
    val archetype: String? = null,
    val deltas: Map<String, Double> = emptyMap(),
    val note: String = "",
)

/**
 * Offline, keyword-based parsing — Nemesis' `_heuristic` fallbacks. Always
 * available; the Claude path (see [io.github.ckirby04.nemesis.data.remote.ClaudeService])
 * upgrades the same outputs when an API key is configured.
 */
object TableParser {

    /** Claude system prompt: description -> villain-model JSON. */
    val DESCRIPTION_SYSTEM = """
        You convert a poker player's plain-English description of their table/opponent pool into a JSON
        object of villain-model parameters for an exploitative simulator. Output ONLY raw JSON, no prose,
        no markdown fences.

        Fields (all numbers):
        - openRaisePct: fraction of hands the pool OPENS/RAISES (0.10 tight .. 0.45 very loose)
        - threebetPct: fraction they 3-bet (0.01 nitty .. 0.10 aggressive)
        - callOpenPct: fraction they CALL a raise with ("calls light/wide" => higher, up to ~0.6)
        - openSizeBb: typical open size in big blinds (e.g. 4, 6, 10)
        - station: calldown propensity 0..1 (1 = never folds postflop; "can't fold"/"sticky" => 0.7-0.9)
        - cbetFreq: 0..1
        - bluffFreq: 0..1 (passive pools low ~0.05; aggressive higher)
        - stackBb: typical effective stack in bb (e.g. 100, 150, 200)

        Map qualitative cues sensibly. "raise light with KJo" => openRaisePct ~0.30+.
        "call light" => callOpenPct ~0.5+. "can't get away from hands" => station ~0.85.
    """.trimIndent()

    /** Claude system prompt: seat note -> [SeatUpdate] JSON. */
    val UPDATE_SYSTEM = """
        You parse a poker player's in-session note about ONE seat (or the whole table) into a JSON update.
        Output ONLY raw JSON, no prose.

        Schema:
        { "target": "<UTG|UTG+1|MP|LJ|HJ|CO|BTN|SB|BB | left | right | all>",
          "archetype": "<nit|tag|lag|maniac|station|fish|reg> or null",
          "deltas": { "openRaisePct": 0.0, "callOpenPct": 0.0, "threebetPct": 0.0, "station": 0.0, "bluffFreq": 0.0 },
          "note": "<short human description>" }

        Rules: pick archetype if one is clearly implied, else null and use deltas (signed numbers).
        "bluffs rivers/turns" => bluffFreq +0.2. "calls everything"/"can't fold"/"sticky" => station +0.25 and
        callOpenPct +0.15. "folds a lot"/"nitty" => station -0.2, callOpenPct -0.1. "raises light"/"opens wide" =>
        openRaisePct +0.12, callOpenPct +0.1. "3bets a lot" => threebetPct +0.05. Relative references like
        "the guy on my left" => "left". Keep deltas small and sane. Always include note.
    """.trimIndent()

    private fun String.hasAny(vararg words: String) = words.any { it in this }

    /** Plain-English table description -> villain model (keyword heuristic). */
    fun parseDescription(text: String): Villain {
        val t = text.lowercase()
        var v = Villain()
        if (t.hasAny("light", "loose", "lag", "wide", "splashy", "raise a lot"))
            v = v.copy(openRaisePct = 0.32, callOpenPct = 0.52)
        if (t.hasAny("nit", "tight", "abc", "passive rock"))
            v = v.copy(openRaisePct = 0.12, callOpenPct = 0.20)
        if (t.hasAny("station", "can't fold", "cant fold", "sticky", "call down", "calls everything"))
            v = v.copy(station = 0.85, callOpenPct = maxOf(v.callOpenPct, 0.55))
        if (t.hasAny("aggressive", "3bet", "3-bet", "maniac"))
            v = v.copy(threebetPct = 0.08, bluffFreq = 0.25)

        // open size: "raises to 20", "opens 6bb", "raise 4x" (the word cues the number)
        Regex("""(?:raises?|opens?|to)\D{0,8}(\d+)""").find(t)?.let {
            v = v.copy(openSizeBb = it.groupValues[1].toDouble())
        }
        // Stack depth: prefer an explicit 'deep'/'stack'/'effective' cue so the open size isn't
        // mistaken for the stack ("raises 20bb ... 100bb deep" => 100). Else a bare Nbb >= 40bb.
        val stack = Regex("""(\d+)\s*(?:bb|big blinds)?\s*deep""").find(t)
            ?: Regex("""(?:stacks?|effective)\D{0,8}(\d+)""").find(t)
        if (stack != null) {
            v = v.copy(stackBb = stack.groupValues[1].toDouble())
        } else {
            Regex("""(\d+)\s*(?:bb|big blinds)\b""").findAll(t)
                .map { it.groupValues[1].toDouble() }
                .firstOrNull { it >= 40 && it != v.openSizeBb }
                ?.let { v = v.copy(stackBb = it) }
        }
        return v
    }

    /** In-session seat note -> [SeatUpdate] (keyword heuristic). */
    fun parseUpdate(text: String): SeatUpdate {
        // Normalise "utg1" / "utg + 1" to "utg+1", then try longer labels first so "utg+1" isn't read as "utg".
        val t = text.lowercase().replace(Regex("""\butg\s*\+?\s*1\b"""), "utg+1")
        val target = Positions.ALL.sortedByDescending { it.length }
            .firstOrNull { Regex("""(?<![\w+])${Regex.escape(it.lowercase())}(?![\w+])""").containsMatchIn(t) }
            ?: when {
                t.hasAny("my left", "on my left", " left", "next to me") -> "left"
                t.hasAny("my right", "on my right", " right") -> "right"
                else -> "all"
            }
        val archetype = (Archetypes.PRESETS.keys + Archetypes.ALIASES.keys)
            .firstOrNull { Regex("""\b$it\b""").containsMatchIn(t) }
            ?.let { Archetypes.ALIASES[it] ?: it }

        val d = LinkedHashMap<String, Double>()
        if (t.hasAny("bluff", "barrel", "fires", "stab")) d["bluffFreq"] = 0.2
        if (t.hasAny("calls everything", "can't fold", "cant fold", "sticky", "station", "call down", "never folds")) {
            d["station"] = 0.25; d["callOpenPct"] = 0.15
        }
        if (t.hasAny("folds a lot", "nitty", "tight", "weak tight", "folds too much")) {
            d["station"] = -0.2; d["callOpenPct"] = -0.1
        }
        if (t.hasAny("raises light", "opens wide", "loose", "splashy", "raising a lot")) {
            d["openRaisePct"] = 0.12; d["callOpenPct"] = 0.1
        }
        if (t.hasAny("3bet", "3-bet", "three bet", "reraises")) d["threebetPct"] = 0.05
        return SeatUpdate(target, archetype, d, text.trim().take(80))
    }
}
