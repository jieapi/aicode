package com.aicodeeditor.feature.settings.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aicodeeditor.feature.settings.data.local.entity.AIProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIProviderDao {
    @Query("SELECT * FROM ai_providers")
    fun getAllProviders(): Flow<List<AIProviderEntity>>

    @Query("SELECT * FROM ai_providers WHERE isActive = 1 LIMIT 1")
    fun getActiveProvider(): Flow<AIProviderEntity?>

    @Query("SELECT * FROM ai_providers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProviderSync(): AIProviderEntity?

    @Query("SELECT * FROM ai_providers WHERE id = :id")
    suspend fun getProviderById(id: String): AIProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: AIProviderEntity)

    @Update
    suspend fun updateProvider(provider: AIProviderEntity)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun deleteProvider(id: String)

    @Query("UPDATE ai_providers SET isActive = 0")
    suspend fun deactivateAllProviders()

    @Query("UPDATE ai_providers SET isActive = 1 WHERE id = :id")
    suspend fun activateProvider(id: String)

    @Query("UPDATE ai_providers SET selectedModel = :model WHERE id = :id")
    suspend fun setSelectedModel(id: String, model: String)

    @Query("UPDATE ai_providers SET models = :models WHERE id = :id")
    suspend fun setModels(id: String, models: String)
}
