package com.example

import android.app.Application
import com.example.core.AppNotificationManager
import com.example.worker.ChannelSyncScheduler

class TubePulseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppNotificationManager(this).ensureChannelsCreated()
        ChannelSyncScheduler.schedulePeriodicSync(this)
    }
}
