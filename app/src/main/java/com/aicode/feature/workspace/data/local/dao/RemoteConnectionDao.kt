package com.aicode.feature.workspace.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aicode.feature.workspace.data.local.entity.RemoteConnectionEntity
import com.aicode.feature.workspace.data.local.entity.RemoteMountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteConnectionDao {
    // Connection operations
    @Query("SELECT * FROM remote_connections")
    fun getAllConnections(): Flow<List<RemoteConnectionEntity>>

    @Query("SELECT * FROM remote_connections WHERE id = :id")
    suspend fun getConnectionById(id: String): RemoteConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: RemoteConnectionEntity)

    @Update
    suspend fun updateConnection(connection: RemoteConnectionEntity)

    @Delete
    suspend fun deleteConnection(connection: RemoteConnectionEntity)

    // Mount operations
    @Query("SELECT * FROM remote_mounts")
    fun getAllMounts(): Flow<List<RemoteMountEntity>>

    @Query("SELECT * FROM remote_mounts WHERE id = :id")
    suspend fun getMountById(id: String): RemoteMountEntity?

    @Query("SELECT * FROM remote_mounts WHERE connectionId = :connectionId")
    fun getMountsByConnectionId(connectionId: String): Flow<List<RemoteMountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMount(mount: RemoteMountEntity)

    @Update
    suspend fun updateMount(mount: RemoteMountEntity)

    @Delete
    suspend fun deleteMount(mount: RemoteMountEntity)
}
