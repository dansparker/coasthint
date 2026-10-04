package io.github.dansparker.coasthint.speedlimit

import io.github.dansparker.coasthint.core.Backoff
import io.github.dansparker.coasthint.location.PositionFix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Speed limits from OpenStreetMap roads around the corridor ahead. Loads the corridor in the
 * background through [loader] (Overpass, offline database or a choice of both, see
 * [FetchPolicy] for when), keeps the last few corridors and matches every fix against them.
 * [lookup] must be called from the thread [scope] runs on.
 */
class CorridorSpeedLimitProvider(
    private val scope: CoroutineScope,
    private val loader: CorridorLoader,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val policy: FetchPolicy = FetchPolicy(),
    private val matcher: MapMatcher = MapMatcher(),
    private val lookAhead: LookAhead = LookAhead(),
    private val corridorRadiusM: Int = 250,
    private val keptCorridors: Int = 3,
    /** GPS bearing is unreliable when (almost) standing still. */
    private val minBearingSpeedMps: Double = 3.0,
) : SpeedLimitProvider {
    private val _status = MutableStateFlow<SpeedLimitStatus>(SpeedLimitStatus.Idle)
    override val status: StateFlow<SpeedLimitStatus> = _status.asStateFlow()

    private val corridors = ArrayDeque<RoadNetwork>()
    private var network = RoadNetwork.EMPTY
    private var lastFetch: FetchRecord? = null
    private var fetchJob: Job? = null
    private var retryAtMillis = 0L
    private val backoff = Backoff(initialMillis = 15_000, maxMillis = 300_000)

    override fun lookup(fix: PositionFix, speedKmh: Double): SpeedLimitInfo {
        val position = LatLon(fix.lat, fix.lon)
        val bearing = fix.bearingDeg?.takeIf { (fix.speedMps ?: 0.0) >= minBearingSpeedMps }
        maybeFetch(position, bearing, fix.elapsedMillis)
        if (bearing == null) return SpeedLimitInfo()
        val net = network
        val match = matcher.match(net, position, bearing) ?: return SpeedLimitInfo()
        return SpeedLimitInfo(
            road = match.way,
            current = match.way.maxspeed(match.forward),
            ahead = lookAhead.nextLowerLimit(net, match, speedKmh),
        )
    }

    private fun maybeFetch(position: LatLon, bearing: Double?, nowMillis: Long) {
        if (fetchJob?.isActive == true || nowMillis < retryAtMillis) return
        if (!policy.shouldFetch(lastFetch, position, bearing, nowMillis)) return
        lastFetch = FetchRecord(position, bearing, nowMillis)
        val request = CorridorRequest(position, OverpassQuery.corridor(position, bearing), corridorRadiusM)
        _status.value = SpeedLimitStatus.Loading
        fetchJob = scope.launch {
            try {
                val loaded = loader.load(request)
                corridors.addLast(loaded.network)
                while (corridors.size > keptCorridors) corridors.removeFirst()
                network = withContext(computeDispatcher) { RoadNetwork.merge(corridors.toList()) }
                backoff.reset()
                _status.value = SpeedLimitStatus.Ready(network.ways.size, loaded.source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val delayMillis = backoff.next()
                retryAtMillis = nowMillis + delayMillis
                lastFetch = null
                _status.value = SpeedLimitStatus.Failed(e.message ?: e.javaClass.simpleName, delayMillis)
            }
        }
    }
}
