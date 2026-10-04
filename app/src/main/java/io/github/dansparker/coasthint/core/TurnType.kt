package io.github.dansparker.coasthint.core

/** Turn type values as reported by OsmAnd (mirrors `net.osmand.router.TurnType`). */
object TurnType {
    const val C = 1
    const val TL = 2
    const val TSLL = 3
    const val TSHL = 4
    const val TR = 5
    const val TSLR = 6
    const val TSHR = 7
    const val KL = 8
    const val KR = 9
    const val TU = 10
    const val TRU = 11
    const val OFFR = 12
    const val RNDB = 13
    const val RNLB = 14

    fun category(turnType: Int): TurnCategory = when (turnType) {
        TL, TR -> TurnCategory.TURN
        TSHL, TSHR, TU, TRU -> TurnCategory.SHARP_TURN
        TSLL, TSLR, KL, KR -> TurnCategory.SLIGHT_TURN
        RNDB, RNLB -> TurnCategory.ROUNDABOUT
        else -> TurnCategory.NONE
    }
}

enum class TurnCategory { TURN, SHARP_TURN, SLIGHT_TURN, ROUNDABOUT, NONE }
