package io.github.dansparker.coasthint.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.dansparker.coasthint.core.CoastMeasurement
import io.github.dansparker.coasthint.core.CoastMode
import io.github.dansparker.coasthint.core.CoastSettings
import io.github.dansparker.coasthint.speedlimit.SpeedLimitSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Every setting must survive a write and a fresh read. Uses an in-memory Preferences store:
 * what is tested is our mapping of each field; the file format is DataStore's business (and its
 * JVM file storage cannot replace files on Windows).
 */
class SettingsRepositoryTest {

    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val lock = Mutex()
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

    private val changed = AppSettings(
        coast = CoastSettings(
            mode = CoastMode.SAILING,
            engineBrakingDecelerationMps2 = 0.75,
            sailingDecelerationMps2 = 0.25,
            useQuadraticModel = true,
            reactionTimeS = 1.5,
            marginM = 50.0,
            speedToleranceKmh = 5.0,
            turnTargetKmh = 30.0,
            sharpTurnTargetKmh = 10.0,
            slightTurnTargetKmh = 65.0,
            roundaboutTargetKmh = 35.0,
        ),
        output = OutputSettings(tone = false, speech = true, vibration = true),
        speedLimitSource = SpeedLimitSource.OFFLINE,
        overpassServer = "https://overpass.example.org/api/interpreter",
        tripLogEnabled = false,
        calibrations = mapOf(
            CoastMode.ENGINE_BRAKING to listOf(CoastMeasurement(0.55, 25.0, 15.0, 18.0, 0.97)),
            CoastMode.SAILING to listOf(CoastMeasurement(0.31, 22.0, 17.0, 16.0, 0.95), CoastMeasurement(0.29, 30.0, 24.0, 20.0, 0.93)),
        ),
    )

    @Test
    fun `fresh store gives the defaults`() = runTest {
        assertEquals(AppSettings(), SettingsRepository(MemoryStore()).settings.first())
    }

    @Test
    fun `all settings survive a round trip`() = runTest {
        val store = MemoryStore()
        SettingsRepository(store).update { changed }
        // A second repository on the same store reads what the first one wrote.
        val reread = SettingsRepository(store).settings.first()
        assertEquals(changed.copy(calibrations = emptyMap()), reread.copy(calibrations = emptyMap()))
        assertEquals(changed.calibrations.keys, reread.calibrations.keys)
        assertEquals(0.55, reread.measurements(CoastMode.ENGINE_BRAKING).single().decelerationMps2, 1e-6)
        assertEquals(listOf(0.31, 0.29), reread.measurements(CoastMode.SAILING).map { it.decelerationMps2 })
    }

    @Test
    fun `disabled slight-turn cue stays disabled`() = runTest {
        val repository = SettingsRepository(MemoryStore())
        repository.update { it.copy(coast = it.coast.copy(slightTurnTargetKmh = 60.0)) }
        repository.update { it.copy(coast = it.coast.copy(slightTurnTargetKmh = null)) }
        assertNull(repository.settings.first().coast.slightTurnTargetKmh)
    }

    @Test
    fun `calibrations are added and cleared per mode`() = runTest {
        val repository = SettingsRepository(MemoryStore())
        repository.addMeasurement(CoastMode.ENGINE_BRAKING, CoastMeasurement(0.5, 25.0, 15.0, 18.0, 0.97))
        repository.addMeasurement(CoastMode.ENGINE_BRAKING, CoastMeasurement(0.6, 30.0, 20.0, 15.0, 0.96))
        repository.addMeasurement(CoastMode.SAILING, CoastMeasurement(0.3, 25.0, 20.0, 15.0, 0.95))
        var settings = repository.settings.first()
        assertEquals(0.55, settings.effectiveCoast.engineBrakingDecelerationMps2, 1e-6)
        repository.clearMeasurements(CoastMode.ENGINE_BRAKING)
        settings = repository.settings.first()
        assertEquals(emptyList<CoastMeasurement>(), settings.measurements(CoastMode.ENGINE_BRAKING))
        assertEquals(1, settings.measurements(CoastMode.SAILING).size)
        assertEquals(CoastSettings().engineBrakingDecelerationMps2, settings.effectiveCoast.engineBrakingDecelerationMps2)
    }

    @Test
    fun `unknown stored values fall back to defaults`() = runTest {
        val store = MemoryStore()
        store.edit {
            it[stringPreferencesKey("mode")] = "TURBO"
            it[stringPreferencesKey("speed_limit_source")] = "SATELLITE"
            it[stringPreferencesKey("overpass_server")] = "  "
        }
        val settings = SettingsRepository(store).settings.first()
        assertEquals(CoastMode.ENGINE_BRAKING, settings.coast.mode)
        assertEquals(SpeedLimitSource.AUTO, settings.speedLimitSource)
        assertEquals(AppSettings().overpassServer, settings.overpassServer)
    }
}
