package io.github.dansparker.coasthint.core

/** How one upcoming event relates to the current driving state. */
data class Assessment(
    val event: UpcomingEvent,
    val targetSpeedMps: Double,
    /** d_coast: distance needed to coast down to the target speed, including reaction time. */
    val coastDistanceM: Double,
    /** d_coast + d_margin: the cue fires once the event is this close. */
    val triggerDistanceM: Double,
    /** v1 > v2 + v_tol, i.e. slowing down for this event makes sense at all. */
    val relevant: Boolean,
) {
    /** Distance left until the trigger point; zero or negative once it has been reached. */
    val slackM: Double get() = event.distanceM - triggerDistanceM

    val triggered: Boolean get() = relevant && slackM <= 0
}

data class Evaluation(
    val state: DrivingState,
    val assessments: List<Assessment>,
    /** The event to announce now, or null if no cue should be given. */
    val cue: Assessment?,
) {
    /** The relevant event whose trigger point comes first, for display and logging. */
    val next: Assessment? get() = assessments.filter { it.relevant }.minByOrNull { it.slackM }
}

/**
 * Decides when to tell the driver to lift off the throttle. Pure Kotlin, no Android dependencies.
 *
 * For each event with distance d_event and target speed v2 at own speed v1:
 * ```
 * d_coast = (v1² − v2²) / (2 · a_coast) + v1 · t_react   (or the calibrated quadratic model)
 * cue when d_event <= d_coast + d_margin and v1 > v2 + v_tol
 * ```
 * Each event is announced at most once. If several events trigger at the same time, the one
 * with the earliest trigger point wins and the others count as covered by that cue.
 */
class CoastAdvisor(var settings: CoastSettings = CoastSettings()) {
    private val maneuverTracker = ManeuverTracker()
    private val cuedIds = LinkedHashSet<String>()

    fun evaluate(
        state: DrivingState,
        nav: NavInfo?,
        speedLimit: UpcomingEvent.SpeedLimit?,
    ): Evaluation {
        val s = settings
        val events = buildList {
            nav?.let { add(maneuverTracker.track(it)) }
            speedLimit?.let { add(it) }
        }
        val assessments = events.mapNotNull { assess(it, state.speedMps, s) }

        val gateOpen = state.speedMps >= kmhToMps(s.minSpeedKmh) &&
            state.accelerationMps2 >= s.alreadyDeceleratingMps2
        val pending = assessments.filter { it.triggered && it.event.id !in cuedIds }
        val cue = if (gateOpen) pending.minByOrNull { it.slackM } else null
        if (cue != null) pending.forEach { remember(it.event.id) }

        return Evaluation(state, assessments, cue)
    }

    private fun assess(event: UpcomingEvent, v1: Double, s: CoastSettings): Assessment? {
        val targetKmh = when (event) {
            is UpcomingEvent.Maneuver -> s.targetSpeedKmh(event.turnType) ?: return null
            is UpcomingEvent.SpeedLimit -> event.limitKmh.toDouble()
        }
        val v2 = kmhToMps(targetKmh)
        val coast = coastDistance(v1, v2, s.coastModel, s.reactionTimeS)
        return Assessment(
            event = event,
            targetSpeedMps = v2,
            coastDistanceM = coast,
            triggerDistanceM = coast + s.marginM,
            relevant = v1 > v2 + kmhToMps(s.speedToleranceKmh),
        )
    }

    private fun remember(id: String) {
        cuedIds.add(id)
        if (cuedIds.size > MAX_REMEMBERED_IDS) cuedIds.remove(cuedIds.first())
    }

    companion object {
        private const val MAX_REMEMBERED_IDS = 64

        fun coastDistance(v1Mps: Double, v2Mps: Double, model: CoastModel, reactionTimeS: Double): Double =
            model.distanceM(v1Mps, v2Mps) + v1Mps * reactionTimeS
    }
}
