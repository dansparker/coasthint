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

## Offline-Tempolimits

Ohne Mobilfunk kommen die Tempolimits aus SQLite-Dateien (mit R-Tree-Index), eine pro Land
oder Region, die am PC aus OpenStreetMap-Extrakten erzeugt werden.

**Einmalig: Konverter bauen** (braucht Java 17+ im `PATH`)

```bash
./gradlew :tools:roaddb-builder:installDist
```

Das fertige Programm liegt dann in `tools/roaddb-builder/build/install/roaddb-builder/`
(Startskript unter `bin/`); der Ordner kann an einen beliebigen Ort kopiert werden.

**Daten erzeugen**

1. Extrakte in einen Ordner laden, z. B. `D:\OSM\`, von
   [Geofabrik](https://download.geofabrik.de/europe.html): `austria-latest.osm.pbf`,
   `germany-latest.osm.pbf`, …
2. Konverter mit dem Ordner starten:
   ```bash
   tools\roaddb-builder\build\install\roaddb-builder\bin\roaddb-builder.bat D:\OSM
   ```
   Für jeden Extrakt entsteht `D:\OSM\roaddb\<land>.db`. Länder, deren Datei neuer als der
   Extrakt ist, werden übersprungen – nach dem Herunterladen neuer Extrakte einfach erneut
   starten. `--force` erzwingt den Neubau. Für sehr große Länder vorher mehr Speicher geben:
   `set ROADDB_BUILDER_OPTS=-Xmx8g`.
3. Die `.db`-Dateien aufs Handy kopieren und in CoastHint unter **Offline-Daten** importieren.
   Ein erneuter Import desselben Landes ersetzt die alte Datei.

Unter **Einstellungen → Tempolimits aus** steht standardmäßig „Automatisch“: offline, wo eine
installierte Datei die Position abdeckt (auch über Grenzen hinweg, wenn mehrere Länder
installiert sind), sonst online über Overpass.

## Tech-Stack

Kotlin · Jetpack Compose · Coroutines/Flow · Foreground Service · DataStore · JUnit 5

## Status

Phase 1 (MVP) und Phase 2 (Offline-Tempolimits) sind umgesetzt, aber noch nicht im
Straßenverkehr erprobt. Details zu Architektur und Vorgehen: [CLAUDE.md](CLAUDE.md).

Module: `app` (Android-App), `roaddb` (Dateiformat der Offline-Datenbank, reines Kotlin),
`tools/roaddb-builder` (Konverter `.osm.pbf` → Datenbank, läuft am PC).

## Sicherheit

Die App gibt nur Hinweise und greift nie ins Fahrzeug ein. Während der Fahrt ist keine
Bedienung nötig; Einstellungen sind nur im Stand zugänglich.
