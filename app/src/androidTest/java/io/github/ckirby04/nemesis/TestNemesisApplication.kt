package io.github.ckirby04.nemesis

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.ckirby04.nemesis.data.db.NemesisDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import java.util.UUID

/** Test application: never touches the real database, settings, or API key. */
class TestNemesisApplication : NemesisApplication() {
    override fun createContainer(): AppContainer = freshContainer(this)

    companion object {
        /** A container over an in-memory database and brand-new DataStore files, always offline. */
        fun freshContainer(context: Context): AppContainer {
            val id = UUID.randomUUID().toString()
            fun store(name: String): DataStore<Preferences> = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { context.preferencesDataStoreFile("test-$id-$name") },
            )
            return AppContainer(
                context = context,
                db = Room.inMemoryDatabaseBuilder(context, NemesisDatabase::class.java).build(),
                prefs = store("settings"),
                secrets = store("secrets"),
                table = store("table"),
                developerKey = "",
            )
        }
    }
}

/**
 * Gives each test its own empty app state, with simulation settings turned down so end-to-end
 * runs finish in seconds. Must wrap (run before) any rule that launches an activity.
 */
class FreshAppRule : ExternalResource() {
    lateinit var container: AppContainer
        private set

    override fun before() {
        val app = ApplicationProvider.getApplicationContext<NemesisApplication>()
        container = TestNemesisApplication.freshContainer(app)
        runBlocking { container.settings.setHandsPerCell(150) }
        app.container = container
    }
}
