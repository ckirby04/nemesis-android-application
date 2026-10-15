package com.example.pokerapp

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.pokerapp.data.ClaudeService
import com.example.pokerapp.data.CoachRepository
import com.example.pokerapp.data.RunRepository
import com.example.pokerapp.data.SettingsRepository
import com.example.pokerapp.data.SimulationRunner
import com.example.pokerapp.data.TableRepository
import com.example.pokerapp.data.db.NemesisDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

private val Context.prefsStore by preferencesDataStore("settings")
private val Context.secretsStore by preferencesDataStore("secrets")
private val Context.tableStore by preferencesDataStore("table")

/** Manual dependency container: one instance of each repository for the whole app. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob())
    private val db = Room.databaseBuilder(context, NemesisDatabase::class.java, "nemesis.db").build()

    val settings = SettingsRepository(context.prefsStore, context.secretsStore)
    val coach = CoachRepository(ClaudeService(settings))
    val runs = RunRepository(db.runs())
    val tables = TableRepository(context.tableStore, coach)
    val simulation = SimulationRunner(appScope, coach, runs, tables, settings)
}

class NemesisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
