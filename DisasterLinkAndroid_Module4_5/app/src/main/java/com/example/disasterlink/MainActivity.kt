package com.example.disasterlink

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.activity.compose.setContent
import com.example.disasterlink.data.Repository
import com.example.disasterlink.ui.DisasterLinkApp

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repository.init(applicationContext)
        val request = PeriodicWorkRequestBuilder<com.example.disasterlink.data.SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("disasterlink-sync", androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
        setContent { DisasterLinkApp() }
    }
}
