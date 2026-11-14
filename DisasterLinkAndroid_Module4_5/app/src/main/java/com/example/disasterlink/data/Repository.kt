package com.example.disasterlink.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.Locale

object Repository {
    private lateinit var dao: AppDao
    private lateinit var prefs: SharedPreferences
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "disasterlink.db")
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
        dao = db.dao()
        prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        EvidenceStore.init(context)
    }

    private fun parseRole(value: String?): UserRole? = value?.let { str ->
        runCatching { UserRole.valueOf(str.uppercase(Locale.US)) }.getOrNull()
    }

    private fun parseAuthority(value: String?): AuthorityType? = value?.let { str ->
        runCatching { AuthorityType.valueOf(str.uppercase(Locale.US)) }.getOrNull()
    }

    private fun parseEmergencyStatus(value: String?): EmergencyStatus = value?.let { str ->
        runCatching { EmergencyStatus.valueOf(str.uppercase(Locale.US)) }.getOrNull()
    } ?: EmergencyStatus.REPORTED

    private fun parseTaskStatus(value: String?): TaskStatus = value?.let { str ->
        runCatching { TaskStatus.valueOf(str.uppercase(Locale.US)) }.getOrNull()
    } ?: TaskStatus.PENDING

    fun currentSession(): SessionUser? {
        val id = prefs.getString("id", null) ?: return null
        val name = prefs.getString("name", "User") ?: "User"
        val roleStr = prefs.getString("role", null) ?: return null
        val role = parseRole(roleStr) ?: return null
        val authority = parseAuthority(prefs.getString("authority", null))
        val district = prefs.getString("district", "") ?: ""
        val govId = prefs.getString("governmentId", "") ?: ""
        return SessionUser(id, name, role, authority, district, govId)
    }

    /** Explicit logout: revoke the server refresh session when online, then always clear local state. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val token = authToken()
        val refresh = prefs.getString("refreshToken", null)
        if (token != null && refresh != null) runCatching {
            ApiClient.authApi.logout("Bearer $token", RefreshRequest(refresh))
        }
        prefs.edit().clear().apply()
    }

    suspend fun register(
        name: String, phone: String, email: String, govId: String, address: String,
        pincode: String, password: String, role: UserRole,
        authorityType: AuthorityType? = null, district: String = "", inviteCode: String? = null
    ): Result<SessionUser> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.authApi.register(
                RegisterRequest(name, phone, email, govId, address, pincode, password, role.name, authorityType?.name, district, inviteCode)
            )
            if (!response.isSuccessful) return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            val body = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty response from server"))
            cacheUserLocally(body.user, password)
            val session = SessionUser(body.user.id, body.user.name, role, authorityType, body.user.district, body.user.governmentId)
            saveSession(session, body.token, body.refreshToken)
            Result.success(session)
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Can't reach the server. Check your connection and try again.", e))
        }
    }

    /** District Collector+ mints an invite code for onboarding a new authority account. */
    suspend fun mintInviteCode(authorityType: AuthorityType, district: String?): Result<String> = withContext(Dispatchers.IO) {
        val token = authToken() ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val response = ApiClient.authApi.mintInvite("Bearer $token", MintInviteRequest(authorityType.name, district))
            if (!response.isSuccessful) return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            Result.success(response.body()?.code ?: return@withContext Result.failure(IllegalStateException("Empty response from server")))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Can't reach the server. Check your connection and try again.", e))
        }
    }

    /** Requests a real email OTP. The backend never returns the OTP to the app. */
    suspend fun requestEmailOtp(email: String, role: UserRole): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.authApi.requestEmailOtp(EmailOtpRequestBody(email.trim().lowercase(), role.name))
            if (!response.isSuccessful) return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Can't reach the server. Check your connection and try again.", e))
        }
    }

    /** Verifies the one-time email OTP and persists the normal long-lived app session. */
    suspend fun verifyEmailOtp(email: String, otp: String, role: UserRole): Result<SessionUser> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.authApi.verifyEmailOtp(EmailOtpVerifyBody(email.trim().lowercase(), otp.trim(), role.name))
            if (!response.isSuccessful) return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            val body = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty response from server"))
            cacheUserLocally(body.user, "")
            val authorityType = parseAuthority(body.user.authorityType)
            val userRole = parseRole(body.user.role) ?: role
            val session = SessionUser(body.user.id, body.user.name, userRole, authorityType, body.user.district, body.user.governmentId)
            saveSession(session, body.token, body.refreshToken)
            Result.success(session)
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Can't reach the server. Check your connection and try again.", e))
        }
    }

    private suspend fun cacheUserLocally(user: UserDto, password: String) {
        val userRole = parseRole(user.role) ?: UserRole.CITIZEN
        val authorityType = parseAuthority(user.authorityType)
        val entity = UserEntity(
            user.id, user.name, user.phone, user.email, user.governmentId, user.address, user.pincode,
            sha256(password), userRole, authorityType, user.district
        )
        runCatching { dao.insertUser(entity) }
    }

    private fun appContext(): Context = context

    fun authToken(): String? = prefs.getString("token", null)

    private fun saveSession(session: SessionUser, token: String, refreshToken: String? = null) {
        val editor = prefs.edit()
            .putString("id", session.id).putString("name", session.name).putString("role", session.role.name)
            .putString("authority", session.authorityType?.name).putString("district", session.district)
            .putString("governmentId", session.governmentId).putString("token", token)
        if (refreshToken != null) editor.putString("refreshToken", refreshToken)
        editor.apply()
    }

    /**
     * Module 4 session refresh. It never clears the local session when the
     * device is offline. A refresh token is only exchanged when connectivity
     * is available; the citizen/rescue/authority can therefore keep using the
     * offline UI and BLE/LoRa path without being forced back to login.
     */
    suspend fun refreshSession(): Result<Unit> = withContext(Dispatchers.IO) {
        val session = currentSession() ?: return@withContext Result.failure(IllegalStateException("No local session"))
        val refresh = prefs.getString("refreshToken", null)
            ?: return@withContext Result.failure(IllegalStateException("No refresh session"))
        try {
            val response = ApiClient.authApi.refresh(RefreshRequest(refresh))
            if (!response.isSuccessful) return@withContext Result.failure(IllegalStateException(extractApiError(response.errorBody())))
            val body = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty refresh response"))
            val authority = parseAuthority(body.user.authorityType)
            val userRole = parseRole(body.user.role) ?: session.role
            val updated = SessionUser(body.user.id, body.user.name, userRole, authority, body.user.district, body.user.governmentId)
            cacheUserLocally(body.user, "")
            saveSession(updated, body.token)
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /** Module 5 one-tap operational sync. */
    suspend fun syncNow(): Result<Unit> = withContext(Dispatchers.IO) {
        refreshSession()
        val dbResult = runCatching {
            syncPendingEmergencies(dao)
            syncPendingTasks(dao)
            refreshOperationalData()
        }
        dbResult.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
    }

    /**
     * Pulls the authoritative operational state into Room. This is a LAN-first
     * operation: a local Wi-Fi/hotspot connection to the backend is enough;
     * internet access is not required. If the LAN is unavailable, existing
     * Room data remains available to the user.
     */
    suspend fun refreshOperationalData(): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authToken() ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val bearer = "Bearer $token"
            val emergencies = ApiClient.api.getEmergencies(bearer)
            val teams = ApiClient.api.getTeams(bearer)
            val tasks = ApiClient.api.getTasks(bearer)
            if (!emergencies.isSuccessful) return@withContext Result.failure(IllegalStateException(extractApiError(emergencies.errorBody())))
            if (!teams.isSuccessful) return@withContext Result.failure(IllegalStateException(extractApiError(teams.errorBody())))
            if (!tasks.isSuccessful) return@withContext Result.failure(IllegalStateException(extractApiError(tasks.errorBody())))

            emergencies.body().orEmpty().forEach { e ->
                val local = e.clientId?.let { dao.findEmergencyByClientId(it) }
                    ?: dao.findEmergency(e.id)
                val syncedEmergency = EmergencyEntity(
                    id = e.id,
                    citizenId = e.citizenId,
                    type = e.type,
                    message = e.message,
                    latitude = e.latitude,
                    longitude = e.longitude,
                    district = e.district,
                    reporterName = e.reporterName.orEmpty(),
                    reporterPhone = e.reporterPhone.orEmpty(),
                    reporterGovernmentId = e.reporterGovernmentId.orEmpty(),
                    status = runCatching { EmergencyStatus.valueOf(e.status) }.getOrDefault(EmergencyStatus.REPORTED),
                    createdAt = e.createdAt,
                    synced = true,
                    clientId = e.clientId
                )
                dao.insertEmergency(syncedEmergency)
                if (local != null && local.id != e.id) dao.deleteEmergency(local)
            }
            teams.body().orEmpty().forEach { t ->
                dao.insertTeam(RescueTeamEntity(t.id, t.name, t.memberIds.joinToString(","), t.phone, t.latitude, t.longitude, true, t.updatedAt))
            }
            tasks.body().orEmpty().forEach { t ->
                dao.insertTask(TaskEntity(
                    id = t.id, emergencyId = t.emergencyId, title = t.title, location = t.location,
                    assignedTeamId = t.assignedTeamId,
                    status = runCatching { TaskStatus.valueOf(t.status) }.getOrDefault(TaskStatus.PENDING),
                    startedAt = t.startedAt, completedAt = t.completedAt,
                    imageUris = t.imageUris.joinToString("|"), createdAt = t.createdAt, synced = true,
                    clientId = t.clientId
                ))
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    suspend fun createEmergency(citizenId: String, type: String, message: String, lat: Double?, lon: Double?, district: String): EmergencyEntity {
        val reporter = dao.findUser(citizenId)
        val localId = UUID.randomUUID().toString().replace("-", "")
        val item = EmergencyEntity(
            id = localId,
            citizenId = citizenId,
            type = type,
            message = message,
            latitude = lat,
            longitude = lon,
            district = district,
            reporterName = reporter?.name.orEmpty(),
            reporterPhone = reporter?.phone.orEmpty(),
            reporterGovernmentId = reporter?.governmentId.orEmpty(),
            clientId = localId
        )
        dao.insertEmergency(item)
        return item
    }

    suspend fun createAnonymousEmergency(type: String, message: String, lat: Double?, lon: Double?): EmergencyEntity =
        createEmergency("ANONYMOUS", type, message, lat, lon, "")

    fun citizenEmergencies(id: String): Flow<List<EmergencyEntity>> = dao.emergencies(id)
    fun allEmergencies(): Flow<List<EmergencyEntity>> = dao.allEmergencies()
    fun teams(): Flow<List<RescueTeamEntity>> = dao.teams()
    fun tasks(): Flow<List<TaskEntity>> = dao.tasks()
    fun tasksForTeam(id: String): Flow<List<TaskEntity>> = dao.tasksForTeam(id)
    fun feedbackForCitizen(id: String): Flow<List<FeedbackEntity>> = dao.feedbackForUser(id)
    fun allFeedback(): Flow<List<FeedbackEntity>> = dao.allFeedback()

    suspend fun findUser(id: String) = dao.findUser(id)
    suspend fun findEmergency(id: String) = dao.findEmergency(id)
    suspend fun findTask(id: String) = dao.findTask(id)
    suspend fun findTeam(id: String) = dao.findTeam(id)

    suspend fun taskEvents(id: String): Result<List<TaskEventDto>> = withContext(Dispatchers.IO) {
        val token = authToken() ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val response = ApiClient.api.getTaskEvents("Bearer $token", id)
            if (!response.isSuccessful) return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            Result.success(response.body().orEmpty())
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    suspend fun removeTeam(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authToken() ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val response = ApiClient.api.deleteTeam("Bearer $token", id)
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            }
            dao.findTeam(id)?.let { dao.deleteTeam(it) }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Can't reach the server. Team was not removed.", e))
        }
    }

    suspend fun addTeam(name: String, memberIds: List<String>, phone: String): Result<Unit> = withContext(Dispatchers.IO) {
        val normalized = memberIds.map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotBlank() }
            .distinct()

        if (name.trim().isEmpty()) return@withContext Result.failure(IllegalArgumentException("Team name is required"))
        if (name.trim().length > 100) return@withContext Result.failure(IllegalArgumentException("Team name is too long"))
        if (normalized.size !in 2..5) return@withContext Result.failure(IllegalArgumentException("A rescue team must have 2 to 5 members"))
        if (normalized.any { !it.matches(Regex("[A-Z]{3}\\d{4}[A-Z]{3}")) }) {
            return@withContext Result.failure(IllegalArgumentException("Member IDs must match ABC1234XYZ format"))
        }

        val token = authToken() ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            // The backend is authoritative for rescue-member registration.
            // Never reject a valid backend member merely because the authority
            // phone has not cached that rescue user's profile in Room.
            val response = ApiClient.api.createTeam(
                "Bearer $token",
                TeamCreateRequest(name.trim(), normalized, phone.trim())
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            }
            val team = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty response from server"))
            dao.insertTeam(RescueTeamEntity(
                team.id, team.name, team.memberIds.joinToString(","), team.phone,
                team.latitude, team.longitude, true, team.updatedAt
            ))
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Can't reach the server. Connect to the operational LAN before registering a rescue team.", e))
        }
    }

    suspend fun addTask(emergencyId: String, title: String, location: String, teamId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        val emergency = dao.findEmergency(emergencyId) ?: return@withContext Result.failure(IllegalArgumentException("Emergency not found"))
        val existingTask = dao.tasksNow().firstOrNull { it.emergencyId == emergencyId && it.status != TaskStatus.REJECTED && it.status != TaskStatus.COMPLETED }
        val token = authToken()

        // A task created while the authority phone was offline exists only in
        // Room. Do not call PATCH /reassign with its local ID; the backend has
        // never seen that task. Update the cached task and let the normal sync
        // worker create it with the latest selected team.
        if (existingTask != null && !existingTask.synced) {
            dao.updateTask(existingTask.copy(
                assignedTeamId = teamId,
                status = if (teamId == null) TaskStatus.PENDING else TaskStatus.ASSIGNED,
                synced = false
            ))
            dao.updateEmergency(emergency.copy(
                status = if (teamId == null) EmergencyStatus.ACKNOWLEDGED else EmergencyStatus.ASSIGNED,
                synced = false
            ))
            return@withContext Result.success(Unit)
        }

        if (token != null) {
            try {
                val response = if (existingTask != null && teamId != null) {
                    ApiClient.api.reassignTask("Bearer $token", existingTask.id, mapOf("assignedTeamId" to teamId))
                } else {
                    ApiClient.api.createTask("Bearer $token", TaskCreateRequest(emergencyId, title, location, teamId, null))
                }
                if (response.isSuccessful) {
                    val t = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty response from server"))
                    dao.updateEmergency(emergency.copy(status = if (teamId == null) EmergencyStatus.ACKNOWLEDGED else EmergencyStatus.ASSIGNED, synced = true))
                    dao.insertTask(TaskEntity(t.id, t.emergencyId, t.title, t.location, t.assignedTeamId,
                        parseTaskStatus(t.status), t.startedAt, t.completedAt, t.imageUris.joinToString("|"), t.createdAt, true, t.clientId))
                    return@withContext Result.success(Unit)
                }
                // A reachable server rejection is never treated as offline.
                return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            } catch (_: IOException) { /* network unavailable: use local operational cache */ }
        }
        dao.updateEmergency(emergency.copy(status = if (teamId == null) EmergencyStatus.ACKNOWLEDGED else EmergencyStatus.ASSIGNED, synced = false))
        if (existingTask != null && teamId != null) {
            dao.updateTask(existingTask.copy(assignedTeamId = teamId, status = TaskStatus.ASSIGNED, synced = false))
        } else {
            val localTaskId = UUID.randomUUID().toString().replace("-", "")
            dao.insertTask(TaskEntity(localTaskId, emergencyId, title, location, teamId,
                if (teamId == null) TaskStatus.PENDING else TaskStatus.ASSIGNED,
                clientId = localTaskId))
        }
        Result.success(Unit)
    }

    suspend fun acceptTask(task: TaskEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authToken()
        if (token != null) {
            try {
                val response = ApiClient.api.acceptTask("Bearer $token", task.id)
                if (response.isSuccessful) {
                    val t = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty response from server"))
                    dao.insertTask(TaskEntity(t.id, t.emergencyId, t.title, t.location, t.assignedTeamId, parseTaskStatus(t.status), t.startedAt, t.completedAt, t.imageUris.joinToString("|"), t.createdAt, true, t.clientId))
                    return@withContext Result.success(Unit)
                }
                return@withContext if (response.code() == 409) {
                    Result.failure(IllegalStateException(extractApiError(response.errorBody())))
                } else {
                    Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
                }
            } catch (_: IOException) {
                // Continue to local offline state.
            }
        }
        dao.updateTask(task.copy(status = TaskStatus.IN_PROGRESS, startedAt = System.currentTimeMillis(), synced = false))
        Result.success(Unit)
    }

    suspend fun rejectTask(task: TaskEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authToken()
        if (token != null) {
            try {
                val response = ApiClient.api.rejectTask("Bearer $token", task.id)
                if (response.isSuccessful) {
                    val t = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty response from server"))
                    dao.insertTask(TaskEntity(t.id, t.emergencyId, t.title, t.location, t.assignedTeamId, parseTaskStatus(t.status), t.startedAt, t.completedAt, t.imageUris.joinToString("|"), t.createdAt, true, t.clientId))
                    dao.findEmergency(task.emergencyId)?.let { dao.updateEmergency(it.copy(status = EmergencyStatus.REJECTED, synced = true)) }
                    return@withContext Result.success(Unit)
                }
                return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            } catch (_: IOException) {
                // Continue to local offline state.
            }
        }
        dao.updateTask(task.copy(status = TaskStatus.REJECTED, completedAt = System.currentTimeMillis(), synced = false))
        dao.findEmergency(task.emergencyId)?.let { dao.updateEmergency(it.copy(status = EmergencyStatus.REJECTED, synced = false)) }
        Result.success(Unit)
    }

    /** Resizes and compresses evidence images before Wi-Fi or BLE upload. */
    fun compressImage(context: Context, uri: Uri, label: String? = null): ByteArray {
        return ImageUtils.compressAndWatermark(context.contentResolver, uri, label)
    }

    suspend fun uploadEvidenceOverWifi(task: TaskEntity, imageUris: List<String>): Result<List<String>> = withContext(Dispatchers.IO) {
        val token = authToken() ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        if (imageUris.size < 3) return@withContext Result.failure(IllegalArgumentException("At least 3 evidence images are required"))
        try {
            val compressed = imageUris.take(10).map { uriString ->
                compressImage(appContext(), Uri.parse(uriString), "Task ID: ${task.id.take(8)}")
            }
            EvidenceWifiTransfer.uploadBytes(task.id, compressed, token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadEvidenceOverWifi(task: TaskEntity): Result<List<java.io.File>> = withContext(Dispatchers.IO) {
        val token = authToken() ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        val refs = task.images()
        EvidenceWifiTransfer.download(task.id, refs, token)
    }

    suspend fun submitTaskForVerification(task: TaskEntity, imageUris: List<String>, preUploadedRefs: List<String>? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (task.status != TaskStatus.IN_PROGRESS) {
            return@withContext Result.failure(IllegalStateException("Only an in-progress task can be submitted for verification"))
        }
        if (imageUris.size < 3) {
            return@withContext Result.failure(IllegalArgumentException("At least 3 images are required before submission"))
        }
        if (imageUris.any { it.isBlank() }) {
            return@withContext Result.failure(IllegalArgumentException("Evidence image references cannot be empty"))
        }

        val token = authToken()
        if (token != null) {
            try {
                val refs = if (preUploadedRefs != null && preUploadedRefs.size >= 3) {
                    preUploadedRefs
                } else {
                    val uploaded = EvidenceWifiTransfer.upload(appContext(), task.id, imageUris, token)
                    if (uploaded.isFailure) return@withContext Result.failure(uploaded.exceptionOrNull() ?: IllegalStateException("Evidence upload failed"))
                    uploaded.getOrThrow()
                }
                val response = ApiClient.api.submitVerification("Bearer $token", task.id, TaskVerificationRequest(refs))
                if (response.isSuccessful) {
                    val t = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty response from server"))
                    dao.insertTask(TaskEntity(
                        t.id, t.emergencyId, t.title, t.location, t.assignedTeamId,
                        parseTaskStatus(t.status), t.startedAt, t.completedAt,
                        t.imageUris.joinToString("|"), t.createdAt, true, t.clientId
                    ))
                    return@withContext Result.success(Unit)
                }
                return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            } catch (_: IOException) {
                // Fall through to the offline/ESP32 path.
            }
        }

        dao.updateTask(task.copy(
            status = TaskStatus.PENDING_VERIFICATION,
            completedAt = null,
            imageUris = (preUploadedRefs?.takeIf { it.size >= 3 } ?: imageUris).joinToString("|"),
            synced = false
        ))
        Result.success(Unit)
    }

    suspend fun verifyTask(task: TaskEntity, evidenceCount: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (currentSession()?.role != UserRole.AUTHORITY) {
            return@withContext Result.failure(IllegalAccessException("Only an authority can complete a task"))
        }
        if (task.status != TaskStatus.PENDING_VERIFICATION) {
            return@withContext Result.failure(IllegalStateException("Only a task pending verification can be completed"))
        }
        if (evidenceCount < 3) {
            return@withContext Result.failure(IllegalArgumentException("At least 3 evidence images must be reviewed"))
        }
        val token = authToken() ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val response = ApiClient.api.verifyTask("Bearer $token", task.id, TaskVerifyRequest(true, evidenceCount))
            if (!response.isSuccessful) return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            val t = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty response from server"))
            dao.insertTask(TaskEntity(
                t.id, t.emergencyId, t.title, t.location, t.assignedTeamId,
                parseTaskStatus(t.status), t.startedAt, t.completedAt,
                t.imageUris.joinToString("|"), t.createdAt, true, t.clientId
            ))
            dao.findEmergency(task.emergencyId)?.let { dao.updateEmergency(it.copy(status = EmergencyStatus.RESOLVED, synced = true)) }
            Result.success(Unit)
        } catch (e: IOException) {
            // Offline authority approval is stored locally and synchronized
            // through the same verification endpoint when the LAN returns.
            dao.updateTask(task.copy(status = TaskStatus.COMPLETED, completedAt = System.currentTimeMillis(), synced = false))
            dao.findEmergency(task.emergencyId)?.let {
                dao.updateEmergency(it.copy(status = EmergencyStatus.RESOLVED, synced = false))
            }
            Result.success(Unit)
        }
    }

    suspend fun rejectTaskVerification(task: TaskEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (currentSession()?.role != UserRole.AUTHORITY) {
            return@withContext Result.failure(IllegalAccessException("Only an authority can reject verification"))
        }
        if (task.status != TaskStatus.PENDING_VERIFICATION) {
            return@withContext Result.failure(IllegalStateException("Only a task pending verification can be rejected"))
        }
        val token = authToken() ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val response = ApiClient.api.rejectVerification("Bearer $token", task.id)
            if (!response.isSuccessful) return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            val t = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty response from server"))
            dao.insertTask(TaskEntity(
                t.id, t.emergencyId, t.title, t.location, t.assignedTeamId,
                parseTaskStatus(t.status), t.startedAt, t.completedAt,
                t.imageUris.joinToString("|"), t.createdAt, true, t.clientId
            ))
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Authority must be connected to reject task verification", e))
        }
    }

    suspend fun addFeedback(userId: String, taskId: String, rescueTeamId: String, rating: Int, comment: String) =
        dao.insertFeedback(FeedbackEntity(UUID.randomUUID().toString(), userId, taskId, rescueTeamId, rating, comment))

    suspend fun updateTeamLocation(teamId: String, lat: Double, lon: Double): Result<Unit> = withContext(Dispatchers.IO) {
        val local = dao.findTeam(teamId) ?: return@withContext Result.failure(IllegalArgumentException("Team not found"))
        val token = authToken()
        if (token != null) {
            try {
                val response = ApiClient.api.updateTeamLocation("Bearer $token", teamId, TeamLocationRequest(lat, lon))
                if (response.isSuccessful) {
                    val t = response.body()
                    if (t != null) dao.updateTeam(RescueTeamEntity(t.id, t.name, t.memberIds.joinToString(","), t.phone, t.latitude, t.longitude, true, t.updatedAt))
                    return@withContext Result.success(Unit)
                }
                if (response.code() in 400..499) return@withContext Result.failure(IllegalArgumentException(extractApiError(response.errorBody())))
            } catch (_: IOException) { }
        }
        // Keep the latest position locally. It will be visible to the local
        // rescue UI immediately and can be pushed again when LAN is restored.
        dao.updateTeam(local.copy(latitude = lat, longitude = lon, updatedAt = System.currentTimeMillis()))
        Result.success(Unit)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
