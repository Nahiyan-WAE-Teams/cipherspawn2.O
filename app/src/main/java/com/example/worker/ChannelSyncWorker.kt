package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.TubePulseRepository
import com.example.widget.TubePulseGlanceWidget
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit

class ChannelSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            TubePulseGlanceWidget.pushWidgetUpdate(applicationContext)
            return Result.success()
        }

        val repository = TubePulseRepository(applicationContext)

        val syncResult = repository.syncAllTrackedChannels(userId = uid, notificationsEnabled = true)
        TubePulseGlanceWidget.pushWidgetUpdate(applicationContext)

        return if (syncResult.isSuccess) Result.success() else Result.retry()
    }
}

object ChannelSyncScheduler {
    private const val PERIODIC_WORK_NAME = "tubepulse_periodic_channel_sync"

    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ChannelSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
