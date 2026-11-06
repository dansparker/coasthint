package io.github.dansparker.coasthint.simulation

import io.github.dansparker.coasthint.core.CoastAdvisor
import io.github.dansparker.coasthint.core.NavInfo
import io.github.dansparker.coasthint.core.SpeedFilter
import io.github.dansparker.coasthint.core.TurnType
import io.github.dansparker.coasthint.core.UpcomingEvent
import io.github.dansparker.coasthint.core.kmhToMps
import io.github.dansparker.coasthint.core.mpsToKmh
import io.github.dansparker.coasthint.location.PositionFix
import io.github.dansparker.coasthint.log.TripCsv
import io.github.dansparker.coasthint.log.TripLogEntry
import io.github.dansparker.coasthint.speedlimit.CorridorLoader
import io.github.dansparker.coasthint.speedlimit.CorridorSpeedLimitProvider
import io.github.dansparker.coasthint.speedlimit.Geo
import io.github.dansparker.coasthint.speedlimit.LatLon
import io.github.dansparker.coasthint.speedlimit.LoadedRoads
import io.github.dansparker.coasthint.speedlimit.Maxspeed
import io.github.dansparker.coasthint.speedlimit.OverpassQuery
import io.github.dansparker.coasthint.speedlimit.RoadDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.ZoneOffset
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * End-to-end drive over a real road (OSM data in `roads/ruggell-landstrasse.json`): a car at
 * 80 km/h approaches a 50 zone and then a right turn, with noisy 1 Hz GPS. Everything except
 * Android runs for real: speed filter, map matching, look-ahead, advisor, trip log. The simulated
 * driver lifts off the throttle one second after each cue and coasts at 0.6 m/s².
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveSimulationTest {

    private class Route(val points: List<LatLon>) {
        val distances: DoubleArray = DoubleArray(points.size).also { d ->
            for (i in 1 until points.size) d[i] = d[i - 1] + Geo.distanceM(points[i - 1], points[i])
        }
        val length get() = distances.last()

        private fun segment(s: Double): Int =
            (1 until points.size).firstOrNull { distances[it] >= s }?.minus(1) ?: (points.size - 2)

        fun position(s: Double): LatLon {
            val i = segment(s)
            val f = ((s - distances[i]) / (distances[i + 1] - distances[i])).coerceIn(0.0, 1.0)
            return Geo.interpolate(points[i], points[i + 1], f)
        }

        fun bearing(s: Double): Double {
            val i = segment(s)
            return Geo.bearingDeg(points[i], points[i + 1])
        }
    }

    private data class Cue(val atM: Double, val event: UpcomingEvent, val trueDistanceM: Double, val speedKmh: Double)

    private data class DriveResult(
        val cues: List<Cue>,
        val speedAtLimitKmh: Double,
        val speedAtTurnKmh: Double,
        val limitReadings: Map<Int, List<Maxspeed?>>,
        val csvRows: List<String>,
    )

    private val fixture = JSONObject(javaClass.getResource("/roads/ruggell-landstrasse.json")!!.readText())
    private val network = OverpassQuery.parse(fixture.toString())
    private val routeJson = fixture.getJSONObject("route")
    private val route = Route(
        routeJson.getJSONArray("points").let { a -> List(a.length()) { a.getJSONArray(it).let { p -> LatLon(p.getDouble(0), p.getDouble(1)) } } },
    )
    private val limitChangeM = routeJson.getDouble("limitChangeDistanceM")
    private val limitWayId = routeJson.getLong("limitChangeWayId")
    private val turnM = routeJson.getDouble("turnDistanceM")

    private fun TestScope.drive(seed: Long, gpsErrorM: Double = 3.0): DriveResult {
        val random = Random(seed)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val loader = CorridorLoader { LoadedRoads(network, RoadDataSource.OFFLINE) }
        val provider = CorridorSpeedLimitProvider(this, loader, computeDispatcher = dispatcher)
        val advisor = CoastAdvisor()
        val filter = SpeedFilter()

        val cruise = kmhToMps(80.0)
        var s = 0.0
        var v = cruise
        var holdSpeed = cruise
        var coastFromMillis = Long.MAX_VALUE
        val cues = mutableListOf<Cue>()
        val readings = mutableMapOf<Int, MutableList<Maxspeed?>>()
        val rows = mutableListOf<String>()
        var speedAtLimit = Double.NaN
        var speedAtTurn = Double.NaN
        var t = 0L

        while (s < turnM) {
            // Physics in 100 ms steps: coast at 0.6 m/s² once the driver reacted, else hold speed.
            repeat(10) {
                val coasting = t >= coastFromMillis && v > holdSpeed
                if (coasting) v = max(holdSpeed, v - 0.6 * 0.1)
                val before = s
                s += v * 0.1
                if (before < limitChangeM && s >= limitChangeM) speedAtLimit = mpsToKmh(v)
                if (before < turnM && s >= turnM) speedAtTurn = mpsToKmh(v)
                t += 100
            }
            if (s >= turnM) break

            // Noisy GPS fix: position error ~gpsErrorM, 0.3 m/s speed error, 3° bearing error.
            val exact = route.position(s)
            val noisy = Geo.destination(
                Geo.destination(exact, random.nextDouble() * 360, gpsErrorM * sqrt(-2 * Math.log(1 - random.nextDouble()))),
                0.0, 0.0,
            )
            val fix = PositionFix(
                elapsedMillis = t,
                lat = noisy.lat,
                lon = noisy.lon,
                speedMps = max(0.0, v + 0.3 * random.nextGaussian()),
                bearingDeg = route.bearing(s) + 3.0 * random.nextGaussian(),
                accuracyM = 4.0,
            )
            val driving = filter.update(fix.elapsedMillis, fix.speedMps!!)
            val info = provider.lookup(fix, mpsToKmh(driving.speedMps))
            runCurrent() // let the provider finish loading
            val nav = NavInfo(distanceToM = (turnM - s).toInt(), turnType = TurnType.TR)
            val evaluation = advisor.evaluate(driving, nav, info.ahead)

            readings.getOrPut(if (s < limitChangeM - 30) 80 else if (s > limitChangeM + 30) 50 else 0) { mutableListOf() }
                .add(info.current)
            evaluation.cue?.let { cue ->
                val trueDistance = if (cue.event is UpcomingEvent.SpeedLimit) limitChangeM - s else turnM - s
                cues += Cue(s, cue.event, trueDistance, mpsToKmh(v))
                // Driver reacts after one second and coasts down to the target speed.
                coastFromMillis = t + 1_000
                holdSpeed = cue.targetSpeedMps
            }
            rows += TripCsv.row(
                TripLogEntry(
                    wallTimeMillis = 1_793_000_000_000 + t, elapsedMillis = t, lat = fix.lat, lon = fix.lon,
                    speedMps = driving.speedMps, accelerationMps2 = driving.accelerationMps2,
                    currentLimit = info.current, navDistanceM = nav.distanceToM, navTurnType = nav.turnType,
                    evaluation = evaluation,
                ),
                ZoneOffset.UTC,
            )
        }
        return DriveResult(cues, speedAtLimit, speedAtTurn, readings, rows)
    }

    /** Where the cue should fire for coasting from [fromKmh] to [toKmh] with the default settings. */
    private fun expectedTriggerM(fromKmh: Double, toKmh: Double): Double {
        val v1 = kmhToMps(fromKmh)
        val v2 = kmhToMps(toKmh)
        return (v1 * v1 - v2 * v2) / (2 * 0.6) + v1 * 2.0 + 30.0
    }

    @ParameterizedTest(name = "GPS noise seed {0}")
    @ValueSource(longs = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20])
    fun `drive into a 50 zone and a right turn`(seed: Long) = runTest {
        val result = drive(seed)
        println(summary(seed, result))
        val description = result.cues.joinToString { "${it.event::class.simpleName} at ${"%.0f".format(it.atM)} m" }

        // Exactly two cues: first the 50 zone, then the turn.
        assertEquals(2, result.cues.size, "cues: $description")
        val (limitCue, turnCue) = result.cues
        val limit = limitCue.event as UpcomingEvent.SpeedLimit
        assertEquals(50, limit.limitKmh)
        assertEquals(limitWayId, limit.wayId)
        assertTrue(turnCue.event is UpcomingEvent.Maneuver)

        // The look-ahead distance along the real road matches the true distance.
        assertEquals(limitCue.trueDistanceM, limit.distanceM, 15.0, "look-ahead distance")

        // The cue fires where the formula says. Later by at most one GPS step (22 m at 80 km/h,
        // 14 m at 50 km/h); earlier or later by the measured-speed error, which shifts the trigger
        // point by v/a + t_react ≈ 25–40 m per m/s (GPS speed noise here: 0.3 m/s, smoothed).
        val trigger = expectedTriggerM(80.0, 50.0)
        assertTrue(limitCue.trueDistanceM in (trigger - 40)..(trigger + 15), "limit cue at ${limitCue.trueDistanceM} m, expected ≈ $trigger m")
        val turnTrigger = expectedTriggerM(50.0, 25.0)
        assertTrue(turnCue.trueDistanceM in (turnTrigger - 30)..(turnTrigger + 15), "turn cue at ${turnCue.trueDistanceM} m, expected ≈ $turnTrigger m")

        // Coasting from the cue is enough: the car arrives at (or below) the target speed.
        assertTrue(result.speedAtLimitKmh <= 50.5, "speed at the 50 sign: ${result.speedAtLimitKmh} km/h")
        assertTrue(result.speedAtTurnKmh <= 25.5, "speed at the turn: ${result.speedAtTurnKmh} km/h")

        // Map matching recognises the current limit despite GPS noise.
        val on80 = result.limitReadings.getValue(80)
        val on50 = result.limitReadings.getValue(50)
        assertTrue(on80.count { it == Maxspeed.Limit(80) } >= on80.size * 0.95, "80 stretch: $on80")
        assertTrue(on50.count { it == Maxspeed.Limit(50) } >= on50.size * 0.95, "50 stretch: $on50")

        // The trip log has one row per fix and marks the two cues.
        assertEquals(result.csvRows.size, on80.size + on50.size + result.limitReadings[0].orEmpty().size)
        assertEquals(2, result.csvRows.count { it.endsWith(",1") })
    }

    /** Poor reception (8 m): the cues still come, but the current limit may be missed more often. */
    @ParameterizedTest(name = "poor GPS, seed {0}")
    @ValueSource(longs = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    fun `poor GPS still gives both cues in time`(seed: Long) = runTest {
        val result = drive(seed, gpsErrorM = 8.0)
        println(summary(seed, result))
        assertEquals(listOf(UpcomingEvent.SpeedLimit::class, UpcomingEvent.Maneuver::class), result.cues.map { it.event::class })
        assertTrue(result.speedAtLimitKmh <= 50.5, "speed at the 50 sign: ${result.speedAtLimitKmh} km/h")
        assertTrue(result.speedAtTurnKmh <= 25.5, "speed at the turn: ${result.speedAtTurnKmh} km/h")
    }

    private fun summary(seed: Long, r: DriveResult): String {
        val matched = r.limitReadings.filterKeys { it != 0 }.map { (limit, readings) ->
            "$limit: %.0f%%".format(100.0 * readings.count { it == Maxspeed.Limit(limit) } / readings.size)
        }
        return "seed $seed: cues " + r.cues.joinToString { c ->
            "${c.event::class.simpleName} at %.0f m (app %.0f m)".format(c.trueDistanceM, c.event.distanceM)
        } + "; at sign %.1f km/h, at turn %.1f km/h".format(r.speedAtLimitKmh, r.speedAtTurnKmh) +
            "; limit recognised $matched"
    }

    @org.junit.jupiter.api.Test
    fun `fixture is consistent`() {
        assertTrue(network.ways.size > 100)
        assertEquals(Maxspeed.Limit(50), network.ways.getValue(limitWayId).maxspeedForward)
        assertTrue(abs(route.length - turnM) < 1.0, "route length ${route.length} vs turn $turnM")
        assertTrue(min(limitChangeM, turnM - limitChangeM) > 500)
    }

    private fun abs(x: Double) = kotlin.math.abs(x)
}
