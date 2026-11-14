package com.example.disasterlink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.util.concurrent.TimeUnit

// Module 3 operational sync works over the local LAN as well as the internet.
// If the phone, backend PC and gateway share a local Wi-Fi/hotspot, these calls
// do not require internet access.
data class EmergencySyncRequest(
    val clientId: String, val type: String, val message: String,
    val latitude: Double?, val longitude: Double?, val district: String?,
    val citizenId: String? = null, val reporterName: String? = null,
    val reporterPhone: String? = null, val reporterGovernmentId: String? = null
)
data class EmergencySyncResponse(val id: String, val clientId: String?, val status: String)

data class EmergencyDto(
    val id: String, val clientId: String?, val citizenId: String,
    val reporterName: String? = "", val reporterPhone: String? = "", val reporterGovernmentId: String? = "",
    val type: String, val message: String, val latitude: Double?, val longitude: Double?,
    val district: String, val status: String, val createdAt: Long
)

data class TeamDto(
    val id: String, val name: String, val memberIds: List<String>, val phone: String,
    val latitude: Double?, val longitude: Double?, val updatedAt: Long
)

data class TeamCreateRequest(
    val name: String, val memberIds: List<String>, val phone: String
)

data class TaskDto(
    val id: String, val clientId: String? = null, val emergencyId: String, val title: String, val location: String,
    val assignedTeamId: String?, val status: String, val createdAt: Long,
    val startedAt: Long?, val completedAt: Long?, val imageUris: List<String> = emptyList()
)

data class TaskCreateRequest(
    val emergencyId: String, val title: String, val location: String, val assignedTeamId: String?,
    val clientId: String? = null
)

data class EvidenceUploadRequest(val index: Int, val mimeType: String, val dataBase64: String)
data class EvidenceUploadResponse(val index: Int, val uri: String, val mimeType: String, val size: Int)

data class TaskVerificationRequest(val imageUris: List<String>)
data class TaskVerifyRequest(val evidenceReviewed: Boolean, val evidenceCount: Int)
data class TeamLocationRequest(val latitude: Double, val longitude: Double)
data class TaskEventDto(val id: String, val taskId: String, val action: String, val actorId: String, val actorRole: String, val details: String, val createdAt: Long)

interface DisasterApi {
    @GET("api/emergencies")
    suspend fun getEmergencies(@Header("Authorization") bearer: String): Response<List<EmergencyDto>>

    @GET("api/teams")
    suspend fun getTeams(@Header("Authorization") bearer: String): Response<List<TeamDto>>

    @POST("api/teams")
    suspend fun createTeam(@Header("Authorization") bearer: String, @Body body: TeamCreateRequest): Response<TeamDto>

    @DELETE("api/teams/{id}")
    suspend fun deleteTeam(@Header("Authorization") bearer: String, @Path("id") id: String): Response<Unit>

    @GET("api/tasks")
    suspend fun getTasks(@Header("Authorization") bearer: String): Response<List<TaskDto>>

    @POST("api/emergencies")
    suspend fun uploadEmergency(@Header("Authorization") bearer: String?, @Body body: EmergencySyncRequest): Response<EmergencySyncResponse>

    @POST("api/tasks")
    suspend fun createTask(@Header("Authorization") bearer: String, @Body body: TaskCreateRequest): Response<TaskDto>

    @PATCH("api/teams/{id}/location")
    suspend fun updateTeamLocation(@Header("Authorization") bearer: String, @Path("id") id: String, @Body body: TeamLocationRequest): Response<TeamDto>

    @PATCH("api/tasks/{id}/reassign")
    suspend fun reassignTask(@Header("Authorization") bearer: String, @Path("id") id: String, @Body body: Map<String, String>): Response<TaskDto>

    @PATCH("api/tasks/{id}/accept")
    suspend fun acceptTask(@Header("Authorization") bearer: String, @Path("id") id: String): Response<TaskDto>

