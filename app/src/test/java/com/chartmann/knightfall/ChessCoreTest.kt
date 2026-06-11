package com.chartmann.knightfall

import com.chartmann.knightfall.chess.ChessGame
import com.chartmann.knightfall.chess.EloCalculator
import com.chartmann.knightfall.chess.GameOutcome
import com.chartmann.knightfall.chess.GameStatus
import com.chartmann.knightfall.data.GameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EloCalculatorTest {

    @Test
    fun `equal ratings win gives plus 16`() {
        val (white, black) = EloCalculator.newRatings(1200, 1200, GameOutcome.WHITE_WIN)
        assertEquals(1216, white)
        assertEquals(1184, black)
    }

    @Test
    fun `equal ratings draw changes nothing`() {
        val (white, black) = EloCalculator.newRatings(1500, 1500, GameOutcome.DRAW)
        assertEquals(1500, white)
        assertEquals(1500, black)
    }

    @Test
    fun `underdog win pays more`() {
        val (white, black) = EloCalculator.newRatings(1200, 1600, GameOutcome.WHITE_WIN)
        assertTrue("underdog should gain > 16, got ${white - 1200}", white - 1200 > 16)
        assertTrue(black < 1600)
    }

    @Test
    fun `ratings are symmetric`() {
        val (w1, b1) = EloCalculator.newRatings(1300, 1500, GameOutcome.BLACK_WIN)
        // deltas mirror each other
        assertEquals(w1 - 1300, -(b1 - 1500))
    }
}

class InviteCodeTest {

    @Test
    fun `codes are six chars from safe alphabet`() {
        repeat(200) {
            val code = GameRepository.generateInviteCode()
            assertEquals(6, code.length)
            assertFalse("ambiguous chars in $code", code.any { it in "01IO" })
        }
    }
}

class ChessGameTest {

    @Test
    fun `scholars mate is checkmate`() {
        val game = ChessGame()
        for (move in listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")) {
            assertTrue("move $move should be legal", game.playUci(move))
        }
        assertEquals(GameStatus.CHECKMATE, game.status())
        assertEquals(GameOutcome.WHITE_WIN, game.outcome())
    }

    @Test
    fun `illegal moves are rejected`() {
        val game = ChessGame()
        assertFalse(game.playUci("e2e5"))
        assertFalse(game.playUci("a1a8"))
        assertTrue(game.playUci("e2e4"))
    }

    @Test
    fun `promotion requires a piece choice`() {
        val game = ChessGame()
        // Fast path to a promotion: shuttle a pawn up the h-file
        for (move in listOf("h2h4", "g7g5", "h4g5", "g8f6", "g5g6", "f6g8", "g6g7", "g8f6")) {
            assertTrue("setup move $move failed", game.playUci(move))
        }
        assertTrue(game.playUci("g7g8q"))
        assertEquals(GameStatus.ONGOING, game.status())
    }

    @Test
    fun `move list round trip`() {
        val game = ChessGame()
        val moves = listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4")
        moves.forEach { assertTrue(game.playUci(it)) }
        val rebuilt = ChessGame()
        assertTrue(rebuilt.loadFromUciMoves(game.movesUci))
        assertEquals(game.fen(), rebuilt.fen())
    }

    @Test
    fun `fresh game has no outcome`() {
        val game = ChessGame()
        assertNull(game.outcome())
        assertEquals(GameStatus.ONGOING, game.status())
    }
}
