package com.example.pokerapp.data

import com.example.pokerapp.engine.Hands
import com.example.pokerapp.engine.Simulator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

sealed interface SimState {
    data object Idle : SimState
    data object Parsing : SimState
    data class Running(val progress: Float, val villainTag: String, val source: Source) : SimState
    data class Done(val runId: Long) : SimState
    data class Failed(val message: String) : SimState
}

/**
 * Runs parse -> simulate -> save in an application-scoped coroutine, so a run keeps going
 * while the user switches tabs or rotates the phone. Screens observe [state].
 */
class SimulationRunner(
    private val scope: CoroutineScope,
    private val coach: CoachRepository,
    private val runs: RunRepository,
    private val tables: TableRepository,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow<SimState>(SimState.Idle)
    val state: StateFlow<SimState> = _state.asStateFlow()
    private var job: Job? = null

    val isBusy: Boolean get() = _state.value is SimState.Parsing || _state.value is SimState.Running

    fun start(description: String, positions: List<String>, useTableReads: Boolean) {
        if (isBusy) return
        job = scope.launch {
            try {
                _state.value = SimState.Parsing
                val s = settings.current()
                val villain = coach.parseDescription(description)
                tables.setDefault(villain.value)
                val table = tables.current()
                val seatModels = if (useTableReads) table.seatModels() else null
                val tag = villain.value.describe()

                _state.value = SimState.Running(0f, tag, villain.source)
                val lastPct = AtomicInteger()
                val results = Simulator.runStats(
                    villain = villain.value,
                    positions = positions,
                    nHands = s.handsPerCell,
                    nSeats = table.nSeats,
                    subset = if (s.fullGrid) null else Hands.DEFAULT_SUBSET,
                    seatModels = seatModels,
                    rake = s.rake,
                ) { p ->
                    // Called from worker threads, possibly out of order; publish each new whole percent once.
                    val pct = (p * 100).toInt()
                    if (pct > lastPct.getAndAccumulate(pct, ::maxOf)) _state.value = SimState.Running(p, tag, villain.source)
                }
                val id = runs.save(description, villain, positions, s.handsPerCell, table.nSeats, s.rake, results, seatModels.orEmpty())
                _state.value = SimState.Done(id)
            } catch (e: CancellationException) {
                _state.value = SimState.Idle
                throw e
            } catch (e: Exception) {
                _state.value = SimState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancel() { job?.cancel() }

    /** Acknowledge a terminal state (after navigating to the result or showing the error). */
    fun consume() {
        if (_state.value is SimState.Done || _state.value is SimState.Failed) _state.value = SimState.Idle
    }
}
