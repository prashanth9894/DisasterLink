package com.example.disasterlink.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Evidence transfer over the operational Wi-Fi/LAN path.
 *
 * Images are compressed to the same safe size used by the BLE evidence path,
 * uploaded one-at-a-time to the authenticated backend, and stored on the
 * server. Authorities can then retrieve the real image bytes over Wi-Fi.
 */
object EvidenceWifiTransfer {
    private const val MAX_IMAGE_BYTES = 180 * 1024
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    suspend fun upload(context: Context, taskId: String, uris: List<String>, token: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (uris.size < 3) return@withContext Result.failure(IllegalArgumentException("At least 3 evidence images are required"))
        try {
            val files = uris.take(10).map {
                ImageUtils.compressAndWatermark(context.contentResolver, Uri.parse(it), "Task ID: ${taskId.take(8)}")
            }
            uploadBytes(taskId, files, token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Uploads evidence already cached locally by the BLE/offline path. */
    suspend fun uploadFiles(taskId: String, files: List<File>, token: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (files.size < 3) return@withContext Result.failure(IllegalArgumentException("At least 3 cached evidence images are required"))
        try {
            val bytes = files.sortedBy { it.name }.take(10).map { file ->
                if (!file.exists() || file.length() <= 0L || file.length() > MAX_IMAGE_BYTES) {
                    throw IllegalArgumentException("Invalid cached evidence file: ${file.name}")
                }
                file.readBytes()
            }
            uploadBytes(taskId, bytes, token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadBytes(taskId: String, images: List<ByteArray>, token: String): Result<List<String>> {
        val refs = mutableListOf<String>()
        images.forEachIndexed { index, bytes ->
            val response = ApiClient.api.uploadEvidence(
                "Bearer $token",
                taskId,
                EvidenceUploadRequest(index, "image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
            )
            if (!response.isSuccessful) {
                return Result.failure(IllegalStateException("Evidence ${index + 1} upload failed (${response.code()})"))
            }
            refs += response.body()?.uri ?: return Result.failure(IllegalStateException("Evidence ${index + 1} returned no server reference"))
        }
        return Result.success(refs)
    }

    suspend fun download(taskId: String, refs: List<String>, token: String): Result<List<File>> = withContext(Dispatchers.IO) {
        if (refs.size < 3) return@withContext Result.failure(IllegalArgumentException("The task does not contain at least 3 server evidence references"))
        try {
            EvidenceStore.clear(taskId)
            val files = mutableListOf<File>()
            refs.take(10).forEachIndexed { index, ref ->
                val bytes = downloadOne(ref, token)
                if (bytes.size < 100 || bytes.size > MAX_IMAGE_BYTES) throw IllegalStateException("Evidence ${index + 1} has an invalid size")
                if (!((bytes[0].toInt() and 0xff) == 0xff && (bytes[1].toInt() and 0xff) == 0xd8 && (bytes[2].toInt() and 0xff) == 0xff)) {
                    throw IllegalStateException("Evidence ${index + 1} is not a valid JPEG")
                }
                files += EvidenceStore.save(taskId, index, bytes)
            }
            if (files.size < 3) throw IllegalStateException("Fewer than 3 evidence images were downloaded")
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadOne(ref: String, token: String): ByteArray {
        val cleanRef = ref.trim()
        require(cleanRef.startsWith("/api/tasks/")) { "Unsupported evidence reference" }
        val base = AppConfig.API_BASE_URL.trimEnd('/')
        val url = URL(if (cleanRef.startsWith("http://") || cleanRef.startsWith("https://")) cleanRef else base + cleanRef)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "image/jpeg")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Evidence download failed (${connection.responseCode})")
            return connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) throw IllegalStateException("Evidence image is too large")
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
