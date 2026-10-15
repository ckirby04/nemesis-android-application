package com.example.pokerapp.advisor

import com.example.pokerapp.engine.Cell
import com.example.pokerapp.engine.Hands
import com.example.pokerapp.engine.Villain
import kotlinx.serialization.Serializable
import java.util.Locale

/** Statistical profile of a sim run: per-class EV plus table-feature flags. Drives retrieval and the report. */
@Serializable
data class TableProfile(
    val refPos: String,
    val classEv: Map<String, Double>,
    val flags: List<String>,
    val villain: Villain,
    val desc: String,
) {
    /** Retrieval query: the description plus the sim's OUTPUT features, so the principles fit what the numbers say. */
    val query: String get() = "$desc. " + flags.joinToString(". ")
}

/**
 * RAG advisor, ported from Nemesis' `advisor.py`:
 * 1. [profile] extracts per-class EV and feature flags from the sim results,
 * 2. [TfidfRetriever] pulls the principles that match that profile,
 * 3. the report is written by Claude grounded on them ([GEN_SYSTEM] + [userPrompt]),
 *    or by the deterministic [offlineReport] when no API key is set.
 */
object Advisor {
    /** Hand-class buckets used for profile extraction. Order is the order shown in reports. */
    val CLASSES: Map<String, List<String>> = linkedMapOf(
        "premiums" to listOf("AA", "KK", "QQ", "JJ", "TT", "AKs", "AKo"),
        "broadways" to listOf("AQs", "AQo", "AJs", "AJo", "ATs", "KQs", "KQo", "KJs", "KJo", "QJs", "QJo", "JTs", "JTo"),
        "medium_pairs" to listOf("99", "88", "77"),
        "suited_connectors" to listOf("T9s", "98s", "87s", "76s", "65s", "54s"),
        "small_pairs" to listOf("55", "44", "33", "22"),
        "suited_aces" to listOf("A5s", "A2s"),
        "offsuit_marginal" to listOf("A9o", "K9o", "Q9o", "J9o", "T9o"),
        "trash" to listOf("72o", "J4o", "T6o"),
    )

    val retriever by lazy { TfidfRetriever(KnowledgeBase.PRINCIPLES) }

    private fun avg(evmap: Map<String, Cell>, hands: List<String>): Double {
        val vals = hands.mapNotNull { evmap[Hands.normalize(it)]?.mean }
        return if (vals.isEmpty()) Double.NaN else vals.average()
    }

    fun profile(results: Map<String, Map<String, Cell>>, villain: Villain, desc: String, refPos: String? = null): TableProfile {
        val pos = refPos ?: results.keys.first()
        val evmap = results.getValue(pos)
        val ce = CLASSES.mapValues { (_, hs) -> avg(evmap, hs) }

        val flags = mutableListOf<String>()
        val sc = ce.getValue("suited_connectors")
        val sp = ce.getValue("small_pairs")
        flags += when {
            sc > 1 -> "suited connectors are profitable to open in position"
            sc < -0.5 -> "suited connectors are losing as opens; speculation is overpriced"
            else -> "suited connectors are roughly break-even as opens"
        }
        if (sp < -0.5) flags += "small pairs lose as opens at this depth (set-mining tax)"
        else if (sp > 1) flags += "small pairs are profitable, helped by depth or sticky payoffs"
        if (ce.getValue("premiums") > 15) flags += "premium hands print heavily; value gets paid"

        if (villain.station >= 0.7) flags += "opponents are sticky calling stations who can't fold"
        if (villain.openRaisePct >= 0.28) flags += "the pool opens and raises light/wide"
        if (villain.callOpenPct >= 0.45) flags += "the pool calls raises light/wide"
        if (villain.openSizeBb >= 8) flags += "standard opens are large (${fmt0(villain.openSizeBb)}bb), taxing speculation"
        if (villain.stackBb < 130) flags += "stacks are around ${fmt0(villain.stackBb)}bb (not deep enough for cheap set-mining)"
        else if (villain.stackBb >= 150) flags += "stacks are deep (${fmt0(villain.stackBb)}bb), improving implied odds"
        if (villain.bluffFreq >= 0.2) flags += "opponents bluff and raise aggressively"

        return TableProfile(pos, ce, flags, villain, desc)
    }

