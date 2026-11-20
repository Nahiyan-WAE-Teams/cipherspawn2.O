package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.example.core.AppNotificationManager
import com.example.ui.navigation.TubePulseApp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deepLinkChannelId = intent?.getStringExtra(AppNotificationManager.EXTRA_CHANNEL_ID)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            TubePulseApp(
                widthSizeClass = windowSizeClass.widthSizeClass,
                initialDeepLinkChannelId = deepLinkChannelId,
            )
        }
    }
}
