# CoastHint – Projektbeschreibung für Claude Code

Android-App, die parallel zur Navigation in **OsmAnd** läuft und rechtzeitig vor
**Abbiegungen** und **sinkenden Tempolimits** einen Hinweis „Gas weg“ gibt, damit man
ausrollen kann statt zu bremsen (Sprit sparen, Bremsen schonen).

Sprache im Code: Englisch. UI-Texte und Ansagen: Deutsch.

---

## Tech-Stack

- Kotlin, minSdk 26, targetSdk aktuell, Gradle Kotlin DSL
- Jetpack Compose für die UI (eine Seite für Status und Einstellungen)
- Coroutines + Flow, kein RxJava
- Foreground Service (`foregroundServiceType="location"`), damit die App auch bei
  Bildschirm aus und OsmAnd im Vordergrund läuft
- `FusedLocationProviderClient` für GPS (1 Hz, hohe Genauigkeit)
- DataStore für die Einstellungen
- Unit-Tests (JUnit5) für die gesamte Berechnungslogik

## Datenquellen

### 1. Nächstes Manöver – OsmAnd AIDL API

Referenz: https://github.com/osmandapp/osmand-api-demo (`OsmAnd-api-sample`, vor allem
`OsmAndAidlHelper.java`). **Vor der Implementierung dort die aktuellen Signaturen prüfen.**

- Abhängigkeit: `implementation("net.osmand:android-aidl-lib:master-snapshot@aar")`
- Repository (Ivy) in `settings.gradle.kts`:
  - url `https://builder.osmand.net`
  - artifact pattern `ivy/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]`
- Im Manifest `<queries>` für die Pakete `net.osmand` und `net.osmand.plus` eintragen
- Bind: `Intent("net.osmand.aidl.OsmandAidlServiceV2").setPackage(<installiertes OsmAnd-Paket>)`,
  ab API 34 zusätzlich `BIND_ALLOW_ACTIVITY_STARTS`
- `registerForNavigationUpdates(ANavigationUpdateParams(subscribeToUpdates=true, callbackId=0), callback)`
  → Callback `updateNavigationInfo(ADirectionInfo)` mit:
  - `distanceTo` (int, Meter bis zum nächsten Manöver)
  - `turnType` (int, siehe unten)
  - `isLeftSide`
- Optional zusätzlich `registerForVoiceRouterMessages` (für Debug/Logging)
- Beim Beenden sauber abmelden (`subscribeToUpdates=false` mit der callbackId)
- Wenn OsmAnd nicht installiert ist oder die Verbindung abbricht: Status in der UI
  anzeigen und automatisch neu verbinden (Backoff)

TurnType-Konstanten (aus `net.osmand.router.TurnType`):
`C=1, TL=2, TSLL=3, TSHL=4, TR=5, TSLR=6, TSHR=7, KL=8, KR=9, TU=10, TRU=11, OFFR=12, RNDB=13, RNLB=14`

Zielgeschwindigkeit je Manöver (konfigurierbar, Standardwerte):

| TurnType | v_Ziel |
|---|---|
| TL, TR | 25 km/h |
| TSHL, TSHR, TU, TRU | 15 km/h |
| TSLL, TSLR, KL, KR | kein Hinweis (oder 60 km/h, konfigurierbar) |
| RNDB, RNLB | 30 km/h |
| C, OFFR | kein Hinweis |

### 2. Tempolimits – OpenStreetMap `maxspeed`

Die OsmAnd-API liefert keine kommenden Tempolimits, deshalb kommen sie aus OSM.

**Phase 1 (MVP): Overpass API**
- Alle 500 m bzw. bei Kurswechsel > 30° die Straßen (`way[highway][maxspeed]`) in einem
  Korridor von ca. 1,5 km vor der aktuellen Position in Fahrtrichtung abfragen und cachen
  (Server konfigurierbar, Standard `https://overpass-api.de/api/interpreter`; Rate-Limits beachten)
- Einfaches Map-Matching: aktuelle Straße = nächster Way mit passendem Bearing
  (Abstand < 25 m, Winkelabweichung < 35°)
- Vorausschau: dem aktuellen Way in Fahrtrichtung folgen (bei Verzweigungen den
  geradlinigsten Nachfolger nehmen) und die erste Stelle finden, an der `maxspeed` kleiner
  als die aktuelle Geschwindigkeit wird. Ergebnis: Distanz + neues Limit
- `maxspeed`-Werte parsen: Zahlen, `"AT:urban"`=50, `"AT:rural"`=100, `"AT:motorway"`=130,
  `"DE:urban"`=50, `"DE:rural"`=100, `"walk"`, `"none"`, `mph`-Suffix
- Geschwindigkeitsabhängige Tags wie `maxspeed:conditional` vorerst ignorieren

