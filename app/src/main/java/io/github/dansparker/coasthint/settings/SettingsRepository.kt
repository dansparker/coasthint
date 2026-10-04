package io.github.dansparker.coasthint.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.dansparker.coasthint.core.CoastMeasurement
import io.github.dansparker.coasthint.core.CoastMode
import io.github.dansparker.coasthint.core.CoastSettings
import io.github.dansparker.coasthint.speedlimit.SpeedLimitSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists [AppSettings] in a Preferences DataStore. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map(::read)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    suspend fun addMeasurement(mode: CoastMode, measurement: CoastMeasurement) = update { s ->
        s.copy(calibrations = s.calibrations + (mode to s.measurements(mode) + measurement))
    }

    suspend fun clearMeasurements(mode: CoastMode) = update { s -> s.copy(calibrations = s.calibrations - mode) }

    private fun read(p: Preferences): AppSettings {
        val d = CoastSettings()
        val coast = CoastSettings(
            mode = p[MODE]?.let { runCatching { CoastMode.valueOf(it) }.getOrNull() } ?: d.mode,
            engineBrakingDecelerationMps2 = p[DECEL_ENGINE] ?: d.engineBrakingDecelerationMps2,
            sailingDecelerationMps2 = p[DECEL_SAILING] ?: d.sailingDecelerationMps2,
            useQuadraticModel = p[USE_QUADRATIC] ?: d.useQuadraticModel,
            reactionTimeS = p[REACTION] ?: d.reactionTimeS,
            marginM = p[MARGIN] ?: d.marginM,
            speedToleranceKmh = p[TOLERANCE] ?: d.speedToleranceKmh,
            turnTargetKmh = p[TARGET_TURN] ?: d.turnTargetKmh,
            sharpTurnTargetKmh = p[TARGET_SHARP] ?: d.sharpTurnTargetKmh,
            // Stored as 0 when slight turns should not cue.
            slightTurnTargetKmh = p[TARGET_SLIGHT]?.takeIf { it > 0 } ?: d.slightTurnTargetKmh,
            roundaboutTargetKmh = p[TARGET_ROUNDABOUT] ?: d.roundaboutTargetKmh,
        )
        val output = OutputSettings(
            tone = p[OUT_TONE] ?: true,
            speech = p[OUT_SPEECH] ?: false,
            vibration = p[OUT_VIBRATION] ?: false,
        )
        return AppSettings(
            coast = coast,
            output = output,
            speedLimitSource = p[LIMIT_SOURCE]?.let { runCatching { SpeedLimitSource.valueOf(it) }.getOrNull() }
                ?: SpeedLimitSource.AUTO,
            overpassServer = p[OVERPASS]?.takeIf { it.isNotBlank() } ?: AppSettings().overpassServer,
            tripLogEnabled = p[TRIP_LOG] ?: true,
            calibrations = CoastMode.entries.associateWith { MeasurementCodec.decode(p[calibrationKey(it)]) }
                .filterValues { it.isNotEmpty() },
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        val c = s.coast
        p[MODE] = c.mode.name
        p[DECEL_ENGINE] = c.engineBrakingDecelerationMps2
        p[DECEL_SAILING] = c.sailingDecelerationMps2
        p[USE_QUADRATIC] = c.useQuadraticModel
        p[REACTION] = c.reactionTimeS
        p[MARGIN] = c.marginM
        p[TOLERANCE] = c.speedToleranceKmh
        c.turnTargetKmh?.let { p[TARGET_TURN] = it }
        c.sharpTurnTargetKmh?.let { p[TARGET_SHARP] = it }
        p[TARGET_SLIGHT] = c.slightTurnTargetKmh ?: 0.0
        c.roundaboutTargetKmh?.let { p[TARGET_ROUNDABOUT] = it }
        p[OUT_TONE] = s.output.tone
        p[OUT_SPEECH] = s.output.speech
        p[OUT_VIBRATION] = s.output.vibration
        p[LIMIT_SOURCE] = s.speedLimitSource.name
        p[OVERPASS] = s.overpassServer.trim()
        p[TRIP_LOG] = s.tripLogEnabled
        CoastMode.entries.forEach { mode -> p[calibrationKey(mode)] = MeasurementCodec.encode(s.measurements(mode)) }
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val DECEL_ENGINE = doublePreferencesKey("decel_engine_braking")
        val DECEL_SAILING = doublePreferencesKey("decel_sailing")
        val USE_QUADRATIC = booleanPreferencesKey("use_quadratic_model")
        val REACTION = doublePreferencesKey("reaction_time_s")
        val MARGIN = doublePreferencesKey("margin_m")
        val TOLERANCE = doublePreferencesKey("speed_tolerance_kmh")
        val TARGET_TURN = doublePreferencesKey("target_turn_kmh")
        val TARGET_SHARP = doublePreferencesKey("target_sharp_turn_kmh")
        val TARGET_SLIGHT = doublePreferencesKey("target_slight_turn_kmh")
        val TARGET_ROUNDABOUT = doublePreferencesKey("target_roundabout_kmh")
        val OUT_TONE = booleanPreferencesKey("output_tone")
        val OUT_SPEECH = booleanPreferencesKey("output_speech")
        val OUT_VIBRATION = booleanPreferencesKey("output_vibration")
        val LIMIT_SOURCE = stringPreferencesKey("speed_limit_source")
        val OVERPASS = stringPreferencesKey("overpass_server")
        val TRIP_LOG = booleanPreferencesKey("trip_log")

        fun calibrationKey(mode: CoastMode) = stringPreferencesKey("calibration_${mode.name.lowercase()}")
    }
}
