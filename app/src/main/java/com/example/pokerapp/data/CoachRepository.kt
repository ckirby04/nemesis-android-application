package com.example.pokerapp.data

import com.anthropic.models.messages.OutputConfig
import com.example.pokerapp.advisor.Advisor
import com.example.pokerapp.advisor.Principle
import com.example.pokerapp.advisor.TableProfile
import com.example.pokerapp.engine.Villain
import com.example.pokerapp.parsing.SeatUpdate
import com.example.pokerapp.parsing.TableParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Who produced a piece of text: Claude, or the offline heuristics/templates. */
@Serializable
enum class Source { CLAUDE, OFFLINE }

/** A result plus where it came from; [note] explains an offline fallback ("No API key set"). */
data class Sourced<T>(val value: T, val source: Source, val note: String? = null)

/**
 * Every Claude-powered feature, each with Nemesis' offline fallback: the app works fully
 * without a key, and a key upgrades the parsing and prose.
 */
class CoachRepository(private val claude: ClaudeService) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private suspend fun <T> withFallback(offline: () -> T, online: suspend () -> T): Sourced<T> =
        try {
            Sourced(online(), Source.CLAUDE)
        } catch (e: ClaudeUnavailable) {
            Sourced(offline(), Source.OFFLINE, e.message)
        } catch (e: IllegalArgumentException) { // malformed JSON from the model
            Sourced(offline(), Source.OFFLINE, "Couldn't read Claude's answer")
        }

    /** Pull the JSON object out of a reply that may carry fences or stray prose. */
    private fun jsonObject(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "no JSON object in reply" }
        return raw.substring(start, end + 1)
    }

    suspend fun parseDescription(text: String): Sourced<Villain> = withFallback({ TableParser.parseDescription(text) }) {
        val raw = claude.complete(TableParser.DESCRIPTION_SYSTEM, text, maxTokens = 4_000, effort = OutputConfig.Effort.LOW)
        json.decodeFromString<Villain>(jsonObject(raw)).clamped()
    }

    suspend fun parseUpdate(text: String): Sourced<SeatUpdate> = withFallback({ TableParser.parseUpdate(text) }) {
        val raw = claude.complete(TableParser.UPDATE_SYSTEM, text, maxTokens = 4_000, effort = OutputConfig.Effort.LOW)
        json.decodeFromString<SeatUpdate>(jsonObject(raw)).let { it.copy(note = it.note.ifBlank { text.trim().take(80) }) }
    }

    suspend fun report(profile: TableProfile, retrieved: List<Principle>): Sourced<String> {
        val r = withFallback({ Advisor.offlineReport(profile, retrieved) }) {
            claude.complete(Advisor.GEN_SYSTEM, Advisor.reportPrompt(profile, retrieved))
        }
        // Say why in the report itself when we fell back, as Nemesis does.
        return if (r.source == Source.OFFLINE) {
            r.copy(value = Advisor.offlineReport(profile, retrieved, note = "Offline template — ${r.note}. Add an API key in Settings for a Claude-written version."))
        } else r
    }

    suspend fun followup(question: String, profile: TableProfile, retrieved: List<Principle>, history: List<Pair<String, String>>): Sourced<String> {
        val grounding = Advisor.followupGrounding(question, retrieved)
        return withFallback({ Advisor.offlineFollowup(question, grounding) }) {
            claude.complete(Advisor.FOLLOWUP_SYSTEM, Advisor.followupPrompt(question, profile, grounding), history.takeLast(4), maxTokens = 4_000)
        }
    }

    /** Cheapest possible round-trip, for the Settings "Test key" button. */
    suspend fun testKey(): Result<Unit> = try {
        claude.complete("Reply with the single word OK.", "ping", maxTokens = 1_000, effort = OutputConfig.Effort.LOW)
        Result.success(Unit)
    } catch (e: ClaudeUnavailable) {
        Result.failure(e)
    }
}