    fun retrieve(profile: TableProfile, k: Int = 5): List<Principle> =
        retriever.retrieve(profile.query, k).map { it.principle }

    // ---------------------------------------------------------------- prompts

    val GEN_SYSTEM = """
        You are a sharp, concise live low-stakes poker coach. You are given:
        (1) a player's table description, (2) statistical output from an exploitative
        simulator (per-hand-class EV in big blinds won each time the hand is opened), and
        (3) retrieved strategy principles to ground your advice.

        Write a short, digestible report with these sections:
        - **Table read**: 2-3 sentences on what kind of pool this is.
        - **What the numbers say**: translate the per-class EV into plain English.
        - **How to play it**: 4-6 concrete, specific tips tailored to THIS table.
        - **Leaks to avoid**: 2-3 traps given this pool.
        Ground every claim in the retrieved principles; do not invent poker theory beyond
        them. Be direct and practical. No hedging filler. Use bb figures from the metrics.
        Format in Markdown with ## headings and - bullets; it is read on a phone screen.
    """.trimIndent()

    val FOLLOWUP_SYSTEM = """
        You are a concise live low-stakes poker coach answering a follow-up question. You are
        given the table profile and simulator metrics from an earlier analysis, retrieved
        strategy principles, and the prior conversation. Answer the new question directly and
        practically, grounded in the principles and the metrics. Keep it short (a few sentences,
        phone-readable). Don't invent theory beyond the retrieved principles. If the question
        needs a fresh simulation to answer well, say so and suggest re-running the sim with
        different settings.
    """.trimIndent()

    fun metricsBlob(p: TableProfile): String = buildList {
        add("Reference position: ${p.refPos}")
        add("Per-class avg bb won per open:")
        for ((c, ev) in p.classEv) add("  - ${c.replace('_', ' ')}: ${fmtSigned(ev, 2)} bb")
        add("Observed table features: " + p.flags.joinToString("; "))
        val v = p.villain
        add("Model: open ${pct(v.openRaisePct)}, call ${pct(v.callOpenPct)}, station ${"%.2f".format(Locale.US, v.station)}, " +
            "open size ${fmt0(v.openSizeBb)}bb, stacks ${fmt0(v.stackBb)}bb.")
    }.joinToString("\n")

    private fun grounding(retrieved: List<Principle>) =
        retrieved.joinToString("\n\n") { "[${it.title}]\n${it.text}" }

    fun reportPrompt(p: TableProfile, retrieved: List<Principle>): String =
        "TABLE DESCRIPTION:\n${p.desc}\n\n" +
            "SIMULATOR METRICS:\n${metricsBlob(p)}\n\n" +
            "RETRIEVED PRINCIPLES:\n${grounding(retrieved)}\n\n" +
            "Write the report."

    /** Principles for a follow-up: the run's original hits merged with hits for the question itself. */
    fun followupGrounding(question: String, retrieved: List<Principle>): List<Principle> =
        (retrieved + retriever.retrieve(question, 3).map { it.principle }).distinctBy { it.id }.take(6)

    fun followupPrompt(question: String, p: TableProfile, grounding: List<Principle>): String =
        "TABLE PROFILE + METRICS:\n${metricsBlob(p)}\n\n" +
            "RETRIEVED PRINCIPLES:\n${grounding(grounding)}\n\n" +
            "FOLLOW-UP QUESTION:\n$question"

    /** Offline follow-up: surface the single principle most relevant to the question. */
    fun offlineFollowup(question: String, grounding: List<Principle>): String {
        val top = retriever.retrieve(question, 1).firstOrNull()?.principle ?: grounding.first()
        val gist = top.text.split(". ").first().trimEnd('.') + "."
        return "Most relevant principle — **${top.title}**: $gist\n\n" +
            "*(Offline answer. Add an Anthropic API key in Settings for full conversational answers.)*"
    }

    // ---------------------------------------------------------------- offline report

    private fun verdict(x: Double) = when {
        x.isNaN() -> "not simulated"
        x > 8 -> "a strong open"
        x > 1 -> "profitable"
        x > -1 -> "roughly break-even"
        else -> "a leak — fold these"
    }

