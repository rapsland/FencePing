package de.fenceping.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1B4D3E),
                    onPrimary = Color.White,
                    background = Color(0xFFF4F1EA),
                    surface = Color.White,
                    onBackground = Color(0xFF1A1A1A),
                    onSurface = Color(0xFF1A1A1A),
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val trial = remember { TrialManager(this) }
                    if (trial.isExpired()) {
                        LockedScreen()
                    } else {
                        FenceScreen(trialDays = trial.remainingDays())
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("FencePing", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Die 10-tägige Testphase ist abgelaufen. Die App ist gesperrt.")
    }
}

@Composable
private fun FenceScreen(trialDays: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(App.PREFS, Context.MODE_PRIVATE) }
    val registrar = remember { FenceRegistrar(context) }

    var lat by remember { mutableStateOf(prefs.getString(App.KEY_LAT, App.DEFAULT_LAT.toString()) ?: "") }
    var lon by remember { mutableStateOf(prefs.getString(App.KEY_LON, App.DEFAULT_LON.toString()) ?: "") }
    var label by remember { mutableStateOf(prefs.getString(App.KEY_LABEL, "Lippstadt Testpunkt") ?: "") }
    var radius by remember { mutableFloatStateOf(prefs.getInt(App.KEY_RADIUS, App.DEFAULT_RADIUS).toFloat()) }
    var armed by remember { mutableStateOf(prefs.getBoolean(App.KEY_ARMED, false)) }
    var status by remember { mutableStateOf(prefs.getString(App.KEY_LAST_EVENT, "Noch kein Event") ?: "") }
    var triggerCount by remember { mutableIntStateOf(prefs.getInt(App.KEY_TRIGGER_COUNT, 0)) }
    var permFine by remember { mutableStateOf(hasFine(context)) }
    var permBg by remember { mutableStateOf(hasBackground(context)) }
    var permNotif by remember { mutableStateOf(hasNotif(context)) }
    var gpsMsg by remember { mutableStateOf("") }

    fun refreshPerms() {
        permFine = hasFine(context)
        permBg = hasBackground(context)
        permNotif = hasNotif(context)
        status = prefs.getString(App.KEY_LAST_EVENT, status) ?: status
        triggerCount = prefs.getInt(App.KEY_TRIGGER_COUNT, triggerCount)
        armed = prefs.getBoolean(App.KEY_ARMED, armed)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refreshPerms() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPerms()
    }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshPerms()
        if (granted && Build.VERSION.SDK_INT >= 33 && !hasNotif(context)) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPerms()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("FencePing", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B4D3E))
        Text("v${BuildConfig.VERSION_NAME} · Beta-Demo · noch $trialDays Tage", fontSize = 12.sp, color = Color.Gray)
        Text(
            "Ein GPS-Punkt. Play Services überwacht ihn — auch wenn die App geschlossen ist. Kein In-App-GPS-Polling.",
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Berechtigungen (alle drei Pflicht)", fontWeight = FontWeight.SemiBold)
                PermRow("Standort (genau)", permFine) {
                    fineLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                PermRow("Hintergrund-Standort (Immer erlauben)", permBg, enabled = permFine) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
                PermRow("Benachrichtigungen", permNotif) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (permFine && !permBg && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Text(
                        "Android verlangt: erst „Genau“, dann extra „Immer erlauben“. Ohne Immer feuert nichts bei geschlossener App.",
                        fontSize = 12.sp,
                        color = Color(0xFF8B1E1E),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(label, { label = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            lat, { lat = it },
            label = { Text("Latitude") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            lon, { lon = it },
            label = { Text("Longitude") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(8.dp))
        Text("Radius: ${radius.toInt()} m", fontWeight = FontWeight.Medium)
        Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..1000f, steps = 18)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100, 200, 300, 500).forEach { m ->
                FilterChip(selected = radius.toInt() == m, onClick = { radius = m.toFloat() }, label = { Text("${m}m") })
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (!hasFine(context)) {
                    gpsMsg = "Erst Standort erlauben"
                    return@OutlinedButton
                }
                val fused = LocationServices.getFusedLocationProviderClient(context)
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                    .addOnSuccessListener { loc: Location? ->
                        if (loc == null) {
                            gpsMsg = "Kein GPS-Fix"
                        } else {
                            lat = String.format(Locale.US, "%.6f", loc.latitude)
                            lon = String.format(Locale.US, "%.6f", loc.longitude)
                            gpsMsg = "Aktuelle Position übernommen"
                        }
                    }
                    .addOnFailureListener { gpsMsg = it.message ?: "GPS fehlgeschlagen" }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Aktuelle GPS-Position übernehmen") }
        if (gpsMsg.isNotBlank()) Text(gpsMsg, fontSize = 12.sp, color = Color.Gray)

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val la = lat.replace(',', '.').toDoubleOrNull()
                val lo = lon.replace(',', '.').toDoubleOrNull()
                if (la == null || lo == null) {
                    status = "Ungültige Koordinaten"
                    return@Button
                }
                prefs.edit()
                    .putString(App.KEY_LAT, la.toString())
                    .putString(App.KEY_LON, lo.toString())
                    .putInt(App.KEY_RADIUS, radius.toInt())
                    .putString(App.KEY_LABEL, label.ifBlank { "Punkt" })
                    .apply()
                registrar.register(la, lo, radius) { ok, msg ->
                    armed = ok
                    prefs.edit().putBoolean(App.KEY_ARMED, ok).apply()
                    status = msg
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = permFine && permBg
        ) { Text(if (armed) "Geofence neu setzen" else "Geofence scharf schalten") }

        OutlinedButton(
            onClick = {
                registrar.disarm { _, msg ->
                    armed = false
                    prefs.edit().putBoolean(App.KEY_ARMED, false).apply()
                    status = msg
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) { Text("Geofence abschalten") }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val n = NotificationCompat.Builder(context, App.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_map)
                    .setContentTitle("Test (ohne Geofence)")
                    .setContentText("Kanal + Permission OK. Das ist kein Play-Services-Event.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(9002, n)
                status = "Direkt-Notification gesendet (Pipeline-Check)"
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A52BE))
        ) { Text("🔔 Test-Notification (App offen)") }

        OutlinedButton(
            onClick = {
                val pm = context.getSystemService(PowerManager::class.java)
                if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    status = "Akku-Optimierung bereits ausgenommen"
                } else {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(i)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) { Text("Akku-Optimierung deaktivieren") }

        Spacer(Modifier.height(16.dp))
        val badge = if (armed) Color(0xFF1B4D3E) else Color(0xFF8B1E1E)
        Text(if (armed) "STATUS: SCHARF" else "STATUS: AUS", color = badge, fontWeight = FontWeight.Bold)
        Text("Trigger bisher: $triggerCount", fontSize = 13.sp)
        Text("Letztes Event:", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(status, fontSize = 13.sp, modifier = Modifier.background(Color(0xFFE8E4D9)).padding(8.dp).fillMaxWidth())

        Spacer(Modifier.height(12.dp))
        Text(
            "Test bei geschlossener App:\n" +
                "1. Rechte + scharf schalten.\n" +
                "2. App per Zurück/Recents schließen (nicht „Beenden erzwingen“ — das löscht Geofences).\n" +
                "3. Zum Punkt laufen oder Mock-Location setzen.\n" +
                "Play Services weckt den Receiver. Letztes Event steht hier nach erneutem Öffnen.",
            fontSize = 12.sp,
            color = Color(0xFF444444)
        )
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, enabled: Boolean = true, onAsk: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${if (ok) "✅" else "❌"} $label", modifier = Modifier.weight(1f), fontSize = 14.sp)
        if (!ok) {
            Button(onClick = onAsk, enabled = enabled) { Text("Erlauben") }
        }
    }
}

private fun hasFine(ctx: Context) =
    ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasBackground(ctx: Context) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    else true

private fun hasNotif(ctx: Context) =
    if (Build.VERSION.SDK_INT >= 33)
        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    else true
