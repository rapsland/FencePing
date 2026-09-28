package de.fenceping.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        TrialManager(this)
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_desc)
            enableVibration(true)
            setShowBadge(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "fenceping_alerts"
        const val PREFS = "fenceping"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_RADIUS = "radius"
        const val KEY_LABEL = "label"
        const val KEY_ARMED = "armed"
        const val KEY_LAST_EVENT = "last_event"
        const val KEY_TRIGGER_COUNT = "trigger_count"
        const val FENCE_ID = "manual_point"
        const val DEFAULT_LAT = 51.6736
        const val DEFAULT_LON = 8.3448
        const val DEFAULT_RADIUS = 300
    }
}