    /** Deterministic report assembled from the features + retrieved principles. */
    fun offlineReport(p: TableProfile, retrieved: List<Principle>, note: String? = null): String {
        val ce = p.classEv
        val v = p.villain
        fun ev(c: String) = ce.getValue(c)
        val out = mutableListOf<String>()

        out += "# Table report (${p.refPos})\n"
        out += "## Table read"
        val read = mutableListOf(if (v.openRaisePct >= 0.28) "loose, raising wide" else "relatively tight")
        if (v.station >= 0.7) read += "very sticky postflop (calling stations who can't fold)"
        if (v.openSizeBb >= 8) read += "using large ${fmt0(v.openSizeBb)}bb opens"
        read += "~${fmt0(v.stackBb)}bb deep"
        out += "This is a " + read.joinToString(", ") + " pool. Against this profile your edge is value, " +
            "not bluffs, and your range discipline preflop matters more than fancy play.\n"

        out += "## What the numbers say"
        out += "- Premiums: **${fmtSigned(ev("premiums"))} bb/open** — ${verdict(ev("premiums"))}. Value gets paid; bet them relentlessly."
        out += "- Broadways: **${fmtSigned(ev("broadways"))} bb/open** — ${verdict(ev("broadways"))}."
        out += "- Medium pairs (99-77): **${fmtSigned(ev("medium_pairs"))} bb/open** — ${verdict(ev("medium_pairs"))}."
        out += "- Suited connectors: **${fmtSigned(ev("suited_connectors"))} bb/open** — ${verdict(ev("suited_connectors"))}."
        out += "- Small pairs (55-22): **${fmtSigned(ev("small_pairs"))} bb/open** — ${verdict(ev("small_pairs"))} " +
            "(set-mining tax at this depth/price)."
        out += "- Suited aces: **${fmtSigned(ev("suited_aces"))} bb/open** — ${verdict(ev("suited_aces"))}."
        out += "- Offsuit junk/trash: **${fmtSigned(ev("trash"))} bb/open** — clear folds.\n"

        out += "## How to play it"
        val tips = mutableListOf<String>()
        if (v.station >= 0.7) tips += "Value-bet thinner and bigger than feels comfortable, and **stop bluffing** — " +
            "fold equity is low, so barreling air just donates chips."
        if (v.openSizeBb >= 8 || v.stackBb < 130) tips += "Treat the large opens / ~100bb depth as a signal to " +
            "**fold most speculative cold-calls**; you're not getting the 10–15x implied price set-mining needs."
        tips += if (ev("suited_connectors") > 0) "Play suited connectors **in position and cheap** (closing the action or " +
            "isolating), where their EV is positive — not as out-of-position calls vs a raise."
        else "Tighten on suited connectors as opens here; the price overruns their implied odds."
        tips += "**Isolate weak limpers** with a raise rather than over-limping behind, to take initiative, thin the " +
            "field, and fight the rake."
        if (v.openRaisePct >= 0.28 || v.callOpenPct >= 0.45) tips += "Against their wide opens, keep a **linear, " +
            "value-heavy 3-bet range** — punish the trash they raise, don't flat wide out of position."
        tips += "Lean on **range advantage on high boards** (e.g. AK on K-high): c-bet for value across streets " +
            "against opponents who can't release a worse pair."
        tips.forEach { out += "- $it" }

        out += "\n## Leaks to avoid"
        out += "- Bluffing into stations because they 'fold to pressure' — the classic recency trap."
        out += "- Limp-calling speculative hands out of position when the pot gets raised."
        out += "- Trusting a hot run over a real sample — track win rate by position and hand class before widening.\n"

        out += "---\n*Grounded in: " + retrieved.joinToString(", ") { it.title } + ".*"
        if (note != null) out += "\n*($note)*"
        return out.joinToString("\n")
    }

    // ---------------------------------------------------------------- formatting

    private fun fmt0(x: Double) = "%.0f".format(Locale.US, x)
    private fun pct(x: Double) = "%.0f%%".format(Locale.US, x * 100)
    private fun fmtSigned(x: Double, decimals: Int = 1) =
        if (x.isNaN()) "n/a" else "%+.${decimals}f".format(Locale.US, x)
}
