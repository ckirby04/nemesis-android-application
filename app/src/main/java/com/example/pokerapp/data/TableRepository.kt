package com.example.pokerapp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.pokerapp.engine.Villain
import com.example.pokerapp.parsing.Archetypes
import com.example.pokerapp.parsing.SeatUpdate
import com.example.pokerapp.parsing.TableState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Outcome of applying a plain-English seat note. Empty [seats] means the target wasn't understood. */
data class NoteResult(val update: SeatUpdate, val seats: List<String>, val source: Source, val note: String?)

/** The live table (hero seat, per-seat reads and notes), persisted as JSON in DataStore. */
class TableRepository(private val store: DataStore<Preferences>, private val coach: CoachRepository) {
    private val key = stringPreferencesKey("table_state")
    private val json = Json { ignoreUnknownKeys = true }

    val table: Flow<TableState> = store.data.map { p ->
        p[key]?.let {
            try { json.decodeFromString<TableState>(it) } catch (e: SerializationException) { null }
        } ?: TableState(default = Villain())
    }

    suspend fun current(): TableState = table.first()

    private suspend fun edit(transform: (TableState) -> TableState) {
        val next = transform(current())
        store.edit { it[key] = json.encodeToString(next) }
    }

    suspend fun applyNote(text: String): NoteResult {
        val parsed = coach.parseUpdate(text)
        val seats = current().resolve(parsed.value.target)
        if (seats.isNotEmpty()) edit { it.apply(parsed.value) }
        return NoteResult(parsed.value, seats, parsed.source, parsed.note)
    }

    suspend fun setArchetype(pos: String, archetype: String) = edit { t ->
        t.setSeat(pos, Archetypes.apply(t.seats[pos] ?: t.default, archetype, emptyMap()))
            .let { it.copy(notes = it.notes + (pos to (it.notes[pos].orEmpty() + archetype))) }
    }

    suspend fun clearSeat(pos: String) = edit { it.setSeat(pos, null) }

    suspend fun setHeroPos(pos: String) = edit { it.copy(heroPos = pos, seats = it.seats - pos, notes = it.notes - pos) }

    suspend fun setSeatCount(n: Int) = edit { t ->
        val next = t.copy(nSeats = n)
        val labels = next.labels.toSet()
        next.copy(
            heroPos = if (t.heroPos in labels) t.heroPos else next.labels.last(),
            seats = t.seats.filterKeys { it in labels },
            notes = t.notes.filterKeys { it in labels },
        )
    }

    /** Unread seats (and new notes on them) start from the latest parsed table description. */
    suspend fun setDefault(v: Villain) = edit { it.copy(default = v) }

    suspend fun reset() = edit { it.reset() }
}
