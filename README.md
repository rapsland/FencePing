# FencePing

Minimale Android-App zum Testen von **Play-Services-Geofences bei geschlossener App**.

Kein In-Process-GPS-Polling. Ein manueller GPS-Punkt (Lat/Lon + Radius) wird bei Google Play Services registriert. Play Services weckt `GeofenceReceiver`, auch wenn der Prozess tot ist.

## Warum SSL-Notifications im Closed-State oft ausbleiben

SharedShoppingList mischt zwei Pfade:

1. `GlobalLocationTracker` (`requestLocationUpdates` im App-Prozess) — **stirbt mit der App**.
2. `GeofenceReceiver` mit `android:exported="false"` — Play Services kann einen getöteten Prozess oft **nicht** wecken.

FencePing isoliert nur Pfad 2, korrekt verdrahtet:

- `PendingIntent.FLAG_MUTABLE` (Play Services hängt Event-Extras an)
- Receiver `exported="true"`
- Channel in `Application.onCreate`
- Runtime: Fine Location → Background Location („Immer erlauben“) → Notifications
- Boot-Re-Register

## Test

1. Standort genau + **Immer erlauben** + Notifications.
2. Optional Akku-Optimierung ausnehmen.
3. Lat/Lon (oder „Aktuelle GPS-Position“) + Radius, **scharf schalten**.
4. App schließen (Zurück/Recents). **Nicht** „Beenden erzwingen“ — das entfernt Geofences.
5. Zum Punkt laufen oder Mock-Location.
6. Notification muss kommen. Beim nächsten Öffnen steht das Event unter „Letztes Event“.

„🔔 Test-Notification“ prüft nur Kanal+Permission, nicht Play Services.
