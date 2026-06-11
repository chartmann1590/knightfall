package com.chartmann.knightfall.chess

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Standard Elo with K=32. Both clients compute the same deltas from the
 * rating snapshots stored on the game document, so the result is
 * deterministic regardless of who applies it first.
 */
object EloCalculator {

    const val K = 32
    const val STARTING_ELO = 1200

    fun expectedScore(rating: Int, opponentRating: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponentRating - rating) / 400.0))

    /** Returns (newWhiteElo, newBlackElo). */
    fun newRatings(whiteElo: Int, blackElo: Int, outcome: GameOutcome): Pair<Int, Int> {
        val whiteScore = when (outcome) {
            GameOutcome.WHITE_WIN -> 1.0
            GameOutcome.BLACK_WIN -> 0.0
            GameOutcome.DRAW -> 0.5
        }
        val newWhite = whiteElo + (K * (whiteScore - expectedScore(whiteElo, blackElo))).roundToInt()
        val newBlack = blackElo + (K * ((1.0 - whiteScore) - expectedScore(blackElo, whiteElo))).roundToInt()
        return newWhite.coerceIn(0, 4000) to newBlack.coerceIn(0, 4000)
    }
}
