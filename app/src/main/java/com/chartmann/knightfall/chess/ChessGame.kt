package com.chartmann.knightfall.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveGenerator

enum class GameStatus {
    ONGOING,
    CHECKMATE,
    STALEMATE,
    DRAW_FIFTY_MOVES,
    DRAW_REPETITION,
    DRAW_INSUFFICIENT_MATERIAL,
}

enum class GameOutcome { WHITE_WIN, BLACK_WIN, DRAW }

/**
 * Mutable chess game state shared by local, AI and online play.
 * Wraps chesslib's [Board] with UCI-friendly helpers.
 */
class ChessGame {

    private val board = Board()
    private val played = mutableListOf<Move>()

    val movesUci: List<String> get() = played.map { it.toUci() }
    val sideToMove: Side get() = board.sideToMove
    val moveCount: Int get() = played.size

    fun fen(): String = board.fen

    fun pieceAt(square: Square): Piece = board.getPiece(square)

    fun lastMove(): Move? = played.lastOrNull()

    fun legalMoves(): List<Move> = try {
        MoveGenerator.generateLegalMoves(board)
    } catch (e: Exception) {
        emptyList()
    }

    fun legalMovesFrom(square: Square): List<Move> =
        legalMoves().filter { it.from == square }

    fun isPromotionMove(from: Square, to: Square): Boolean =
        legalMoves().any { it.from == from && it.to == to && it.promotion != Piece.NONE }

    /** Plays a move expressed as from/to plus an optional promotion type. */
    fun play(from: Square, to: Square, promotion: PieceType? = null): Boolean {
        val move = legalMoves().firstOrNull {
            it.from == from && it.to == to &&
                (promotion == null && it.promotion == Piece.NONE ||
                    promotion != null && it.promotion.pieceType == promotion)
        } ?: return false
        return doMove(move)
    }

    /** Plays a UCI move like "e2e4" or "e7e8q". Returns false if illegal. */
    fun playUci(uci: String): Boolean {
        if (uci.length < 4) return false
        val from = squareOf(uci.substring(0, 2)) ?: return false
        val to = squareOf(uci.substring(2, 4)) ?: return false
        val promo = if (uci.length > 4) pieceTypeOf(uci[4]) else null
        return play(from, to, promo)
    }

    private fun doMove(move: Move): Boolean {
        val ok = try {
            board.doMove(move, true)
        } catch (e: Exception) {
            false
        }
        if (ok) played += move
        return ok
    }

    fun status(): GameStatus = when {
        board.isMated -> GameStatus.CHECKMATE
        board.isStaleMate -> GameStatus.STALEMATE
        board.isInsufficientMaterial -> GameStatus.DRAW_INSUFFICIENT_MATERIAL
        board.isRepetition -> GameStatus.DRAW_REPETITION
        board.halfMoveCounter >= 100 -> GameStatus.DRAW_FIFTY_MOVES
        else -> GameStatus.ONGOING
    }

    fun outcome(): GameOutcome? = when (status()) {
        GameStatus.ONGOING -> null
        GameStatus.CHECKMATE ->
            if (board.sideToMove == Side.WHITE) GameOutcome.BLACK_WIN else GameOutcome.WHITE_WIN
        else -> GameOutcome.DRAW
    }

    fun isKingInCheck(): Boolean = board.isKingAttacked

    fun checkedKingSquare(): Square? =
        if (board.isKingAttacked) board.getKingSquare(board.sideToMove) else null

    /** Pieces each side has captured, computed against the standard set. */
    fun capturedBy(side: Side): List<PieceType> {
        val remaining = mutableMapOf<PieceType, Int>()
        for (sq in Square.values()) {
            if (sq == Square.NONE) continue
            val p = board.getPiece(sq)
            if (p != Piece.NONE && p.pieceSide != side) {
                remaining.merge(p.pieceType, 1, Int::plus)
            }
        }
        val full = mapOf(
            PieceType.PAWN to 8, PieceType.KNIGHT to 2, PieceType.BISHOP to 2,
            PieceType.ROOK to 2, PieceType.QUEEN to 1, PieceType.KING to 1,
        )
        val captured = mutableListOf<PieceType>()
        for ((type, count) in full) {
            repeat((count - (remaining[type] ?: 0)).coerceAtLeast(0)) { captured += type }
        }
        return captured.sortedBy { MATERIAL_ORDER.indexOf(it) }
    }

    fun materialDiff(): Int {
        var diff = 0
        for (sq in Square.values()) {
            if (sq == Square.NONE) continue
            val p = board.getPiece(sq)
            if (p == Piece.NONE) continue
            val v = PIECE_VALUES[p.pieceType] ?: 0
            diff += if (p.pieceSide == Side.WHITE) v else -v
        }
        return diff
    }

    fun reset() {
        board.loadFromFen(STANDARD_FEN)
        played.clear()
    }

    fun loadFromFen(fen: String) {
        board.loadFromFen(fen)
        played.clear()
    }

    /** Rebuilds the game from a UCI move list (used for online sync). */
    fun loadFromUciMoves(moves: List<String>): Boolean {
        reset()
        for (m in moves) {
            if (!playUci(m)) return false
        }
        return true
    }

    companion object {
        const val STANDARD_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        private val MATERIAL_ORDER = listOf(
            PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP,
            PieceType.ROOK, PieceType.QUEEN, PieceType.KING,
        )

        val PIECE_VALUES = mapOf(
            PieceType.PAWN to 1, PieceType.KNIGHT to 3, PieceType.BISHOP to 3,
            PieceType.ROOK to 5, PieceType.QUEEN to 9, PieceType.KING to 0,
        )

        fun squareOf(name: String): Square? = try {
            Square.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }

        fun pieceTypeOf(char: Char): PieceType? = when (char.lowercaseChar()) {
            'q' -> PieceType.QUEEN
            'r' -> PieceType.ROOK
            'b' -> PieceType.BISHOP
            'n' -> PieceType.KNIGHT
            else -> null
        }
    }
}

fun Move.toUci(): String {
    val promo = if (promotion != Piece.NONE) {
        when (promotion.pieceType) {
            PieceType.QUEEN -> "q"
            PieceType.ROOK -> "r"
            PieceType.BISHOP -> "b"
            PieceType.KNIGHT -> "n"
            else -> ""
        }
    } else ""
    return from.value().lowercase() + to.value().lowercase() + promo
}
