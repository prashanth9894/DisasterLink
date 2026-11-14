package com.example.disasterlink.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.Room

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "disasterlink.db")
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()
        return try {
            // Refresh the access token when possible; a network failure does
            // not clear the persisted local session or block offline use.
            Repository.refreshSession()
            syncPendingEmergencies(db.dao())
            syncPendingTasks(db.dao())
            Repository.refreshOperationalData()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
