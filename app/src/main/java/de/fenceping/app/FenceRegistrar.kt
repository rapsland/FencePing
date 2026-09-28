package de.fenceping.app

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Registers a single circular geofence with Play Services.
 * The fence lives in GMS, not in this process — it fires after the app is killed.
 */
class FenceRegistrar(private val context: Context) {
    private val client = LocationServices.getGeofencingClient(context)

    fun canRegister(): Boolean {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bg = context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (!bg) return false
        }
        return true
    }

    fun register(lat: Double, lon: Double, radiusM: Float, onDone: (Boolean, String) -> Unit) {
        if (!canRegister()) {
            onDone(false, "Fine + Hintergrund-Standort fehlen")
            return
        }
        val fence = Geofence.Builder()
            .setRequestId(App.FENCE_ID)
            .setCircularRegion(lat, lon, radiusM.coerceIn(50f, 2000f))
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(0)
            .setLoiteringDelay(0)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(fence)
            .build()

        client.removeGeofences(listOf(App.FENCE_ID)).addOnCompleteListener {
            try {
                client.addGeofences(request, pendingIntent())
                    .addOnSuccessListener {
                        Log.i(TAG, "Geofence armed lat=$lat lon=$lon r=$radiusM")
                        onDone(true, "Geofence aktiv · Play Services überwacht den Punkt")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "addGeofences failed", e)
                        onDone(false, "Play Services: ${e.message}")
                    }
            } catch (se: SecurityException) {
                onDone(false, "SecurityException: ${se.message}")
            }
        }
    }

    fun disarm(onDone: (Boolean, String) -> Unit) {
        client.removeGeofences(listOf(App.FENCE_ID))
            .addOnSuccessListener { onDone(true, "Geofence entfernt") }
            .addOnFailureListener { e -> onDone(false, e.message ?: "remove failed") }
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, 42, intent, flags)
    }

    companion object {
        private const val TAG = "FenceRegistrar"
    }
}
