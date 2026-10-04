package io.github.dansparker.coasthint.core

enum class CoastMode {
    /** Engine braking with gear engaged (fuel cut-off). */
    ENGINE_BRAKING,

    /** Rolling in neutral ("Segeln"). */
    SAILING,
}

/** User-tunable parameters of the coast advice. Speeds in km/h, distances in m, times in s. */
data class CoastSettings(
    val mode: CoastMode = CoastMode.ENGINE_BRAKING,
    val engineBrakingDecelerationMps2: Double = 0.6,
    val sailingDecelerationMps2: Double = 0.3,
    val reactionTimeS: Double = 2.0,
    val marginM: Double = 30.0,
    val speedToleranceKmh: Double = 8.0,
    /** No cue below this speed. */
    val minSpeedKmh: Double = 30.0,
    /** If dv/dt is below this, the driver is already slowing down and no cue is given. */
    val alreadyDeceleratingMps2: Double = -0.4,
    val turnTargetKmh: Double? = 25.0,
    val sharpTurnTargetKmh: Double? = 15.0,
    val slightTurnTargetKmh: Double? = null,
    val roundaboutTargetKmh: Double? = 30.0,
) {
    val coastDecelerationMps2: Double
        get() = when (mode) {
            CoastMode.ENGINE_BRAKING -> engineBrakingDecelerationMps2
            CoastMode.SAILING -> sailingDecelerationMps2
        }

    /** Target speed for a maneuver, or null if this maneuver type should not trigger a cue. */
    fun targetSpeedKmh(turnType: Int): Double? = when (TurnType.category(turnType)) {
        TurnCategory.TURN -> turnTargetKmh
        TurnCategory.SHARP_TURN -> sharpTurnTargetKmh
        TurnCategory.SLIGHT_TURN -> slightTurnTargetKmh
        TurnCategory.ROUNDABOUT -> roundaboutTargetKmh
        TurnCategory.NONE -> null
    }
}
