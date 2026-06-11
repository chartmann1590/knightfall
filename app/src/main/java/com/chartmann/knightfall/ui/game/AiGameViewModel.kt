package com.chartmann.knightfall.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.chess.ChessGame
import com.chartmann.knightfall.chess.GameOutcome
import com.chartmann.knightfall.chess.GameStatus
import com.chartmann.knightfall.chess.StockfishEngine
import com.chartmann.knightfall.chess.toUci
import com.chartmann.knightfall.coach.GemmaCoach
import com.chartmann.knightfall.ui.SoundManager
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CoachMessage(val text: String, val isStreaming: Boolean = false)

data class AiGameUiState(
    val position: Map<Square, Piece> = emptyMap(),
    val playerSide: Side = Side.WHITE,
    val sideToMove: Side = Side.WHITE,
    val selected: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val lastMove: Pair<Square, Square>? = null,
    val checkSquare: Square? = null,
    val engineThinking: Boolean = false,
    val status: GameStatus = GameStatus.ONGOING,
    val outcome: GameOutcome? = null,
    val playerResigned: Boolean = false,
    val capturedByPlayer: List<PieceType> = emptyList(),
    val capturedByAi: List<PieceType> = emptyList(),
    val materialDiff: Int = 0,
    val difficulty: StockfishEngine.Difficulty = StockfishEngine.Difficulty.KNIGHT,
    val pendingPromotion: Pair<Square, Square>? = null,
    val hintMove: Pair<Square, Square>? = null,
    val hintLoading: Boolean = false,
    val coachAvailable: Boolean = false,
    val coachInitializing: Boolean = false,
    val coachMessages: List<CoachMessage> = emptyList(),
    val moveCount: Int = 0,
)

