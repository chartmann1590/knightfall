package com.chartmann.knightfall.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.chess.ChessGame
import com.chartmann.knightfall.chess.GameStatus
import com.chartmann.knightfall.data.model.EndReasons
import com.chartmann.knightfall.data.model.GameResultValues
import com.chartmann.knightfall.data.model.GameStatusValues
import com.chartmann.knightfall.data.model.OnlineGame
import com.chartmann.knightfall.ui.SoundManager
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnlineGameUiState(
    val game: OnlineGame? = null,
    val position: Map<Square, Piece> = emptyMap(),
    val mySide: Side = Side.WHITE,
    val sideToMove: Side = Side.WHITE,
    val selected: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val lastMove: Pair<Square, Square>? = null,
    val checkSquare: Square? = null,
    val capturedByMe: List<PieceType> = emptyList(),
    val capturedByOpponent: List<PieceType> = emptyList(),
    val pendingPromotion: Pair<Square, Square>? = null,
    val waitingForOpponent: Boolean = true,
    val finished: Boolean = false,
    val resultTitle: String = "",
    val resultSubtitle: String = "",
    val eloDelta: Int? = null,
    val drawOfferedByOpponent: Boolean = false,
    val opponentIdle: Boolean = false,
    val error: String? = null,
)

class OnlineGameViewModel(
    private val container: AppContainer,
    private val gameId: String,
) : ViewModel() {

    private val chess = ChessGame()
    private val repo = container.games
    private val sounds: SoundManager = container.sounds
    private val myUid: String get() = container.auth.uid ?: ""

    private val _state = MutableStateFlow(OnlineGameUiState())
    val state: StateFlow<OnlineGameUiState> = _state.asStateFlow()

    private var lastSeenMoveCount = 0
    private var resultHandled = false

    init {
        viewModelScope.launch {
            repo.gameFlow(gameId).collect { game ->
                if (game != null) onGameUpdate(game)
            }
        }
        viewModelScope.launch {
            // Watchdog for abandoned games.
            while (true) {
                delay(15_000)
                checkOpponentIdle()
            }
        }
    }

    private fun onGameUpdate(game: OnlineGame) {
        val mySeat = game.seatOf(myUid)
        val mySide = if (mySeat == "black") Side.BLACK else Side.WHITE

        if (!chess.loadFromUciMoves(game.moves)) {
            _state.value = _state.value.copy(error = "Game state is corrupted")
            return
        }

        val newMoves = game.moves.size - lastSeenMoveCount
        if (newMoves > 0 && lastSeenMoveCount > 0 && chess.sideToMove == mySide) {
            // Opponent just moved.
            when {
                chess.isKingInCheck() -> sounds.play(SoundManager.Sound.CHECK)
                else -> sounds.play(SoundManager.Sound.MOVE)
            }
        }
        lastSeenMoveCount = game.moves.size

        val last = chess.lastMove()
        _state.value = _state.value.copy(
            game = game,
            position = currentPosition(),
            mySide = mySide,
            sideToMove = chess.sideToMove,
            lastMove = last?.let { it.from to it.to },
            checkSquare = chess.checkedKingSquare(),
            capturedByMe = chess.capturedBy(mySide),
            capturedByOpponent = chess.capturedBy(mySide.flip2()),
            waitingForOpponent = game.status == GameStatusValues.WAITING,
            drawOfferedByOpponent = game.drawOfferBy != null && game.drawOfferBy != myUid,
            selected = null,
            legalTargets = emptySet(),
        )

        when (game.status) {
            GameStatusValues.ACTIVE -> {
                // The mover's client writes the game end when the position is terminal.
                val status = chess.status()
                if (status != GameStatus.ONGOING && chess.sideToMove == mySide.flip2()) {
                    // We made the final move; finish the game.
                    finishFromPosition(status)
                } else if (status != GameStatus.ONGOING && chess.sideToMove == mySide) {
                    // Opponent delivered the final blow; they may finish it,
                    // but make sure it happens even if they disconnected.
                    viewModelScope.launch {
                        delay(4_000)
                        if (_state.value.game?.status == GameStatusValues.ACTIVE) {
                            finishFromPosition(status)
                        }
                    }
                }
            }
            GameStatusValues.FINISHED -> handleFinished(game)
        }
    }

    private fun finishFromPosition(status: GameStatus) {
        val result = when (status) {
            GameStatus.CHECKMATE ->
                if (chess.sideToMove == Side.WHITE) GameResultValues.BLACK_WIN else GameResultValues.WHITE_WIN
            else -> GameResultValues.DRAW
        }
        val reason = when (status) {
            GameStatus.CHECKMATE -> EndReasons.CHECKMATE
            GameStatus.STALEMATE -> EndReasons.STALEMATE
            GameStatus.DRAW_REPETITION -> EndReasons.REPETITION
            GameStatus.DRAW_FIFTY_MOVES -> EndReasons.FIFTY_MOVES
            else -> EndReasons.INSUFFICIENT_MATERIAL
        }
        viewModelScope.launch {
            try {
                repo.finishGame(gameId, result, reason)
            } catch (_: Exception) {
            }
        }
    }

    private fun handleFinished(game: OnlineGame) {
        if (resultHandled) return
        resultHandled = true

        val mySeat = game.seatOf(myUid) ?: return
        val iWon = game.result == mySeat ||
            (game.result == GameResultValues.WHITE_WIN && mySeat == "white") ||
            (game.result == GameResultValues.BLACK_WIN && mySeat == "black")
        val isDraw = game.result == GameResultValues.DRAW

        when {
            isDraw -> sounds.play(SoundManager.Sound.DRAW)
            iWon -> sounds.play(SoundManager.Sound.WIN)
            else -> sounds.play(SoundManager.Sound.LOSE)
        }

        val reasonText = when (game.endReason) {
            EndReasons.CHECKMATE -> "by checkmate"
            EndReasons.RESIGNATION -> "by resignation"
            EndReasons.DRAW_AGREED -> "draw agreed"
            EndReasons.STALEMATE -> "stalemate"
            EndReasons.REPETITION -> "by repetition"
            EndReasons.FIFTY_MOVES -> "fifty-move rule"
            EndReasons.INSUFFICIENT_MATERIAL -> "insufficient material"
            EndReasons.ABANDONED -> "opponent left"
            else -> ""
        }

        _state.value = _state.value.copy(
            finished = true,
            resultTitle = when {
                isDraw -> "Draw"
                iWon -> "You win! 🏆"
                else -> "You lost"
            },
            resultSubtitle = reasonText,
        )

        // Apply Elo + stats for my side, exactly once.
        viewModelScope.launch {
            try {
                val newElo = repo.markEloApplied(gameId, mySeat) ?: return@launch
                val startElo = if (mySeat == "white") game.whiteEloStart else game.blackEloStart
                container.users.applyGameResult(
                    uid = myUid,
                    newElo = newElo,
                    won = iWon,
                    drew = isDraw,
                )
                _state.value = _state.value.copy(eloDelta = newElo - startElo)
            } catch (_: Exception) {
            }
        }
    }

    private fun currentPosition(): Map<Square, Piece> =
        Square.values()
            .filter { it != Square.NONE }
            .mapNotNull { sq ->
                val p = chess.pieceAt(sq)
                if (p != Piece.NONE) sq to p else null
            }.toMap()

    fun onSquareTap(square: Square) {
        val s = _state.value
        if (s.finished || s.waitingForOpponent) return
        if (chess.sideToMove != s.mySide) return

        val piece = chess.pieceAt(square)
        if (s.selected == null) {
            if (piece != Piece.NONE && piece.pieceSide == s.mySide) select(square)
            return
        }
        if (square == s.selected) {
            _state.value = s.copy(selected = null, legalTargets = emptySet())
            return
        }
        if (piece != Piece.NONE && piece.pieceSide == s.mySide) {
            select(square)
            return
        }
        attemptMove(s.selected, square)
    }

    fun onDragMove(from: Square, to: Square) {
        val s = _state.value
        if (s.finished || s.waitingForOpponent) return
        if (chess.sideToMove != s.mySide) return
        val piece = chess.pieceAt(from)
        if (piece == Piece.NONE || piece.pieceSide != s.mySide) return
        attemptMove(from, to)
    }

    private fun select(square: Square) {
        _state.value = _state.value.copy(
            selected = square,
            legalTargets = chess.legalMovesFrom(square).map { it.to }.toSet(),
        )
    }

    private fun attemptMove(from: Square, to: Square) {
        if (chess.isPromotionMove(from, to)) {
            _state.value = _state.value.copy(pendingPromotion = from to to)
            return
        }
        sendMove(from, to, null)
    }

    fun onPromotionPicked(type: PieceType) {
        val pending = _state.value.pendingPromotion ?: return
        _state.value = _state.value.copy(pendingPromotion = null)
        sendMove(pending.first, pending.second, type)
    }

    fun onPromotionDismissed() {
        _state.value = _state.value.copy(pendingPromotion = null, selected = null, legalTargets = emptySet())
    }

    private fun sendMove(from: Square, to: Square, promotion: PieceType?) {
        val wasCapture = chess.pieceAt(to) != Piece.NONE
        if (!chess.play(from, to, promotion)) {
            _state.value = _state.value.copy(selected = null, legalTargets = emptySet())
            return
        }
        val uci = chess.movesUci.last()
        lastSeenMoveCount = chess.moveCount

        when {
            chess.isKingInCheck() -> sounds.play(SoundManager.Sound.CHECK)
            wasCapture -> sounds.play(SoundManager.Sound.CAPTURE)
            else -> sounds.play(SoundManager.Sound.MOVE)
        }

        val last = chess.lastMove()
        _state.value = _state.value.copy(
            position = currentPosition(),
            sideToMove = chess.sideToMove,
            lastMove = last?.let { it.from to it.to },
            checkSquare = chess.checkedKingSquare(),
            selected = null,
            legalTargets = emptySet(),
            capturedByMe = chess.capturedBy(_state.value.mySide),
            capturedByOpponent = chess.capturedBy(_state.value.mySide.flip2()),
        )

        viewModelScope.launch {
            try {
                repo.playMove(gameId, uci)
                val status = chess.status()
                if (status != GameStatus.ONGOING) finishFromPosition(status)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Move failed to send — check connection")
            }
        }
    }

    fun resign() {
        val mySeat = _state.value.game?.seatOf(myUid) ?: return
        viewModelScope.launch {
            try {
                repo.resign(gameId, mySeat)
            } catch (_: Exception) {
            }
        }
    }

    fun offerDraw() {
        viewModelScope.launch {
            try {
                repo.offerDraw(gameId, myUid)
            } catch (_: Exception) {
            }
        }
    }

    fun respondToDraw(accept: Boolean) {
        viewModelScope.launch {
            try {
                if (accept) repo.acceptDraw(gameId) else repo.declineDraw(gameId)
            } catch (_: Exception) {
            }
        }
    }

    fun cancelWaiting() {
        viewModelScope.launch {
            try {
                repo.cancelWaitingGame(gameId)
            } catch (_: Exception) {
            }
        }
    }

    private fun checkOpponentIdle() {
        val s = _state.value
        val game = s.game ?: return
        if (s.finished || s.waitingForOpponent) return
        if (chess.sideToMove == s.mySide) return // it's my turn; opponent isn't idle
        val lastMoveAt = game.lastMoveAt ?: return
        val idleMs = System.currentTimeMillis() - lastMoveAt.toDate().time
        _state.value = s.copy(opponentIdle = idleMs > ABANDON_THRESHOLD_MS)
    }

    fun claimAbandonedWin() {
        val mySeat = _state.value.game?.seatOf(myUid) ?: return
        viewModelScope.launch {
            try {
                repo.claimAbandonment(gameId, mySeat)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val ABANDON_THRESHOLD_MS = 2 * 60 * 1000L

        fun factory(container: AppContainer, gameId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OnlineGameViewModel(container, gameId) as T
            }
    }
}

private fun Side.flip2(): Side = if (this == Side.WHITE) Side.BLACK else Side.WHITE
