package io.github.dansparker.coasthint.location

import kotlinx.coroutines.flow.Flow

/** Raw own speed; [elapsedMillis] is on the [android.os.SystemClock.elapsedRealtime] clock. */
data class SpeedSample(val elapsedMillis: Long, val speedMps: Double)

/** Source of the own speed: GPS now, OBD-II later. */
interface SpeedSource {
    fun speeds(): Flow<SpeedSample>
}