class AiGameViewModel(
    private val container: AppContainer,
    difficulty: StockfishEngine.Difficulty,
    playerSide: Side,
) : ViewModel() {

    private val game = ChessGame()
    private val engine = container.stockfish
    private val coach: GemmaCoach = container.coach
    private val sounds: SoundManager = container.sounds

    private val _state = MutableStateFlow(
        AiGameUiState(
            position = currentPosition(),
            playerSide = playerSide,
            difficulty = difficulty,
        ),
    )
    val state: StateFlow<AiGameUiState> = _state.asStateFlow()

    private var coachJob: Job? = null
    private var lastEvalCp: Int? = null
    private var lastMateIn: Int? = null

    init {
        viewModelScope.launch {
            engine.setDifficulty(difficulty)
            if (game.sideToMove != playerSide) engineMove()
        }
        viewModelScope.launch {
            if (container.modelManager.isDownloaded && container.settings.current().coachEnabled) {
                _state.value = _state.value.copy(coachInitializing = true)
                val ok = coach.initialize()
                _state.value = _state.value.copy(
                    coachAvailable = ok,
                    coachInitializing = false,
                )
                if (ok) {
                    pushCoachText("I'm watching your game — good luck out there!")
                }
            }
        }
    }

    private fun currentPosition(): Map<Square, Piece> =
        Square.values()
            .filter { it != Square.NONE }
            .mapNotNull { sq ->
                val p = game.pieceAt(sq)
                if (p != Piece.NONE) sq to p else null
            }.toMap()

    fun onSquareTap(square: Square) {
        val s = _state.value
        if (s.engineThinking || s.status != GameStatus.ONGOING) return
        if (game.sideToMove != s.playerSide) return

        val piece = game.pieceAt(square)
        if (s.selected == null) {
            if (piece != Piece.NONE && piece.pieceSide == s.playerSide) select(square)
            return
        }
        if (square == s.selected) {
            clearSelection()
            return
        }
        if (piece != Piece.NONE && piece.pieceSide == s.playerSide) {
            select(square)
            return
        }
        attemptPlayerMove(s.selected, square)
    }

    fun onDragMove(from: Square, to: Square) {
        val s = _state.value
        if (s.engineThinking || s.status != GameStatus.ONGOING) return
        if (game.sideToMove != s.playerSide) return
        val piece = game.pieceAt(from)
        if (piece == Piece.NONE || piece.pieceSide != s.playerSide) return
        attemptPlayerMove(from, to)
    }

    private fun select(square: Square) {
        _state.value = _state.value.copy(
            selected = square,
            legalTargets = game.legalMovesFrom(square).map { it.to }.toSet(),
        )
    }

    private fun clearSelection() {
        _state.value = _state.value.copy(selected = null, legalTargets = emptySet())
    }

    private fun attemptPlayerMove(from: Square, to: Square) {
        if (game.isPromotionMove(from, to)) {
            _state.value = _state.value.copy(pendingPromotion = from to to)
            return
        }
        playPlayerMove(from, to, null)
    }

    fun onPromotionPicked(type: PieceType) {
        val pending = _state.value.pendingPromotion ?: return
        _state.value = _state.value.copy(pendingPromotion = null)
        playPlayerMove(pending.first, pending.second, type)
    }

    fun onPromotionDismissed() {
        _state.value = _state.value.copy(pendingPromotion = null)
        clearSelection()
    }

    private fun playPlayerMove(from: Square, to: Square, promotion: PieceType?) {
        val wasCapture = game.pieceAt(to) != Piece.NONE
        if (!game.play(from, to, promotion)) {
            clearSelection()
            return
        }
        afterMove(wasCapture)
        if (_state.value.status == GameStatus.ONGOING) {
            viewModelScope.launch { engineMove() }
        }
    }

    private suspend fun engineMove() {
        _state.value = _state.value.copy(engineThinking = true)
        try {
            val result = engine.search(game.fen(), _state.value.difficulty.moveTimeMs)
            lastEvalCp = result.scoreCp
            lastMateIn = result.mateIn
            // Small pause so instant replies still feel deliberate.
            delay(350)
            val to = ChessGame.squareOf(result.bestMoveUci.substring(2, 4))
            val wasCapture = to != null && game.pieceAt(to) != Piece.NONE
            if (game.playUci(result.bestMoveUci)) {
                afterMove(wasCapture)
                maybeCoachComment()
            }
        } catch (e: Exception) {
            // Engine failure: leave the position as-is; player can retry by tapping.
        } finally {
            _state.value = _state.value.copy(engineThinking = false)
        }
    }

    private fun afterMove(wasCapture: Boolean) {
        val status = game.status()
        val outcome = game.outcome()
        val last = game.lastMove()
        val playerSide = _state.value.playerSide

        _state.value = _state.value.copy(
            position = currentPosition(),
            sideToMove = game.sideToMove,
            selected = null,
            legalTargets = emptySet(),
            lastMove = last?.let { it.from to it.to },
            checkSquare = game.checkedKingSquare(),
            status = status,
            outcome = outcome,
            capturedByPlayer = game.capturedBy(playerSide),
            capturedByAi = game.capturedBy(playerSide.flip()),
            materialDiff = game.materialDiff(),
            hintMove = null,
            moveCount = game.moveCount,
        )

        when {
            outcome != null -> {
                val playerWon = (outcome == GameOutcome.WHITE_WIN && playerSide == Side.WHITE) ||
                    (outcome == GameOutcome.BLACK_WIN && playerSide == Side.BLACK)
                when {
                    outcome == GameOutcome.DRAW -> sounds.play(SoundManager.Sound.DRAW)
                    playerWon -> {
                        sounds.play(SoundManager.Sound.WIN)
                        recordAiWin()
                    }
                    else -> sounds.play(SoundManager.Sound.LOSE)
                }
            }
            game.isKingInCheck() -> sounds.play(SoundManager.Sound.CHECK)
            wasCapture -> sounds.play(SoundManager.Sound.CAPTURE)
            else -> sounds.play(SoundManager.Sound.MOVE)
        }
    }

    private fun recordAiWin() {
        val uid = container.auth.uid ?: return
        viewModelScope.launch {
            try {
                container.users.incrementAiWins(uid)
            } catch (_: Exception) {
            }
        }
    }

    fun resign() {
        if (_state.value.status != GameStatus.ONGOING) return
        sounds.play(SoundManager.Sound.LOSE)
        _state.value = _state.value.copy(
            playerResigned = true,
            outcome = if (_state.value.playerSide == Side.WHITE) GameOutcome.BLACK_WIN else GameOutcome.WHITE_WIN,
        )
    }

    fun requestHint() {
        val s = _state.value
        if (s.engineThinking || s.hintLoading || s.status != GameStatus.ONGOING) return
        if (game.sideToMove != s.playerSide) return
        viewModelScope.launch {
            _state.value = _state.value.copy(hintLoading = true)
            try {
                val result = engine.hint(game.fen())
                val from = ChessGame.squareOf(result.bestMoveUci.substring(0, 2))
                val to = ChessGame.squareOf(result.bestMoveUci.substring(2, 4))
                if (from != null && to != null) {
                    _state.value = _state.value.copy(hintMove = from to to)
                }
            } catch (_: Exception) {
            } finally {
                _state.value = _state.value.copy(hintLoading = false)
            }
        }
    }

    fun newGame() {
        game.reset()
        lastEvalCp = null
        lastMateIn = null
        _state.value = _state.value.copy(
            position = currentPosition(),
            sideToMove = Side.WHITE,
            selected = null,
            legalTargets = emptySet(),
            lastMove = null,
            checkSquare = null,
            status = GameStatus.ONGOING,
            outcome = null,
            playerResigned = false,
            capturedByPlayer = emptyList(),
            capturedByAi = emptyList(),
            materialDiff = 0,
            pendingPromotion = null,
            hintMove = null,
            moveCount = 0,
        )
        if (game.sideToMove != _state.value.playerSide) {
            viewModelScope.launch { engineMove() }
        }
    }

    private fun maybeCoachComment() {
        if (!_state.value.coachAvailable) return
        // Comment every few moves to keep it special (and the device cool).
        if (game.moveCount % 6 != 0 && game.outcome() == null) return
        streamCoach(
            coach.commentOnPosition(
                fen = game.fen(),
                recentMovesUci = game.movesUci,
                playerIsWhite = _state.value.playerSide == Side.WHITE,
                evalCp = lastEvalCp,
                mateIn = lastMateIn,
            ),
        )
    }

    fun askCoach(question: String) {
        if (!_state.value.coachAvailable || question.isBlank()) return
        pushCoachText("You: $question")
        streamCoach(
            coach.answerQuestion(
                fen = game.fen(),
                recentMovesUci = game.movesUci,
                playerIsWhite = _state.value.playerSide == Side.WHITE,
                question = question.trim(),
            ),
        )
    }

    private fun streamCoach(flow: kotlinx.coroutines.flow.Flow<String>) {
        coachJob?.cancel()
        coachJob = viewModelScope.launch {
            var acc = ""
            _state.value = _state.value.copy(
                coachMessages = _state.value.coachMessages + CoachMessage("", isStreaming = true),
            )
            flow.collect { chunk ->
                acc += chunk
                val msgs = _state.value.coachMessages.toMutableList()
                if (msgs.isNotEmpty()) msgs[msgs.lastIndex] = CoachMessage(acc, isStreaming = true)
                _state.value = _state.value.copy(coachMessages = msgs)
            }
            val msgs = _state.value.coachMessages.toMutableList()
            if (msgs.isNotEmpty()) msgs[msgs.lastIndex] = CoachMessage(acc, isStreaming = false)
            _state.value = _state.value.copy(coachMessages = msgs)
        }
    }

    private fun pushCoachText(text: String) {
        _state.value = _state.value.copy(
            coachMessages = _state.value.coachMessages + CoachMessage(text),
        )
    }

    override fun onCleared() {
        engine.stopSearch()
    }

    companion object {
        fun factory(
            container: AppContainer,
            difficulty: StockfishEngine.Difficulty,
            playerSide: Side,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AiGameViewModel(container, difficulty, playerSide) as T
        }
    }
}

private fun Side.flip(): Side = if (this == Side.WHITE) Side.BLACK else Side.WHITE
