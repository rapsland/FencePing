package de.fenceping.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return
        val pending = goAsync()
        val prefs = context.getSharedPreferences(App.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(App.KEY_ARMED, false)) {
            pending.finish()
            return
        }
        val lat = prefs.getString(App.KEY_LAT, null)?.toDoubleOrNull()
        val lon = prefs.getString(App.KEY_LON, null)?.toDoubleOrNull()
        if (lat == null || lon == null) {
            pending.finish()
            return
        }
        val radius = prefs.getInt(App.KEY_RADIUS, App.DEFAULT_RADIUS).toFloat()
        FenceRegistrar(context).register(lat, lon, radius) { ok, msg ->
            prefs.edit().putString(App.KEY_LAST_EVENT, "BOOT re-register ok=$ok $msg").apply()
            pending.finish()
        }
    }
}
