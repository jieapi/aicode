package com.aicode.feature.workspace.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "remote_mounts",
    foreignKeys = [
        ForeignKey(
            entity = RemoteConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["connectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("connectionId")]
)
data class RemoteMountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val connectionId: String,
    val remotePath: String,
    val localMountPath: String,
    val isActive: Boolean = false,
    val autoConnect: Boolean = true
)
