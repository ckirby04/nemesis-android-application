package io.github.ckirby04.nemesis

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import io.github.ckirby04.nemesis.data.ClaudeService
import io.github.ckirby04.nemesis.data.CoachRepository
import io.github.ckirby04.nemesis.data.RunRepository
import io.github.ckirby04.nemesis.data.SettingsRepository
import io.github.ckirby04.nemesis.data.SimulationRunner
import io.github.ckirby04.nemesis.data.TableRepository
import io.github.ckirby04.nemesis.data.db.NemesisDatabase
import io.github.ckirby04.nemesis.sim.SimNotifications
import io.github.ckirby04.nemesis.sim.SimulationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

private val Context.prefsStore by preferencesDataStore("settings")
private val Context.secretsStore by preferencesDataStore("secrets")
private val Context.tableStore by preferencesDataStore("table")

/**
 * Manual dependency container: one instance of each repository for the whole app. Storage is
 * passed in so instrumented tests can swap in an in-memory database and throwaway DataStores.
 */
class AppContainer(
    context: Context,
    db: NemesisDatabase,
    prefs: DataStore<Preferences>,
    secrets: DataStore<Preferences>,
    table: DataStore<Preferences>,
    developerKey: String,
) {
    private val appScope = CoroutineScope(SupervisorJob())

    val settings = SettingsRepository(prefs, secrets, developerKey)
    val coach = CoachRepository(ClaudeService(settings))
    val runs = RunRepository(db.runs())
    val tables = TableRepository(table, coach)
    val simulation = SimulationRunner(appScope, coach, runs, tables, settings) {
        SimulationService.start(context.applicationContext)
    }

    companion object {
        fun production(context: Context) = AppContainer(
            context = context,
            db = Room.databaseBuilder(context, NemesisDatabase::class.java, "nemesis.db").build(),
            prefs = context.prefsStore,
            secrets = context.secretsStore,
            table = context.tableStore,
            developerKey = BuildConfig.DEV_ANTHROPIC_KEY,
        )
    }
}

open class NemesisApplication : Application() {
    lateinit var container: AppContainer
        @VisibleForTesting internal set

    /** Overridden by the instrumented-test application. */
    protected open fun createContainer(): AppContainer = AppContainer.production(this)

    override fun onCreate() {
        super.onCreate()
        SimNotifications.createChannels(this)
        container = createContainer()
    }
}
