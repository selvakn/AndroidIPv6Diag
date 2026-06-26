package com.lenovo.mesh.ipv6diag.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: DiagnosticSessionEntity)

    @Query("SELECT * FROM diagnostic_sessions ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<DiagnosticSessionEntity>>

    @Query("SELECT * FROM diagnostic_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DiagnosticSessionEntity?

    @Query("SELECT COUNT(*) FROM diagnostic_sessions")
    suspend fun count(): Int

    @Query("DELETE FROM diagnostic_sessions WHERE id IN (SELECT id FROM diagnostic_sessions ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}
