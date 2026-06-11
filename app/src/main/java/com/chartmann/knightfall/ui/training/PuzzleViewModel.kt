package com.chartmann.knightfall.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.badges.BadgeChecker
import com.chartmann.knightfall.chess.ChessGame
import com.chartmann.knightfall.data.model.Puzzle
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PuzzleUiState(
    val puzzle: Puzzle? = null,
    val position: Map<Square, Piece> = emptyMap(),
    val selectedSquare: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val lastMove: Pair<Square, Square>? = null,
    val playerSide: Side = Side.WHITE,
    val moveIndex: Int = 0,
    val solved: Boolean = false,
    val failed: Boolean = false,
    val newBadges: List<String> = emptyList(),
)

class PuzzleViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PuzzleUiState())
    val state: StateFlow<PuzzleUiState> = _state.asStateFlow()

    private val game = ChessGame()

    fun loadPuzzle(puzzle: Puzzle) {
        game.loadFromFen(puzzle.fen)
        // moves[0] is the opponent's "setup" move; replay it to reach the tactic position.
        if (puzzle.moves.isNotEmpty()) {
            game.playUci(puzzle.moves[0])
        }
        _state.value = PuzzleUiState(
            puzzle = puzzle,
            position = currentPosition(),
            playerSide = game.sideToMove,
            lastMove = game.lastMove()?.let { it.from to it.to },
            moveIndex = 1,
        )
    }

    fun onSquareTap(square: Square) {
        val s = _state.value
        if (s.solved || s.failed || s.puzzle == null) return

        val selected = s.selectedSquare
        if (selected == null) {
            val piece = game.pieceAt(square)
            if (piece == Piece.NONE || piece.pieceSide != s.playerSide) return
            _state.value = s.copy(
                selectedSquare = square,
                legalTargets = game.legalMovesFrom(square).map { it.to }.toSet(),
            )
        } else {
            if (square == selected) {
                _state.value = s.copy(selectedSquare = null, legalTargets = emptySet())
                return
            }
            // Re-select a different friendly piece
            val piece = game.pieceAt(square)
            if (piece != Piece.NONE && piece.pieceSide == s.playerSide) {
                _state.value = s.copy(
                    selectedSquare = square,
                    legalTargets = game.legalMovesFrom(square).map { it.to }.toSet(),
                )
                return
            }

            val uci = selected.value().lowercase() + square.value().lowercase()
            val expected = s.puzzle.moves.getOrNull(s.moveIndex) ?: ""

            if (expected.startsWith(uci)) {
                game.playUci(expected)
                val nextIndex = s.moveIndex + 1
                val lastMovePair = game.lastMove()?.let { it.from to it.to }

                if (nextIndex >= s.puzzle.moves.size) {
                    _state.value = s.copy(
                        position = currentPosition(),
                        selectedSquare = null, legalTargets = emptySet(),
                        lastMove = lastMovePair,
                        moveIndex = nextIndex, solved = true,
                    )
                    onPuzzleSolved()
                } else {
                    // Play the opponent's response automatically
                    val opponentMove = s.puzzle.moves[nextIndex]
                    game.playUci(opponentMove)
                    val opponentLastMove = game.lastMove()?.let { it.from to it.to }
                    val afterOpponent = nextIndex + 1
                    if (afterOpponent >= s.puzzle.moves.size) {
                        _state.value = s.copy(
                            position = currentPosition(),
                            selectedSquare = null, legalTargets = emptySet(),
                            lastMove = opponentLastMove,
                            moveIndex = afterOpponent, solved = true,
                        )
                        onPuzzleSolved()
                    } else {
                        _state.value = s.copy(
                            position = currentPosition(),
                            selectedSquare = null, legalTargets = emptySet(),
                            lastMove = opponentLastMove,
                            moveIndex = afterOpponent,
                        )
                    }
                }
            } else {
                _state.value = s.copy(selectedSquare = null, legalTargets = emptySet(), failed = true)
                recordFailed()
            }
        }
    }

    fun onDragMove(from: Square, to: Square) {
        onSquareTap(from)
        onSquareTap(to)
    }

    fun retry() {
        val puzzle = _state.value.puzzle ?: return
        loadPuzzle(puzzle)
    }

    fun clearNewBadges() {
        _state.value = _state.value.copy(newBadges = emptyList())
    }

    private fun currentPosition(): Map<Square, Piece> =
        Square.values()
            .filter { it != Square.NONE }
            .mapNotNull { sq ->
                val p = game.pieceAt(sq)
                if (p != Piece.NONE) sq to p else null
            }
            .toMap()

    private fun onPuzzleSolved() {
        val uid = container.auth.uid ?: return
        viewModelScope.launch {
            try {
                container.users.recordPuzzleSolved(uid)
                val profile = container.users.getProfile(uid) ?: return@launch
                val newBadges = BadgeChecker.checkPuzzleBadges(profile)
                if (newBadges.isNotEmpty()) {
                    _state.value = _state.value.copy(newBadges = newBadges)
                }
            } catch (_: Exception) {}
        }
    }

    private fun recordFailed() {
        val uid = container.auth.uid ?: return
        viewModelScope.launch {
            try { container.users.recordPuzzleFailed(uid) } catch (_: Exception) {}
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PuzzleViewModel(container) as T
        }
    }
}
