package com.example.disasterlink.data

import android.content.Context
import java.io.File

/** Local cross-device evidence cache. Files received over BLE live outside Room
 * so server refreshes can never overwrite their local paths. */
object EvidenceStore {
    private lateinit var root: File

    fun init(context: Context) {
        root = File(context.filesDir, "disasterlink_evidence").apply { mkdirs() }
    }

    fun taskDir(taskId: String): File = File(root, safe(taskId)).apply { mkdirs() }

    fun save(taskId: String, index: Int, bytes: ByteArray): File {
        val file = File(taskDir(taskId), "evidence_${index + 1}.jpg")
        file.writeBytes(bytes)
        return file
    }

    fun files(taskId: String): List<File> = taskDir(taskId).listFiles()
        ?.filter { it.isFile && it.extension.equals("jpg", true) }
        ?.sortedBy { it.name }
        .orEmpty()

    fun clear(taskId: String) {
        taskDir(taskId).deleteRecursively()
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
}
