package com.example.pokerapp.parsing

import com.example.pokerapp.engine.Positions
import com.example.pokerapp.engine.Villain
import kotlinx.serialization.Serializable

object Archetypes {
    val PRESETS: Map<String, Villain.() -> Villain> = linkedMapOf(
        "nit" to { copy(openRaisePct = 0.10, threebetPct = 0.02, callOpenPct = 0.16, station = 0.30, bluffFreq = 0.04) },
        "tag" to { copy(openRaisePct = 0.18, threebetPct = 0.05, callOpenPct = 0.28, station = 0.45, bluffFreq = 0.12) },
        "lag" to { copy(openRaisePct = 0.34, threebetPct = 0.09, callOpenPct = 0.40, station = 0.45, bluffFreq = 0.28) },
        "maniac" to { copy(openRaisePct = 0.45, threebetPct = 0.12, callOpenPct = 0.45, station = 0.50, bluffFreq = 0.40) },
        "station" to { copy(openRaisePct = 0.14, threebetPct = 0.02, callOpenPct = 0.58, station = 0.88, bluffFreq = 0.05) },
        "fish" to { copy(openRaisePct = 0.22, threebetPct = 0.02, callOpenPct = 0.55, station = 0.80, bluffFreq = 0.10) },
        "reg" to { copy(openRaisePct = 0.20, threebetPct = 0.06, callOpenPct = 0.26, station = 0.42, bluffFreq = 0.14) },
    )

    val ALIASES = mapOf(
        "calling station" to "station", "whale" to "fish", "donk" to "fish", "splashy" to "fish",
        "rock" to "nit", "tight aggressive" to "tag", "loose aggressive" to "lag",
        "tight passive" to "nit", "loose passive" to "station", "regular" to "reg",
    )

    /**
     * Apply an archetype preset (unknown names are ignored rather than failing) and
     * additive deltas keyed by [Villain] field name, then clamp to sane ranges.
     */
    fun apply(base: Villain, archetype: String?, deltas: Map<String, Double>): Villain {
        var v = base
        val key = archetype?.lowercase()?.let { ALIASES[it] ?: it }
        PRESETS[key]?.let { v = v.it() }
        for ((k, d) in deltas) v = when (k) {
            "openRaisePct" -> v.copy(openRaisePct = v.openRaisePct + d)
            "threebetPct" -> v.copy(threebetPct = v.threebetPct + d)
            "callOpenPct" -> v.copy(callOpenPct = v.callOpenPct + d)
            "station" -> v.copy(station = v.station + d)
            "bluffFreq" -> v.copy(bluffFreq = v.bluffFreq + d)
            "cbetFreq" -> v.copy(cbetFreq = v.cbetFreq + d)
            else -> v
        }
        return v.clamped()
    }
}

/**
 * A live, incrementally-updatable picture of the table: a default read plus per-seat
 * overrides and notes, keyed by position label. Immutable — updates return a copy.
 */
@Serializable
data class TableState(
    val default: Villain,
    val heroPos: String = "BTN",
    val nSeats: Int = 9,
    val seats: Map<String, Villain> = emptyMap(),
    val notes: Map<String, List<String>> = emptyMap(),
) {
    val labels: List<String> get() = Positions.forSeats(nSeats)

    /** Per-seat models in the shape [com.example.pokerapp.engine.Simulator.runStats] takes; null when every seat uses the default. */
    fun seatModels(): Map<String, Villain>? = seats.ifEmpty { null }

    /** Position labels a target refers to: a label, left/right of hero, or the whole table. */
    fun resolve(target: String): List<String> {
        val t = target.trim().lowercase()
        if (t in setOf("all", "table", "everyone", "everybody", "whole table")) return labels.filter { it != heroPos }
        val up = target.trim().uppercase()
        if (up in labels) return listOf(up)
        val i = labels.indexOf(heroPos).coerceAtLeast(0)
        return when (t) {
            "left", "my left", "next", "behind", "to my left" -> listOf(labels[(i + 1) % labels.size])
            "right", "my right", "previous", "before", "to my right" -> listOf(labels[(i - 1 + labels.size) % labels.size])
            else -> emptyList()
        }
    }

    fun apply(update: SeatUpdate): TableState {
        var s = seats
        var n = notes
        for (pos in resolve(update.target)) {
            s = s + (pos to Archetypes.apply(s[pos] ?: default, update.archetype, update.deltas))
            if (update.note.isNotBlank()) n = n + (pos to (n[pos].orEmpty() + update.note))
        }
        return copy(seats = s, notes = n)
    }

    fun setSeat(pos: String, villain: Villain?): TableState =
        if (villain == null) copy(seats = seats - pos, notes = notes - pos) else copy(seats = seats + (pos to villain))

    fun reset(): TableState = copy(seats = emptyMap(), notes = emptyMap())

    /** The description plus seat reads, as fed to the advisor. */
    fun augmentedDescription(desc: String): String {
        if (notes.isEmpty()) return desc
        val reads = notes.entries.joinToString("; ") { (pos, ns) -> "$pos=${ns.joinToString("/")}" }
        return "$desc | Seat reads: $reads"
    }
}
