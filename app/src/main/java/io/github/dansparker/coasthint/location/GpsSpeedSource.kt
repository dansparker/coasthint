package io.github.dansparker.coasthint.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapNotNull

/** GPS fixes at 1 Hz with high accuracy; also usable as plain [SpeedSource]. */
class GpsSpeedSource(context: Context) : SpeedSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun speeds(): Flow<SpeedSample> =
        fixes().mapNotNull { fix -> fix.speedMps?.let { SpeedSample(fix.elapsedMillis, it) } }

    // The service checks the location permission before collecting this flow.
    @SuppressLint("MissingPermission")
    fun fixes(): Flow<PositionFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(INTERVAL_MILLIS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach {
                    trySend(
                        PositionFix(
                            elapsedMillis = it.elapsedRealtimeNanos / 1_000_000,
                            lat = it.latitude,
                            lon = it.longitude,
                            speedMps = if (it.hasSpeed()) it.speed.toDouble() else null,
                            bearingDeg = if (it.hasBearing()) it.bearing.toDouble() else null,
                            accuracyM = if (it.hasAccuracy()) it.accuracy.toDouble() else null,
                        ),
                    )
                }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private companion object {
        const val INTERVAL_MILLIS = 1_000L
    }
}
