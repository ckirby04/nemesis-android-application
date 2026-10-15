package io.github.ckirby04.nemesis.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.ckirby04.nemesis.engine.Rake
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Where the effective API key came from. */
enum class KeySource { NONE, SAVED, DEVELOPER }

data class AppSettings(
    /** The key requests use: the one saved in Settings, else the debug build's developer key. */
    val apiKey: String = "",
    val keySource: KeySource = KeySource.NONE,
    /** Only the key the user saved in Settings (what the Settings field edits). */
    val savedKey: String = "",
    val model: String = DEFAULT_MODEL,
    val handsPerCell: Int = 600,
    val fullGrid: Boolean = true,
    val rake: Rake = Rake(),
    val positions: Set<String> = setOf("BTN", "CO"),
) {
    val claudeEnabled: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        val MODELS = listOf(
            "claude-opus-5-5" to "Claude Opus 5.5 — best reads (default)",
            "claude-sonnet-5-5" to "Claude Sonnet 5.5 — faster, cheaper",
            "claude-haiku-5-5" to "Claude Haiku 5.5 — fastest, cheapest",
        )
    }
}

/**
 * User preferences in DataStore. The API key lives in its own store ([secrets]) so it can be
 * excluded from cloud backup and device transfer (see res/xml/data_extraction_rules.xml).
 */
class SettingsRepository(
    private val prefs: DataStore<Preferences>,
    private val secrets: DataStore<Preferences>,
    /** Debug-only fallback key from local.properties (see app/build.gradle.kts); empty in release. */
    private val developerKey: String = "",
) {
    private object Keys {
        val API_KEY = stringPreferencesKey("anthropic_api_key")
        val MODEL = stringPreferencesKey("model")
        val HANDS = intPreferencesKey("hands_per_cell")
        val FULL_GRID = booleanPreferencesKey("full_grid")
        val RAKE_ON = booleanPreferencesKey("rake_enabled")
        val RAKE_PCT = doublePreferencesKey("rake_pct")
        val RAKE_CAP = doublePreferencesKey("rake_cap")
        val POSITIONS = stringSetPreferencesKey("positions")
    }

    val settings: Flow<AppSettings> = combine(prefs.data, secrets.data) { p, s ->
        val d = AppSettings()
        val saved = s[Keys.API_KEY].orEmpty()
        AppSettings(
            apiKey = saved.ifBlank { developerKey },
            keySource = when {
                saved.isNotBlank() -> KeySource.SAVED
                developerKey.isNotBlank() -> KeySource.DEVELOPER
                else -> KeySource.NONE
            },
            savedKey = saved,
            model = p[Keys.MODEL] ?: d.model,
            handsPerCell = p[Keys.HANDS] ?: d.handsPerCell,
            fullGrid = p[Keys.FULL_GRID] ?: d.fullGrid,
            rake = Rake(
                pct = p[Keys.RAKE_PCT] ?: d.rake.pct,
                capBb = p[Keys.RAKE_CAP] ?: d.rake.capBb,
                enabled = p[Keys.RAKE_ON] ?: d.rake.enabled,
            ),
            positions = p[Keys.POSITIONS] ?: d.positions,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setApiKey(key: String) = secrets.edit { it[Keys.API_KEY] = key.trim() }
    suspend fun setModel(model: String) = prefs.edit { it[Keys.MODEL] = model }
    suspend fun setHandsPerCell(n: Int) = prefs.edit { it[Keys.HANDS] = n }
    suspend fun setFullGrid(on: Boolean) = prefs.edit { it[Keys.FULL_GRID] = on }
    suspend fun setPositions(positions: Set<String>) = prefs.edit { it[Keys.POSITIONS] = positions }
    suspend fun setRake(rake: Rake) = prefs.edit {
        it[Keys.RAKE_ON] = rake.enabled
        it[Keys.RAKE_PCT] = rake.pct
        it[Keys.RAKE_CAP] = rake.capBb
    }
}
