package selvakn.ipv6diag.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XlatSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: XlatSummaryEntity)

    @Query("SELECT * FROM xlat_summaries WHERE session_id = :sessionId")
    suspend fun getBySession(sessionId: String): XlatSummaryEntity?

    @Query("DELETE FROM xlat_summaries WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
