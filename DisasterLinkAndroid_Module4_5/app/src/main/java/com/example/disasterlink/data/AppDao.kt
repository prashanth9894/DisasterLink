package com.example.disasterlink.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // The backend now owns uniqueness validation (governmentId/phone/email).
    // This just caches the server's response locally so other screens can
    // keep reading user profiles via Room while offline.
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertUser(user: UserEntity)
    @Query("SELECT * FROM users WHERE governmentId = :id LIMIT 1") suspend fun findUserByGovId(id: String): UserEntity?
    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1") suspend fun findUserByPhone(phone: String): UserEntity?
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1") suspend fun findUserByEmail(email: String): UserEntity?
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1") suspend fun findUser(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEmergency(emergency: EmergencyEntity)
    @Update suspend fun updateEmergency(emergency: EmergencyEntity)
    @Query("SELECT * FROM emergencies WHERE citizenId = :citizenId ORDER BY createdAt DESC") fun emergencies(citizenId: String): Flow<List<EmergencyEntity>>
    @Query("SELECT * FROM emergencies ORDER BY createdAt DESC") fun allEmergencies(): Flow<List<EmergencyEntity>>
    @Query("SELECT * FROM emergencies WHERE id = :id LIMIT 1") suspend fun findEmergency(id: String): EmergencyEntity?
    @Query("SELECT * FROM emergencies WHERE clientId = :clientId LIMIT 1") suspend fun findEmergencyByClientId(clientId: String): EmergencyEntity?
    @Delete suspend fun deleteEmergency(emergency: EmergencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTeam(team: RescueTeamEntity)
    @Update suspend fun updateTeam(team: RescueTeamEntity)
    @Delete suspend fun deleteTeam(team: RescueTeamEntity)
    @Query("SELECT * FROM rescue_teams ORDER BY name") fun teams(): Flow<List<RescueTeamEntity>>
    @Query("SELECT * FROM rescue_teams WHERE id = :id LIMIT 1") suspend fun findTeam(id: String): RescueTeamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTask(task: TaskEntity)
    @Update suspend fun updateTask(task: TaskEntity)
    @Delete suspend fun deleteTask(task: TaskEntity)
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC") fun tasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks") suspend fun tasksNow(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE assignedTeamId = :teamId ORDER BY createdAt DESC") fun tasksForTeam(teamId: String): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1") suspend fun findTask(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFeedback(feedback: FeedbackEntity)
    @Query("SELECT * FROM feedback WHERE userId = :userId ORDER BY createdAt DESC") fun feedbackForUser(userId: String): Flow<List<FeedbackEntity>>
    @Query("SELECT * FROM feedback ORDER BY createdAt DESC") fun allFeedback(): Flow<List<FeedbackEntity>>
    @Query("SELECT * FROM feedback WHERE taskId = :taskId LIMIT 1") suspend fun findFeedbackForTask(taskId: String): FeedbackEntity?

    @Query("SELECT * FROM emergencies WHERE synced = 0") suspend fun unsyncedEmergencies(): List<EmergencyEntity>
    @Query("UPDATE emergencies SET synced = 1 WHERE id = :id") suspend fun markEmergencySynced(id: String)
    @Query("SELECT * FROM tasks WHERE synced = 0") suspend fun unsyncedTasks(): List<TaskEntity>
    @Query("UPDATE tasks SET synced = 1 WHERE id = :id") suspend fun markTaskSynced(id: String)
    @Query("SELECT * FROM feedback WHERE synced = 0") suspend fun unsyncedFeedback(): List<FeedbackEntity>
}
