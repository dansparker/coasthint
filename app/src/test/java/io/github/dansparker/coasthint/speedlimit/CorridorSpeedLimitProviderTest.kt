package io.github.dansparker.coasthint.speedlimit

import io.github.dansparker.coasthint.location.PositionFix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CorridorSpeedLimitProviderTest {
    private val json = TestRoads.Builder()
        .node(1, 0.0, 0.0).node(2, 0.0, 500.0).node(3, 0.0, 1_000.0)
        .way(1, 1, 2, maxspeed = 100)
        .way(2, 2, 3, maxspeed = 70)
        .overpassJson()

    private fun fix(atMillis: Long, speedMps: Double = 27.0, y: Double = 100.0): PositionFix {
        val p = TestRoads.at(0.0, y)
        return PositionFix(atMillis, p.lat, p.lon, speedMps, bearingDeg = 0.0, accuracyM = 5.0)
    }

    private fun TestScope.provider(fetch: suspend (String) -> String): CorridorSpeedLimitProvider {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return CorridorSpeedLimitProvider(this, OverpassLoader(fetch, dispatcher), computeDispatcher = dispatcher)
    }

    @Test
    fun `loads the corridor and reports current and upcoming limit`() = runTest {
        var queries = 0
        val provider = provider { queries++; json }
        assertNull(provider.lookup(fix(0), 100.0).road)
        advanceUntilIdle()

        val info = provider.lookup(fix(1_000), 100.0)
        assertEquals(1L, info.road?.id)
        assertEquals(Maxspeed.Limit(100), info.current)
        assertEquals(70, info.ahead?.limitKmh)
        assertEquals(SpeedLimitStatus.Ready(2, RoadDataSource.ONLINE), provider.status.value)
        assertEquals(1, queries)
    }

    @Test
    fun `no bearing based matching while almost standing still`() = runTest {
        val provider = provider { json }
        provider.lookup(fix(0), 100.0)
        advanceUntilIdle()
        assertNull(provider.lookup(fix(1_000, speedMps = 1.0), 100.0).road)
    }

    @Test
    fun `failed request is retried only after the backoff`() = runTest {
        var queries = 0
        val provider = provider {
            queries++
            throw IOException("offline")
        }
        provider.lookup(fix(0), 100.0)
        advanceUntilIdle()
        assertTrue(provider.status.value is SpeedLimitStatus.Failed)

        provider.lookup(fix(5_000), 100.0)
        advanceUntilIdle()
        assertEquals(1, queries)

        provider.lookup(fix(16_000), 100.0)
        advanceUntilIdle()
        assertEquals(2, queries)
    }

    @Test
    fun `refetches after driving 500 m`() = runTest {
        var queries = 0
        val provider = provider { queries++; json }
        provider.lookup(fix(0, y = 0.0), 100.0)
        advanceUntilIdle()
        provider.lookup(fix(20_000, y = 400.0), 100.0)
        advanceUntilIdle()
        assertEquals(1, queries)
        provider.lookup(fix(30_000, y = 600.0), 100.0)
        advanceUntilIdle()
        assertEquals(2, queries)
    }
}
