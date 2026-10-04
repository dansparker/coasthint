package io.github.dansparker.coasthint.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CoastAdvisorTest {

    private fun cruising(kmh: Double) = DrivingState(kmhToMps(kmh), 0.0)

    private fun limit(distanceM: Double, limitKmh: Int, wayId: Long = 1) =
        UpcomingEvent.SpeedLimit(distanceM, limitKmh, wayId, lat = 48.2, lon = 16.37)

    @Nested
    inner class Formula {
        @Test
        fun `coast distance follows the formula`() {
            // (20² − 5²) / (2 · 0.6) + 20 · 2 = 312.5 + 40
            assertEquals(352.5, CoastAdvisor.coastDistance(20.0, 5.0, CoastModel.Constant(0.6), 2.0), 1e-9)
        }

        @Test
        fun `sailing mode needs a longer coast distance`() {
            val advisor = CoastAdvisor(CoastSettings(mode = CoastMode.SAILING))
            val eval = advisor.evaluate(DrivingState(20.0, 0.0), null, limit(2000.0, 18))
            // (20² − 5²) / (2 · 0.3) + 20 · 2 = 625 + 40
            assertEquals(665.0, eval.assessments.single().coastDistanceM, 1e-9)
        }

        @Test
        fun `calibrated quadratic model is used when enabled`() {
            val quadratic = CoastModel.Quadratic(c0 = 0.3, c2 = 0.0005)
            val settings = CoastSettings(engineBrakingQuadratic = quadratic, useQuadraticModel = true)
            val eval = CoastAdvisor(settings).evaluate(DrivingState(20.0, 0.0), null, limit(2000.0, 18))
            assertEquals(quadratic.distanceM(20.0, 5.0) + 40.0, eval.assessments.single().coastDistanceM, 1e-9)
            val disabled = CoastAdvisor(settings.copy(useQuadraticModel = false))
                .evaluate(DrivingState(20.0, 0.0), null, limit(2000.0, 18))
            assertEquals(352.5, disabled.assessments.single().coastDistanceM, 1e-9)
        }

        @Test
        fun `trigger distance adds the margin`() {
            val eval = CoastAdvisor().evaluate(DrivingState(20.0, 0.0), null, limit(2000.0, 18))
            assertEquals(382.5, eval.assessments.single().triggerDistanceM, 1e-9)
        }
    }

    @Nested
    inner class Triggering {
        @Test
        fun `fires once the event is within trigger distance`() {
            val advisor = CoastAdvisor()
            // 100 km/h → 25 km/h turn: d_coast ≈ 658.4 m, trigger ≈ 688.4 m
            assertNull(advisor.evaluate(cruising(100.0), NavInfo(700, TurnType.TL), null).cue)
            val cue = advisor.evaluate(cruising(100.0), NavInfo(680, TurnType.TL), null).cue
            assertNotNull(cue)
            assertEquals(658.37, cue!!.coastDistanceM, 0.01)
        }

        @Test
        fun `fires just inside the trigger distance`() {
            val eval = CoastAdvisor().evaluate(DrivingState(20.0, 0.0), null, limit(382.0, 18))
            assertNotNull(eval.cue)
        }

        @Test
        fun `no cue if speed is within tolerance of the target`() {
            // 105 km/h towards 100 km/h, tolerance 8 km/h
            val eval = CoastAdvisor().evaluate(cruising(105.0), null, limit(10.0, 100))
            assertNull(eval.cue)
            assertFalse(eval.assessments.single().relevant)
        }

        @Test
        fun `cue just above tolerance`() {
            val eval = CoastAdvisor().evaluate(cruising(109.0), null, limit(10.0, 100))
            assertNotNull(eval.cue)
        }

        @Test
        fun `no cue below minimum speed`() {
            val eval = CoastAdvisor().evaluate(cruising(29.0), NavInfo(10, TurnType.TU), null)
            assertNull(eval.cue)
        }

        @Test
        fun `no cue while the driver is already decelerating`() {
            val state = DrivingState(kmhToMps(80.0), -0.5)
            assertNull(CoastAdvisor().evaluate(state, NavInfo(100, TurnType.TR), null).cue)
        }

        @Test
        fun `mild deceleration does not suppress the cue`() {
            val state = DrivingState(kmhToMps(80.0), -0.3)
            assertNotNull(CoastAdvisor().evaluate(state, NavInfo(100, TurnType.TR), null).cue)
        }

        @Test
        fun `cue comes once deceleration stops`() {
            val advisor = CoastAdvisor()
            assertNull(advisor.evaluate(DrivingState(kmhToMps(80.0), -1.0), NavInfo(200, TurnType.TR), null).cue)
            assertNotNull(advisor.evaluate(cruising(80.0), NavInfo(180, TurnType.TR), null).cue)
        }

        @Test
        fun `straight and off-route maneuvers never cue`() {
            val advisor = CoastAdvisor()
            assertNull(advisor.evaluate(cruising(100.0), NavInfo(10, TurnType.C), null).cue)
            assertNull(advisor.evaluate(cruising(100.0), NavInfo(10, TurnType.OFFR), null).cue)
        }

        @Test
        fun `slight turns cue only when configured`() {
            assertNull(CoastAdvisor().evaluate(cruising(100.0), NavInfo(10, TurnType.KL), null).cue)
            val advisor = CoastAdvisor(CoastSettings(slightTurnTargetKmh = 60.0))
            assertNotNull(advisor.evaluate(cruising(100.0), NavInfo(10, TurnType.KL), null).cue)
        }

        @Test
        fun `target speeds per turn category`() {
            val s = CoastSettings()
            assertEquals(25.0, s.targetSpeedKmh(TurnType.TL))
            assertEquals(25.0, s.targetSpeedKmh(TurnType.TR))
            assertEquals(15.0, s.targetSpeedKmh(TurnType.TSHL))
            assertEquals(15.0, s.targetSpeedKmh(TurnType.TU))
            assertEquals(15.0, s.targetSpeedKmh(TurnType.TRU))
            assertEquals(30.0, s.targetSpeedKmh(TurnType.RNDB))
            assertEquals(30.0, s.targetSpeedKmh(TurnType.RNLB))
            assertNull(s.targetSpeedKmh(TurnType.TSLR))
            assertNull(s.targetSpeedKmh(TurnType.C))
            assertNull(s.targetSpeedKmh(99))
        }
    }

    @Nested
    inner class Debounce {
        @Test
        fun `one cue per maneuver while approaching`() {
            val advisor = CoastAdvisor()
            val cues = listOf(300, 250, 200, 150, 100, 50).count {
                advisor.evaluate(cruising(70.0), NavInfo(it, TurnType.TR), null).cue != null
            }
            assertEquals(1, cues)
        }

        @Test
        fun `GPS jitter in distance does not create a new maneuver`() {
            val advisor = CoastAdvisor()
            assertNotNull(advisor.evaluate(cruising(70.0), NavInfo(200, TurnType.TR), null).cue)
            assertNull(advisor.evaluate(cruising(70.0), NavInfo(215, TurnType.TR), null).cue)
        }

        @Test
        fun `distance jump means a new maneuver and allows a new cue`() {
            val advisor = CoastAdvisor()
            assertNotNull(advisor.evaluate(cruising(70.0), NavInfo(200, TurnType.TR), null).cue)
            assertNull(advisor.evaluate(cruising(70.0), NavInfo(20, TurnType.TR), null).cue)
            // Maneuver passed, the next one is 150 m ahead
            assertNotNull(advisor.evaluate(cruising(70.0), NavInfo(150, TurnType.TR), null).cue)
        }

        @Test
        fun `turn type change means a new maneuver`() {
            val advisor = CoastAdvisor()
            assertNotNull(advisor.evaluate(cruising(70.0), NavInfo(200, TurnType.TR), null).cue)
            assertNotNull(advisor.evaluate(cruising(70.0), NavInfo(190, TurnType.TL), null).cue)
        }

        @Test
        fun `navigation dropout does not repeat the cue`() {
            val advisor = CoastAdvisor()
            assertNotNull(advisor.evaluate(cruising(70.0), NavInfo(200, TurnType.TR), null).cue)
            advisor.evaluate(cruising(70.0), null, null)
            assertNull(advisor.evaluate(cruising(70.0), NavInfo(180, TurnType.TR), null).cue)
        }

        @Test
        fun `one cue per speed limit`() {
            val advisor = CoastAdvisor()
            assertNotNull(advisor.evaluate(cruising(100.0), null, limit(300.0, 70)).cue)
            assertNull(advisor.evaluate(cruising(100.0), null, limit(250.0, 70)).cue)
        }

        @Test
        fun `different speed limit position gives a new cue`() {
            val advisor = CoastAdvisor()
            assertNotNull(advisor.evaluate(cruising(100.0), null, limit(300.0, 70, wayId = 1)).cue)
            assertNotNull(advisor.evaluate(cruising(100.0), null, limit(300.0, 70, wayId = 2)).cue)
        }

        @Test
        fun `speed limit id is stable while approaching`() {
            assertEquals(limit(300.0, 70).id, limit(120.0, 70).id)
            assertNotEquals(limit(300.0, 70, wayId = 1).id, limit(300.0, 70, wayId = 2).id)
        }
    }

    @Nested
    inner class Priority {
        @Test
        fun `earlier trigger point wins`() {
            // 100 km/h: turn (25 km/h) triggers at ≈ 688 m, limit 70 at ≈ 414 m
            val eval = CoastAdvisor().evaluate(cruising(100.0), NavInfo(600, TurnType.TR), limit(350.0, 70))
            assertTrue(eval.cue!!.event is UpcomingEvent.Maneuver)
        }

        @Test
        fun `limit wins when its trigger point comes first`() {
            // Turn only ≈ 48 m past its trigger point, limit ≈ 314 m past its trigger point
            val eval = CoastAdvisor().evaluate(cruising(100.0), NavInfo(640, TurnType.TR), limit(100.0, 70))
            assertTrue(eval.cue!!.event is UpcomingEvent.SpeedLimit)
        }

        @Test
        fun `simultaneous events are covered by a single cue`() {
            val advisor = CoastAdvisor()
            assertNotNull(advisor.evaluate(cruising(100.0), NavInfo(600, TurnType.TR), limit(350.0, 70)).cue)
            assertNull(advisor.evaluate(cruising(100.0), NavInfo(580, TurnType.TR), limit(330.0, 70)).cue)
        }

        @Test
        fun `next is the relevant event with the earliest trigger point`() {
            val eval = CoastAdvisor().evaluate(cruising(100.0), NavInfo(2000, TurnType.TR), limit(500.0, 70))
            assertNull(eval.cue)
            assertTrue(eval.next!!.event is UpcomingEvent.SpeedLimit)
        }

        @Test
        fun `irrelevant events are not reported as next`() {
            val eval = CoastAdvisor().evaluate(cruising(60.0), null, limit(500.0, 100))
            assertNull(eval.next)
        }
    }
}
