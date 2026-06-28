package com.aicodeeditor.feature.workspace.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aicodeeditor.feature.workspace.data.local.entity.RemoteServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteServerDao {
    @Query("SELECT * FROM remote_servers")
    fun getAllServers(): Flow<List<RemoteServerEntity>>

    @Query("SELECT * FROM remote_servers WHERE id = :id")
    suspend fun getServerById(id: String): RemoteServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: RemoteServerEntity)

    @Update
    suspend fun updateServer(server: RemoteServerEntity)

    @Delete
    suspend fun deleteServer(server: RemoteServerEntity)
}
