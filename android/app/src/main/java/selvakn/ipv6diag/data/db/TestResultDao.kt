package selvakn.ipv6diag.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TestResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<TestResultEntity>)

    @Query("SELECT * FROM test_results WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: String): List<TestResultEntity>
}
