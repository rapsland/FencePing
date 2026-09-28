package de.fenceping.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        try {
            val prefs = context.getSharedPreferences(App.PREFS, Context.MODE_PRIVATE)
            val time = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())
            val event = GeofencingEvent.fromIntent(intent)
            if (event == null) {
                log(prefs, "$time Event=null (FLAG_MUTABLE prüfen)")
                notify(context, "FencePing", "Geofence-Intent ohne Event")
                return
            }
            if (event.hasError()) {
                log(prefs, "$time error=${event.errorCode}")
                notify(context, "FencePing Fehler", "Geofence error ${event.errorCode}")
                return
            }
            val kind = when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
                Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
                else -> "TRANS ${event.geofenceTransition}"
            }
            val loc = event.triggeringLocation
            val locTxt = if (loc != null) String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude) else "?"
            val ids = event.triggeringGeofences?.joinToString { it.requestId }.orEmpty()
            val label = prefs.getString(App.KEY_LABEL, "Punkt").orEmpty()
            val count = prefs.getInt(App.KEY_TRIGGER_COUNT, 0) + 1
            log(prefs, "$time $kind $label @$locTxt ids=$ids")
            prefs.edit().putInt(App.KEY_TRIGGER_COUNT, count).apply()
            notify(
                context,
                "$kind · $label",
                "App war geschlossen? Dann hat Play Services geweckt.\n$locTxt · #$count"
            )
        } catch (e: Exception) {
            Log.e("GeofenceReceiver", "onReceive", e)
        } finally {
            pending.finish()
        }
    }

    private fun log(prefs: android.content.SharedPreferences, msg: String) {
        Log.i("GeofenceReceiver", msg)
        prefs.edit().putString(App.KEY_LAST_EVENT, msg).apply()
    }

    private fun notify(context: Context, title: String, body: String) {
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, App.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(9001, n)
    }
}
