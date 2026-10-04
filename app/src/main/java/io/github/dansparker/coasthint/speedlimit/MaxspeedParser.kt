package io.github.dansparker.coasthint.speedlimit

import kotlin.math.roundToInt

sealed interface Maxspeed {
    data class Limit(val kmh: Int) : Maxspeed

    /** Explicitly no limit (`maxspeed=none`, German Autobahn). */
    data object Unlimited : Maxspeed
}

/** Parses OpenStreetMap `maxspeed` values. Returns null for values without a usable limit. */
object MaxspeedParser {
    private const val WALK_KMH = 7
    private const val KMH_PER_MPH = 1.609344

    private val implicit = mapOf(
        "AT:urban" to Maxspeed.Limit(50),
        "AT:rural" to Maxspeed.Limit(100),
        "AT:trunk" to Maxspeed.Limit(100),
        "AT:motorway" to Maxspeed.Limit(130),
        "AT:living_street" to Maxspeed.Limit(WALK_KMH),
        "DE:urban" to Maxspeed.Limit(50),
        "DE:rural" to Maxspeed.Limit(100),
        "DE:motorway" to Maxspeed.Unlimited,
        "DE:living_street" to Maxspeed.Limit(WALK_KMH),
        "walk" to Maxspeed.Limit(WALK_KMH),
        "none" to Maxspeed.Unlimited,
    )

    private val number = Regex("""(\d+(?:\.\d+)?)\s*(km/h|kmh|kph|mph)?""")
    private val zone = Regex("""[A-Z]{2}:zone:?(\d+)""")

    fun parse(raw: String?): Maxspeed? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (';' in value) {
            // Multiple values: use the lowest limit, as the legally binding one is unknown.
            return value.split(';').mapNotNull { parse(it) }
                .minByOrNull { if (it is Maxspeed.Limit) it.kmh else Int.MAX_VALUE }
        }
        implicit[value]?.let { return it }
        zone.matchEntire(value)?.let { return Maxspeed.Limit(it.groupValues[1].toInt()) }
        number.matchEntire(value)?.let { match ->
            val amount = match.groupValues[1].toDouble()
            val kmh = if (match.groupValues[2] == "mph") amount * KMH_PER_MPH else amount
            return Maxspeed.Limit(kmh.roundToInt())
        }
        return null
    }
}
