package io.github.ckirby04.nemesis.data

import io.github.ckirby04.nemesis.advisor.KnowledgeBase
import io.github.ckirby04.nemesis.advisor.Principle
import io.github.ckirby04.nemesis.data.db.RunDao
import io.github.ckirby04.nemesis.data.db.RunEntity
import io.github.ckirby04.nemesis.data.db.RunSummary
import io.github.ckirby04.nemesis.engine.Cell
import io.github.ckirby04.nemesis.engine.Rake
import io.github.ckirby04.nemesis.engine.Villain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Followup(val question: String, val answer: String, val source: Source)

/** A saved run in domain form. [results] is `position -> hand -> Cell`. */
data class SavedRun(
    val id: Long,
    val createdAt: Long,
    val description: String,
    val villain: Villain,
    val positions: List<String>,
    val handsPerCell: Int,
    val seats: Int,
    val rake: Rake,
    val results: Map<String, Map<String, Cell>>,
    val parsedBy: Source,
    val parseNote: String?,
    val seatReads: Map<String, Villain>,
    val report: String?,
    val reportSource: Source?,
    val retrieved: List<Principle>,
    val followups: List<Followup>,
) {
    /** The description with live-table seat reads appended, as fed to the advisor. */
    val advisorDescription: String
        get() = if (seatReads.isEmpty()) description
        else "$description | Seat reads: " + seatReads.entries.joinToString("; ") { (pos, v) -> "$pos=${v.describe()}" }
}

class RunRepository(private val dao: RunDao) {
    private val json = Json { ignoreUnknownKeys = true }

    val summaries: Flow<List<RunSummary>> = dao.observeSummaries()

    fun observe(id: Long): Flow<SavedRun?> = dao.observe(id).map { it?.toDomain() }

    suspend fun get(id: Long): SavedRun? = dao.get(id)?.toDomain()

    suspend fun save(
        description: String,
        villain: Sourced<Villain>,
        positions: List<String>,
        handsPerCell: Int,
        seats: Int,
        rake: Rake,
        results: Map<String, Map<String, Cell>>,
        seatReads: Map<String, Villain>,
    ): Long = dao.insert(
        RunEntity(
            createdAt = System.currentTimeMillis(),
            description = description,
            villainJson = json.encodeToString(villain.value),
            positionsCsv = positions.joinToString(","),
            handsPerCell = handsPerCell,
            seats = seats,
            rakeJson = json.encodeToString(rake),
            resultsJson = json.encodeToString(results),
            parsedBy = villain.source.name,
            parseNote = villain.note,
            seatReadsJson = json.encodeToString(seatReads),
        ),
    )

    /** Store a (re)generated report; clears follow-ups, which were grounded on the old one. */
    suspend fun setReport(id: Long, report: Sourced<String>, retrieved: List<Principle>) =
        dao.setReport(id, report.value, report.source.name, retrieved.joinToString(",") { it.id })

    suspend fun setFollowups(id: Long, followups: List<Followup>) = dao.setFollowups(id, json.encodeToString(followups))

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll() = dao.deleteAll()

    private fun RunEntity.toDomain(): SavedRun {
        val byId = KnowledgeBase.PRINCIPLES.associateBy { it.id }
        return SavedRun(
            id = id,
            createdAt = createdAt,
            description = description,
            villain = json.decodeFromString(villainJson),
            positions = positionsCsv.split(",").filter { it.isNotBlank() },
            handsPerCell = handsPerCell,
            seats = seats,
            rake = json.decodeFromString(rakeJson),
            results = json.decodeFromString(resultsJson),
            parsedBy = Source.valueOf(parsedBy),
            parseNote = parseNote,
            seatReads = json.decodeFromString(seatReadsJson),
            report = reportMarkdown,
            reportSource = reportSource?.let(Source::valueOf),
            retrieved = retrievedIdsCsv.orEmpty().split(",").mapNotNull { byId[it] },
            followups = json.decodeFromString(followupsJson),
        )
    }
}
