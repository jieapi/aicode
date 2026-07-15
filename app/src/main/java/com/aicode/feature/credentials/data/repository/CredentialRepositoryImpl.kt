package com.aicode.feature.credentials.data.repository

import com.aicode.core.util.FileLogger
import com.aicode.feature.credentials.data.local.dao.GitCredentialDao
import com.aicode.feature.credentials.data.local.entity.GitCredentialEntity
import com.aicode.feature.credentials.domain.model.GitCredential
import com.aicode.feature.credentials.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialRepositoryImpl @Inject constructor(
    private val dao: GitCredentialDao
) : CredentialRepository {

    private companion object {
        const val TAG = "CredentialRepo"
    }

    override fun getAll(): Flow<List<GitCredential>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findForHost(host: String): GitCredential? {
        val normalized = host.trim().lowercase()
        if (normalized.isEmpty()) return null
        return dao.findByHost(normalized)?.toDomain()
    }

    override suspend fun save(credential: GitCredential) {
        FileLogger.i(TAG, "保存凭据 id=${credential.id} host=${credential.host} user=${credential.username} default=${credential.isDefault}")
        val now = System.currentTimeMillis()
        val entity = credential.copy(
            host = credential.host.trim().lowercase(),
            createdAt = if (credential.createdAt == 0L) now else credential.createdAt,
            updatedAt = now
        ).toEntity()
        // 设为默认需先清同 host 其它条默认，保证 host 内唯一。
        if (entity.isDefault) dao.clearDefaultForHost(entity.host)
        dao.upsert(entity)
    }

    override suspend fun delete(id: String) {
        FileLogger.i(TAG, "删除凭据 id=$id")
        dao.delete(id)
    }

    override suspend fun setDefault(id: String, isDefault: Boolean) {
        FileLogger.i(TAG, "切换默认凭据 id=$id default=$isDefault")
        val entity = dao.getById(id) ?: return
        if (isDefault) dao.clearDefaultForHost(entity.host)
        dao.setDefault(id, isDefault)
    }

    private fun GitCredentialEntity.toDomain(): GitCredential = GitCredential(
        id = id,
        host = host,
        username = username,
        token = token,
        label = label,
        isDefault = isDefault,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun GitCredential.toEntity(): GitCredentialEntity = GitCredentialEntity(
        id = id,
        host = host,
        username = username,
        token = token,
        label = label,
        isDefault = isDefault,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
