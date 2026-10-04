package io.github.dansparker.coasthint.speedlimit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FetchPolicyTest {
    private val policy = FetchPolicy(refetchDistanceM = 500.0, refetchCourseChangeDeg = 30.0, minIntervalMillis = 10_000)
    private val last = FetchRecord(TestRoads.ORIGIN, bearingDeg = 0.0, elapsedMillis = 0)

    @Test
    fun `fetches when there is no data yet`() {
        assertTrue(policy.shouldFetch(null, TestRoads.ORIGIN, null, 0))
    }

    @Test
    fun `waits until 500 m are driven`() {
        assertFalse(policy.shouldFetch(last, TestRoads.at(0.0, 490.0), 0.0, 60_000))
        assertTrue(policy.shouldFetch(last, TestRoads.at(0.0, 510.0), 0.0, 60_000))
    }

    @Test
    fun `fetches after a course change of more than 30 degrees`() {
        assertFalse(policy.shouldFetch(last, TestRoads.at(0.0, 100.0), 25.0, 60_000))
        assertTrue(policy.shouldFetch(last, TestRoads.at(0.0, 100.0), 35.0, 60_000))
        assertTrue(policy.shouldFetch(last, TestRoads.at(0.0, 100.0), 325.0, 60_000))
    }

    @Test
    fun `respects the minimum interval`() {
        assertFalse(policy.shouldFetch(last, TestRoads.at(0.0, 2_000.0), 90.0, 5_000))
    }

    @Test
    fun `fetches once a bearing becomes available`() {
        val withoutBearing = last.copy(bearingDeg = null)
        assertFalse(policy.shouldFetch(withoutBearing, TestRoads.ORIGIN, null, 60_000))
        assertTrue(policy.shouldFetch(withoutBearing, TestRoads.ORIGIN, 0.0, 60_000))
    }
}
