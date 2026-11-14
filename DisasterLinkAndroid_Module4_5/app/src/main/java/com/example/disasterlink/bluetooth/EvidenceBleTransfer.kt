package com.example.disasterlink.bluetooth

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.disasterlink.data.EvidenceStore
import com.example.disasterlink.data.ImageUtils
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Reliable evidence shuttle over the existing Nordic UART BLE link.
 * LoRa is intentionally not used for image bytes. The ESP32 stores evidence
 * in LittleFS and the authority phone later retrieves it over BLE.
 */
class EvidenceBleTransfer(
    private val context: Context,
    private val bluetooth: BluetoothManager
) {
    companion object {
        private const val MAX_IMAGE_BYTES = 180 * 1024
        private const val DATA_BYTES = 96
        private const val COMMAND_TIMEOUT_MS = 8000L
        private const val LIST_TIMEOUT_MS = 4000L
    }

    suspend fun upload(taskId: String, uris: List<String>): Result<List<File>> {
        if (!bluetooth.ready.value) return Result.failure(IllegalStateException("Connect to the field ESP32 first"))
        if (uris.size < 3) return Result.failure(IllegalArgumentException("At least 3 evidence images are required"))
        val files = mutableListOf<File>()
        return try {
            uris.take(10).forEachIndexed { index, raw ->
                val bytes = ImageUtils.compressAndWatermark(context.contentResolver, Uri.parse(raw), "Task: ${taskId.take(8)}")
                val begin = sendAwait("EVID|BEGIN|$taskId|$index|${bytes.size}", "EACK|BEGIN|$taskId|$index")
                if (!begin) throw IllegalStateException("ESP32 did not accept evidence image ${index + 1}")
                var offset = 0
                while (offset < bytes.size) {
                    val end = minOf(offset + DATA_BYTES, bytes.size)
                    val payload = Base64.encodeToString(bytes.copyOfRange(offset, end), Base64.NO_WRAP)
                    val ack = sendAwait("EVID|PUT|$taskId|$index|$offset|$payload", "EACK|PUT|$taskId|$index|$end")
                    if (!ack) throw IllegalStateException("Evidence transfer interrupted at image ${index + 1}, byte $offset")
                    offset = end
                }
                val finished = sendAwait("EVID|END|$taskId|$index|${bytes.size}", "EACK|END|$taskId|$index|OK")
                if (!finished) throw IllegalStateException("ESP32 did not finalize evidence image ${index + 1}")
                files += EvidenceStore.save(taskId, index, bytes)
            }
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun download(taskId: String): Result<List<File>> {
        if (!bluetooth.ready.value) return Result.failure(IllegalStateException("Connect to the field ESP32 first"))
        return try {
            val listWaiter = coroutineScope {
                val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                    val collected = mutableListOf<Pair<Int, Int>>()
                    val deadline = System.currentTimeMillis() + LIST_TIMEOUT_MS
                    while (System.currentTimeMillis() < deadline) {
                        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(100)
                        val msg = try { withTimeout(remaining) { bluetooth.messages.first { it.startsWith("EVID|META|$taskId|") || it == "EVID|LIST_END|$taskId" } } } catch (_: TimeoutCancellationException) { break }
                        if (msg == "EVID|LIST_END|$taskId") break
                        val p = msg.split('|')
                        if (p.size == 5) collected += (p[3].toIntOrNull() ?: -1) to (p[4].toIntOrNull() ?: 0)
                    }
                    collected
                }
                if (!bluetooth.send("EVID|LIST|$taskId")) {
                    waiter.cancel()
                    emptyList()
                } else waiter.await()
            }
            val metas = listWaiter
            if (metas.size < 3) throw IllegalStateException("ESP32 has fewer than 3 evidence images for this task")
            EvidenceStore.clear(taskId)
            val files = mutableListOf<File>()
            metas.sortedBy { it.first }.forEach { (index, total) ->
                val output = ByteArrayOutputStream(total.coerceAtMost(MAX_IMAGE_BYTES))
                var offset = 0
                while (offset < total) {
                    val waiter = coroutineScope {
                        val w = async(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                withTimeout(COMMAND_TIMEOUT_MS) {
                                    bluetooth.messages.first { it.startsWith("EDATA|$taskId|$index|$offset|") || it.startsWith("EERR|$taskId|") }
                                }
                            } catch (_: TimeoutCancellationException) { null }
                        }
                        if (!bluetooth.send("EVID|GET|$taskId|$index|$offset")) {
                            w.cancel()
                            null
                        } else w.await()
                    }
                    val msg = waiter ?: throw IllegalStateException("Timed out receiving evidence image ${index + 1}")
                    if (msg.startsWith("EERR|")) throw IllegalStateException(msg)
                    val p = msg.split('|', limit = 6)
                    if (p.size != 6) throw IllegalStateException("Malformed evidence packet")
                    val chunkOffset = p[3].toIntOrNull() ?: throw IllegalStateException("Invalid evidence offset")
                    val advertisedTotal = p[4].toIntOrNull() ?: total
                    if (chunkOffset != offset || advertisedTotal != total) throw IllegalStateException("Evidence chunk out of order")
                    val bytes = Base64.decode(p[5], Base64.NO_WRAP)
                    output.write(bytes)
                    offset += bytes.size
                    if (offset > total || offset > MAX_IMAGE_BYTES) throw IllegalStateException("Evidence image is too large")
                }
                files += EvidenceStore.save(taskId, index, output.toByteArray())
            }
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendAwait(command: String, prefix: String): Boolean = coroutineScope {
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            try {
                withTimeout(COMMAND_TIMEOUT_MS) {
                    bluetooth.messages.first { it == prefix || it.startsWith("EERR|") }
                }
            } catch (_: TimeoutCancellationException) {
                null
            }
        }
        if (!bluetooth.send(command)) {
            waiter.cancel()
            return@coroutineScope false
        }
        waiter.await() == prefix
    }
}