    @PATCH("api/tasks/{id}/reject")
    suspend fun rejectTask(@Header("Authorization") bearer: String, @Path("id") id: String): Response<TaskDto>

    @GET("api/tasks/{id}/events")
    suspend fun getTaskEvents(@Header("Authorization") bearer: String, @Path("id") id: String): Response<List<TaskEventDto>>

    @POST("api/tasks/{id}/evidence")
    suspend fun uploadEvidence(@Header("Authorization") bearer: String, @Path("id") id: String, @Body body: EvidenceUploadRequest): Response<EvidenceUploadResponse>

    @PATCH("api/tasks/{id}/submit-verification")
    suspend fun submitVerification(@Header("Authorization") bearer: String, @Path("id") id: String, @Body body: TaskVerificationRequest): Response<TaskDto>

    @PATCH("api/tasks/{id}/verify")
    suspend fun verifyTask(@Header("Authorization") bearer: String, @Path("id") id: String, @Body body: TaskVerifyRequest): Response<TaskDto>

    @PATCH("api/tasks/{id}/reject-verification")
    suspend fun rejectVerification(@Header("Authorization") bearer: String, @Path("id") id: String): Response<TaskDto>
}

object ApiClient {
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val api: DisasterApi by lazy { retrofit.create(DisasterApi::class.java) }
    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
}

suspend fun syncPendingEmergencies(dao: AppDao) = withContext(Dispatchers.IO) {
    dao.unsyncedEmergencies().forEach { e ->
        try {
            val bearer = Repository.authToken()?.let { "Bearer $it" }
            val response = ApiClient.api.uploadEmergency(
                bearer,
                EmergencySyncRequest(e.clientId ?: e.id, e.type, e.message, e.latitude, e.longitude, e.district,
                    e.citizenId, e.reporterName, e.reporterPhone, e.reporterGovernmentId)
            )
            if (response.isSuccessful) {
                val synced = response.body()
                if (synced != null) {
                    val replacement = e.copy(
                        id = synced.id,
                        clientId = synced.clientId ?: (e.clientId ?: e.id),
                        status = runCatching { EmergencyStatus.valueOf(synced.status) }.getOrDefault(e.status),
                        synced = true
                    )
                    dao.insertEmergency(replacement)
                    if (replacement.id != e.id) dao.deleteEmergency(e)
                } else {
                    dao.markEmergencySynced(e.id)
                }
            }
        } catch (_: IOException) {
            // Keep the emergency queued for the next sync attempt.
        }
    }
}


/** Module 5 operational synchronization: push local work first, then pull the
 * authoritative emergency/team/task state. It works against the configured
 * LAN address, so internet access is not inherently required. */
