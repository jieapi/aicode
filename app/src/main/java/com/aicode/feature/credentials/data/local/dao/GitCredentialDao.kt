package com.aicode.feature.credentials.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aicode.feature.credentials.data.local.entity.GitCredentialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GitCredentialDao {
    @Query("SELECT * FROM git_credentials ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<GitCredentialEntity>>

    @Query("SELECT * FROM git_credentials WHERE id = :id")
    suspend fun getById(id: String): GitCredentialEntity?

    /** 优先返回该 host 的默认凭据；无默认时回退取该 host 任意一条。 */
    @Query("SELECT * FROM git_credentials WHERE host = :host ORDER BY isDefault DESC LIMIT 1")
    suspend fun findByHost(host: String): GitCredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GitCredentialEntity)

    @Query("DELETE FROM git_credentials WHERE id = :id")
    suspend fun delete(id: String)

    /** 切换默认凭据前清掉该 host 其它条的 default 标记（host 内唯一）。 */
    @Query("UPDATE git_credentials SET isDefault = 0 WHERE host = :host")
    suspend fun clearDefaultForHost(host: String)

    @Query("UPDATE git_credentials SET isDefault = :isDefault WHERE id = :id")
    suspend fun setDefault(id: String, isDefault: Boolean)
}
