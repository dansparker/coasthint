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

/** GPS speed at 1 Hz with high accuracy. Fixes without a speed value are skipped. */
class GpsSpeedSource(context: Context) : SpeedSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    // The service checks the location permission before collecting this flow.
    @SuppressLint("MissingPermission")
    override fun speeds(): Flow<SpeedSample> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(INTERVAL_MILLIS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.filter { it.hasSpeed() }.forEach {
                    trySend(SpeedSample(it.elapsedRealtimeNanos / 1_000_000, it.speed.toDouble()))
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