suspend fun syncPendingTasks(dao: AppDao) = withContext(Dispatchers.IO) {
    val token = Repository.authToken() ?: return@withContext
    val bearer = "Bearer $token"
    dao.unsyncedTasks().forEach { local ->
        try {
            // Existing server tasks must be updated by ID; only locally-created
            // tasks should go through POST /api/tasks. This is especially
            // important for offline PENDING_VERIFICATION transitions.
            val existingServer = runCatching { ApiClient.api.getTasks(bearer) }
                .getOrNull()?.takeIf { it.isSuccessful }?.body().orEmpty()
                .firstOrNull { it.id == local.id || (local.clientId != null && it.clientId == local.clientId) }

            val serverTask = if (existingServer != null) {
                existingServer
            } else {
                val clientId = local.clientId ?: local.id
                val response = ApiClient.api.createTask(
                    bearer,
                    TaskCreateRequest(local.emergencyId, local.title, local.location, local.assignedTeamId, clientId)
                )
                if (!response.isSuccessful) return@forEach
                response.body() ?: return@forEach
            }

            var finalTask = dtoToTask(serverTask)
            when (local.status) {
                TaskStatus.IN_PROGRESS -> {
                    val r = ApiClient.api.acceptTask(bearer, serverTask.id)
                    if (!r.isSuccessful && r.code() != 409) return@forEach
                    r.body()?.let { finalTask = dtoToTask(it) }
                }
                TaskStatus.REJECTED -> {
                    val r = ApiClient.api.rejectTask(bearer, serverTask.id)
                    if (!r.isSuccessful && r.code() != 409) return@forEach
                    r.body()?.let { finalTask = dtoToTask(it) }
                }
                TaskStatus.ASSIGNED -> {
                    if (local.assignedTeamId != null) {
                        val r = ApiClient.api.reassignTask(bearer, serverTask.id, mapOf("assignedTeamId" to local.assignedTeamId))
                        if (!r.isSuccessful && r.code() != 409) return@forEach
                        r.body()?.let { finalTask = dtoToTask(it) }
                    }
                }
                TaskStatus.PENDING_VERIFICATION -> {
                    var refs = local.images()
                    if (refs.size < 3) return@forEach
                    // Offline rescue submission stores the real JPEGs in EvidenceStore
                    // and may have only content:// references in Room. When LAN returns,
                    // upload those cached files before changing the server task state.
                    if (refs.none { it.startsWith("/api/tasks/") }) {
                        val cached = EvidenceStore.files(local.id)
                        val uploaded = EvidenceWifiTransfer.uploadFiles(local.id, cached, token)
                        if (uploaded.isFailure) return@forEach
                        refs = uploaded.getOrThrow()
                    }
                    val r = ApiClient.api.submitVerification(bearer, serverTask.id, TaskVerificationRequest(refs))
                    if (!r.isSuccessful && r.code() != 409) return@forEach
                    r.body()?.let { finalTask = dtoToTask(it) }
                }
                TaskStatus.COMPLETED -> {
                    var refs = local.images()
                    if (refs.size < 3) return@forEach
                    if (refs.none { it.startsWith("/api/tasks/") }) {
                        val cached = EvidenceStore.files(local.id)
                        val uploaded = EvidenceWifiTransfer.uploadFiles(local.id, cached, token)
                        if (uploaded.isFailure) return@forEach
                        refs = uploaded.getOrThrow()
                        // If the server is still IN_PROGRESS, put it into verification
                        // first; the next call below will then complete it atomically.
                        val serverState = ApiClient.api.getTasks(bearer).takeIf { it.isSuccessful }?.body().orEmpty()
                            .firstOrNull { it.id == serverTask.id }
                        if (serverState?.status == TaskStatus.IN_PROGRESS.name) {
                            val submitted = ApiClient.api.submitVerification(bearer, serverTask.id, TaskVerificationRequest(refs))
                            if (!submitted.isSuccessful && submitted.code() != 409) return@forEach
                            submitted.body()?.let { finalTask = dtoToTask(it) }
                        }
                    }
                    val r = ApiClient.api.verifyTask(bearer, serverTask.id, TaskVerifyRequest(true, refs.size))
                    if (!r.isSuccessful && r.code() != 409) return@forEach
                    r.body()?.let { finalTask = dtoToTask(it) }
                }
                else -> Unit
            }

            val syncedTask = finalTask.copy(synced = true, clientId = serverTask.clientId ?: local.clientId)
            dao.insertTask(syncedTask)
            if (syncedTask.id != local.id) dao.deleteTask(local)
        } catch (_: IOException) {
            // Keep the local state for the next retry.
        }
    }
}

private fun dtoToTask(t: TaskDto) = TaskEntity(
    id = t.id, emergencyId = t.emergencyId, title = t.title, location = t.location,
    assignedTeamId = t.assignedTeamId,
    status = runCatching { TaskStatus.valueOf(t.status) }.getOrDefault(TaskStatus.PENDING),
    startedAt = t.startedAt, completedAt = t.completedAt,
    imageUris = t.imageUris.joinToString("|"), createdAt = t.createdAt,
    synced = true, clientId = t.clientId
)
