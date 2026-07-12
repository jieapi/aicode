package com.aicode.feature.workspace.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aicode.feature.workspace.domain.model.RemoteProtocol
import java.util.UUID

@Entity(tableName = "remote_connections")
data class RemoteConnectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: RemoteProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String = "password", // 'password' or 'key'
    val authData: String, // password or private key path
    val passphrase: String? = null
)
