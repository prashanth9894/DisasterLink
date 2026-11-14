package com.example.disasterlink.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ColumnInfo

enum class UserRole { CITIZEN, RESCUE, AUTHORITY }
enum class AuthorityType { VAO, TAHSILDAR, DISTRICT_COLLECTOR, STATE_DISASTER_MANAGEMENT_AUTHORITY, CM }
enum class EmergencyStatus { REPORTED, ACKNOWLEDGED, ASSIGNED, RESOLVED, REJECTED }
enum class TaskStatus { PENDING, ASSIGNED, IN_PROGRESS, PENDING_VERIFICATION, COMPLETED, REJECTED }

data class SessionUser(
    val id: String,
    val name: String,
    val role: UserRole,
    val authorityType: AuthorityType? = null,
    val district: String = "",
    val governmentId: String = ""
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val governmentId: String,
    val address: String,
    val pincode: String,
    val passwordHash: String,
    val role: UserRole,
    val authorityType: AuthorityType? = null,
    val district: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "emergencies",
    indices = [Index(value = ["clientId"], unique = true, name = "idx_emergencies_client_id")]
)
data class EmergencyEntity(
    @PrimaryKey val id: String,
    val citizenId: String,
    val type: String,
    val message: String,
    val latitude: Double?,
    val longitude: Double?,
    val district: String = "",
    @ColumnInfo(defaultValue = "") val reporterName: String = "",
    @ColumnInfo(defaultValue = "") val reporterPhone: String = "",
    @ColumnInfo(defaultValue = "") val reporterGovernmentId: String = "",
    val status: EmergencyStatus = EmergencyStatus.REPORTED,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
    val clientId: String? = null
)

@Entity(tableName = "rescue_teams")
data class RescueTeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val memberIds: String,
    val phone: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val available: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun members(): List<String> = memberIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val emergencyId: String,
    val title: String,
    val location: String,
    val assignedTeamId: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val imageUris: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
    val clientId: String? = null
) {
    fun images(): List<String> = imageUris.split("|").map { it.trim() }.filter { it.isNotBlank() }
}

@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val taskId: String,
    val rescueTeamId: String,
    val rating: Int,
    val comment: String,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
