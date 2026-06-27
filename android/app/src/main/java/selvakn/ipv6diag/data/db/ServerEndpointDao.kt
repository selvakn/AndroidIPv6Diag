package selvakn.ipv6diag.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ServerEndpointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(endpoint: ServerEndpointEntity)

    @Query("SELECT * FROM server_endpoints WHERE is_default = 1 LIMIT 1")
    suspend fun getDefault(): ServerEndpointEntity?

    @Query("SELECT * FROM server_endpoints WHERE is_default = 0 LIMIT 1")
    suspend fun getCustom(): ServerEndpointEntity?

    @Query("DELETE FROM server_endpoints WHERE is_default = 0")
    suspend fun deleteCustom()
}
