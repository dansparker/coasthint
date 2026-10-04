# CoastHint

Android-App, die parallel zur Navigation in [OsmAnd](https://osmand.net) läuft und
rechtzeitig vor **Abbiegungen** und **sinkenden Tempolimits** einen Hinweis „Gas weg“ gibt –
damit man ausrollen kann statt zu bremsen (Sprit sparen, Bremsen schonen).

## Funktionsweise

- **Nächstes Manöver** über die OsmAnd-AIDL-API (`distanceTo`, `turnType`)
- **Kommende Tempolimits** aus OpenStreetMap (`maxspeed`) über die Overpass API, mit
  Map-Matching auf die eigene Straße und Vorausschau entlang der Straße (1,5 km)
- **Eigene Geschwindigkeit** per GPS (geglättet)
- Ausrollstrecke: `d_coast = (v1² − v2²) / (2 · a_coast) + v1 · t_react` –
  der Hinweis kommt, sobald die Distanz zum Ereignis in diesen Bereich fällt.
  Optional mit kalibriertem Modell `a(v) = c0 + c2 · v²` (Roll- und Luftwiderstand)
- Ausgabe als kurzer Ton (duckt OsmAnd-Ansagen nur), optional Sprachansage und Vibration

## Bedienung

1. OsmAnd installieren und eine Route starten
2. In CoastHint **Starten** drücken (fragt Standort- und Benachrichtigungsrechte ab)
3. Optional unter **Ausrollen messen** die eigene Verzögerung kalibrieren
4. Fahrten werden als CSV aufgezeichnet und lassen sich unter **Fahrten-Logs** teilen

Einstellungen sind nur im Stand (unter 5 km/h) änderbar.

## Bauen

Voraussetzungen: JDK 17+, Android SDK (API 37).

```bash
./gradlew assembleDebug testDebugUnitTest
./gradlew installDebug
```

## Tech-Stack

Kotlin · Jetpack Compose · Coroutines/Flow · Foreground Service · DataStore · JUnit 5

## Status

Phase 1 (MVP) ist umgesetzt. Als Nächstes: Phase 2 mit Offline-Tempolimits
(vorverarbeiteter OSM-Extrakt als SQLite), damit die App ohne Mobilfunk funktioniert.
Details zu Architektur und Vorgehen: [CLAUDE.md](CLAUDE.md).

## Sicherheit

Die App gibt nur Hinweise und greift nie ins Fahrzeug ein. Während der Fahrt ist keine
Bedienung nötig; Einstellungen sind nur im Stand zugänglich.
