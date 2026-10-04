package io.github.dansparker.coasthint.speedlimit

import io.github.dansparker.coasthint.roaddb.Bounds
import io.github.dansparker.coasthint.roaddb.RoadDbLibrary
import io.github.dansparker.coasthint.roaddb.StoredWay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos

enum class RoadDataSource { ONLINE, OFFLINE }

/** Where speed limits should come from (user setting). */
enum class SpeedLimitSource {
    /** Offline database where it covers the position, Overpass elsewhere. */
    AUTO,
    ONLINE,
    OFFLINE,
}

/** The area to load: the corridor ahead of [position], widened by [radiusM]. */
data class CorridorRequest(val position: LatLon, val corridor: List<LatLon>, val radiusM: Int)

data class LoadedRoads(val network: RoadNetwork, val source: RoadDataSource)

fun interface CorridorLoader {
    suspend fun load(request: CorridorRequest): LoadedRoads
}

/** Online: asks an Overpass server. */
class OverpassLoader(
    private val fetch: suspend (query: String) -> String,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CorridorLoader {
    override suspend fun load(request: CorridorRequest): LoadedRoads {
        val json = fetch(OverpassQuery.build(request.corridor, request.radiusM))
        return LoadedRoads(withContext(parseDispatcher) { OverpassQuery.parse(json) }, RoadDataSource.ONLINE)
    }
}

/** Offline: reads the installed road databases. */
class OfflineLoader(
    private val database: RoadDbLibrary,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CorridorLoader {
    override suspend fun load(request: CorridorRequest): LoadedRoads = withContext(ioDispatcher) {
        val ways = database.waysIn(corridorBounds(request.corridor, request.radiusM))
        LoadedRoads(roadNetworkOf(ways), RoadDataSource.OFFLINE)
    }
}

/**
 * Picks online or offline per request according to the setting and the offline coverage.
 * Coverage is first checked by bounding box, which is coarse (Munich lies inside Austria's), so in
 * [SpeedLimitSource.AUTO] offline data only counts if it has a road near the position; otherwise
 * Overpass is asked.
 */
class SelectingLoader(
    private val setting: () -> SpeedLimitSource,
    private val offlineCovers: suspend (LatLon) -> Boolean,
    private val online: CorridorLoader,
    private val offline: CorridorLoader,
) : CorridorLoader {
    override suspend fun load(request: CorridorRequest): LoadedRoads {
        val setting = setting()
        if (choose(setting, offlineCovers(request.position)) == RoadDataSource.ONLINE) return online.load(request)
        val loaded = offline.load(request)
        if (setting == SpeedLimitSource.AUTO && !loaded.network.hasRoadNear(request.position, COVERAGE_RADIUS_M)) {
            return online.load(request)
        }
        return loaded
    }

    companion object {
        private const val COVERAGE_RADIUS_M = 50.0

        fun choose(setting: SpeedLimitSource, offlineCovers: Boolean): RoadDataSource = when (setting) {
            SpeedLimitSource.ONLINE -> RoadDataSource.ONLINE
            SpeedLimitSource.OFFLINE -> RoadDataSource.OFFLINE
            SpeedLimitSource.AUTO -> if (offlineCovers) RoadDataSource.OFFLINE else RoadDataSource.ONLINE
        }
    }
}

/** Bounding box of the corridor points, widened by [radiusM] on every side. */
fun corridorBounds(corridor: List<LatLon>, radiusM: Int): Bounds {
    val midLat = corridor.map { it.lat }.average()
    val dLat = radiusM / METERS_PER_DEGREE
    val dLon = radiusM / (METERS_PER_DEGREE * cos(Math.toRadians(midLat)))
    return Bounds(
        corridor.minOf { it.lat } - dLat,
        corridor.minOf { it.lon } - dLon,
        corridor.maxOf { it.lat } + dLat,
        corridor.maxOf { it.lon } + dLon,
    )
}

private const val METERS_PER_DEGREE = 111_320.0

/** Turns stored ways into the same [RoadNetwork] the online path produces. */
fun roadNetworkOf(ways: List<StoredWay>): RoadNetwork {
    val nodes = HashMap<Long, LatLon>()
    val roads = ways.map { way ->
        for (i in way.nodeIds.indices) nodes[way.nodeIds[i]] = LatLon(way.lat(i), way.lon(i))
        RoadWay.fromTags(way.id, way.nodeIds.toList(), way.tags)
    }
    return RoadNetwork(roads, nodes)
}