**Phase 2: offline**
- Vorverarbeitete Daten (z. B. ein Österreich-Extrakt → SQLite mit R-Tree), damit die App
  ohne Mobilfunk funktioniert. Wird erst nach dem MVP umgesetzt, die Architektur soll
  dafür eine `SpeedLimitProvider`-Schnittstelle vorsehen.

### 3. Eigene Geschwindigkeit
- GPS-Speed (`Location.speed`), mit EMA geglättet (α ≈ 0,3)
- Später optional: OBD-II über ELM327 Bluetooth (Schnittstelle `SpeedSource` vorsehen)

## Kernlogik (`CoastAdvisor`, reine Kotlin-Klasse ohne Android-Abhängigkeiten, voll getestet)

Für jedes kommende Ereignis (Manöver oder Tempolimit) mit Distanz `d_event` und Zielgeschwindigkeit `v2`:

```
d_coast = (v1² − v2²) / (2 · a_coast) + v1 · t_react
Hinweis auslösen, wenn d_event <= d_coast + d_margin  UND  v1 > v2 + v_tol
```

- `a_coast` Standard 0,6 m/s² (Schubabschaltung mit eingelegtem Gang), einstellbar.
  Zusätzlich Modus „Segeln“ mit 0,3 m/s²
- `t_react` Standard 2 s, `d_margin` Standard 30 m, `v_tol` Standard 8 km/h
- Pro Ereignis höchstens **ein** Hinweis (Ereignis-ID: Manöver = Distanz-Trend + TurnType,
  Limit = Way-ID + Position). Eine neue Ankündigung erst, wenn `distanceTo` wieder springt
  (also ein neues Manöver ansteht)
- Kein Hinweis unter 30 km/h und nicht, wenn die Geschwindigkeit bereits sinkt
  (dv/dt < −0,4 m/s² → der Fahrer rollt schon aus)
- Wenn Manöver und Limit gleichzeitig zutreffen, gewinnt das Ereignis mit dem früheren Auslösepunkt

## Ausgabe

- Standard: kurzer, angenehmer Ton (zwei Töne absteigend) über `AudioManager` mit
  Audio Focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, damit OsmAnds Ansagen nicht
  abgewürgt werden
- Optional: TTS „Gas weg – Kurve in 400 Metern“ / „Gas weg – 70 in 350 Metern“
- Optional: Vibration
- Notification des Foreground Service zeigt: Verbindung OsmAnd ✓/✗, aktuelles Limit,
  nächstes Ereignis + Distanz

## Kalibrierung (Phase 1b)

Ein Modus „Ausrollen messen“: Der Fahrer drückt Start, geht vom Gas und die App loggt
v(t) per GPS, bis er Stopp drückt. Daraus wird per linearer Regression `a_coast` für den
Geschwindigkeitsbereich bestimmt und gespeichert. Mehrere Messungen werden gemittelt.
Optional ein quadratisches Modell a(v) = c0 + c2·v² (Rollwiderstand + Luftwiderstand).

## Logging / Debug

- CSV-Log pro Fahrt (Zeit, Lat/Lon, v, nächstes Ereignis, d_event, d_coast, Hinweis ja/nein)
  im app-eigenen Ordner, über die UI teilbar
- Debug-Screen mit Live-Werten

## Projektstruktur

```
app/src/main/java/.../coasthint/
  osmand/      OsmAndConnection (Bind, Reconnect, Flow<NavInfo>)
  speedlimit/  SpeedLimitProvider, OverpassSpeedLimitProvider, MaxspeedParser, MapMatcher
  location/    SpeedSource, GpsSpeedSource
  core/        CoastAdvisor, Event, Settings (reines Kotlin)
  output/      CueOutput (Ton/TTS/Vibration)
  service/     CoastHintService (Foreground)
  ui/          Compose-Screens
```

## Vorgehen

1. Projekt anlegen, Gradle + OsmAnd-AIDL-Abhängigkeit, Build muss grün sein
2. `core/` + Unit-Tests (Formel, Entprellung, Prioritäten, Maxspeed-Parser)
3. OsmAnd-Anbindung + Debug-Screen, der `distanceTo`/`turnType` live zeigt
4. Foreground Service + GPS + Ton-Ausgabe → erster testbarer Stand nur für Abbiegungen
5. Overpass-Tempolimits + Map-Matching
6. Kalibrierung, CSV-Log, Feinschliff

Nach jedem Schritt: Build + Tests laufen lassen und kurz zusammenfassen, was als Nächstes kommt.

## Sicherheit

Die App gibt nur Hinweise und greift nie ins Fahrzeug ein. Keine Interaktion während
der Fahrt nötig; Einstellungen sind nur im Stand zugänglich (v < 5 km/h).
