# CoastHint

Android-App, die parallel zur Navigation in [OsmAnd](https://osmand.net) läuft und
rechtzeitig vor **Abbiegungen** und **sinkenden Tempolimits** einen Hinweis „Gas weg“ gibt –
damit man ausrollen kann statt zu bremsen (Sprit sparen, Bremsen schonen).

## Funktionsweise

- **Nächstes Manöver** über die OsmAnd-AIDL-API (`distanceTo`, `turnType`)
- **Kommende Tempolimits** aus OpenStreetMap (`maxspeed`, zunächst über die Overpass API)
- **Eigene Geschwindigkeit** per GPS (geglättet)
- Ausrollstrecke: `d_coast = (v1² − v2²) / (2 · a_coast) + v1 · t_react` –
  der Hinweis kommt, sobald die Distanz zum Ereignis in diesen Bereich fällt
- Ausgabe als kurzer Ton (duckt OsmAnd-Ansagen nur), optional Sprachansage oder Vibration

## Tech-Stack

Kotlin · Jetpack Compose · Coroutines/Flow · Foreground Service · DataStore · JUnit5

## Status

In Entwicklung. Details zu Architektur und Vorgehen: [CLAUDE.md](CLAUDE.md).

## Sicherheit

Die App gibt nur Hinweise und greift nie ins Fahrzeug ein. Während der Fahrt ist keine
Bedienung nötig; Einstellungen sind nur im Stand zugänglich.
