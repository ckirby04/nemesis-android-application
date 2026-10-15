package io.github.ckirby04.nemesis.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One saved simulation. Structured payloads (villain, results, report grounding, follow-ups)
 * are JSON columns: they're always read whole, never queried into.
 */
@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val description: String,
    val villainJson: String,
    val positionsCsv: String,
    val handsPerCell: Int,
    val seats: Int,
    val rakeJson: String,
    val resultsJson: String,
    val parsedBy: String,
    val parseNote: String?,
    val seatReadsJson: String,
    val reportMarkdown: String? = null,
    val reportSource: String? = null,
    val retrievedIdsCsv: String? = null,
    @ColumnInfo(defaultValue = "[]") val followupsJson: String = "[]",
)

/** History-list projection: skips the large results/report columns. */
data class RunSummary(
    val id: Long,
    val createdAt: Long,
    val description: String,
    val positionsCsv: String,
    val handsPerCell: Int,
    val parsedBy: String,
    val hasReport: Boolean,
)

@Dao
interface RunDao {
    @Query(
        "SELECT id, createdAt, description, positionsCsv, handsPerCell, parsedBy, " +
            "reportMarkdown IS NOT NULL AS hasReport FROM runs ORDER BY createdAt DESC",
    )
    fun observeSummaries(): Flow<List<RunSummary>>

    @Query("SELECT * FROM runs WHERE id = :id")
    fun observe(id: Long): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: Long): RunEntity?

    @Insert
    suspend fun insert(run: RunEntity): Long

    @Query("UPDATE runs SET reportMarkdown = :markdown, reportSource = :source, retrievedIdsCsv = :retrievedIds, followupsJson = '[]' WHERE id = :id")
    suspend fun setReport(id: Long, markdown: String, source: String, retrievedIds: String)

    @Query("UPDATE runs SET followupsJson = :json WHERE id = :id")
    suspend fun setFollowups(id: Long, json: String)

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM runs")
    suspend fun deleteAll()
}

@Database(entities = [RunEntity::class], version = 1)
abstract class NemesisDatabase : RoomDatabase() {
    abstract fun runs(): RunDao
}
