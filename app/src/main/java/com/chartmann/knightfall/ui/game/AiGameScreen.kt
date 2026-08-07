package com.chartmann.knightfall.ui.game

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.chess.GameOutcome
import com.chartmann.knightfall.chess.GameStatus
import com.chartmann.knightfall.chess.StockfishEngine
import com.chartmann.knightfall.review.ReviewPrompter
import com.chartmann.knightfall.ui.board.BoardTheme
import com.chartmann.knightfall.ui.board.ChessBoard
import com.github.bhlangonijr.chesslib.Side

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiGameScreen(
    container: AppContainer,
    difficulty: StockfishEngine.Difficulty,
    playerSide: Side,
    boardTheme: BoardTheme,
    onExit: () -> Unit,
) {
    val vm: AiGameViewModel = viewModel(
        factory = AiGameViewModel.factory(container, difficulty, playerSide),
    )
    val state by vm.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var coachInput by remember { mutableStateOf("") }

    LaunchedEffect(state.lastMove) {
        if (state.lastMove != null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // A game against the AI just ended (win, loss, or draw) — a real completed session.
    LaunchedEffect(state.outcome) {
        if (state.outcome != null) {
            (context as? Activity)?.let { ReviewPrompter.maybeRequestReview(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("vs ${difficulty.label}", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "~${difficulty.approxElo} Elo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.requestHint() },
                        enabled = !state.hintLoading,
                    ) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = "Hint")
                    }
                    IconButton(onClick = { vm.newGame() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "New game")
                    }
                    IconButton(onClick = { vm.resign() }) {
                        Icon(Icons.Filled.Flag, contentDescription = "Resign")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            PlayerBar(
                name = difficulty.label,
                subtitle = if (state.engineThinking) "thinking…" else "Stockfish 18",
                isTurn = state.sideToMove != state.playerSide && state.status == GameStatus.ONGOING,
                capturedPieces = state.capturedByAi,
                capturedSide = state.playerSide,
                materialAdvantage = 0,
            )
            Spacer(Modifier.height(4.dp))
            ClockBar(
                whiteTimeMs = state.whiteTimeMs,
                blackTimeMs = state.blackTimeMs,
                sideToMove = state.sideToMove,
                playerSide = state.playerSide,
                gameOngoing = state.status == GameStatus.ONGOING && state.outcome == null,
            )
            Spacer(Modifier.height(4.dp))

            ChessBoard(
                position = state.position,
                flipped = state.playerSide == Side.BLACK,
                theme = boardTheme,
                selected = state.selected,
                legalTargets = state.legalTargets,
                lastMove = state.hintMove ?: state.lastMove,
                checkSquare = state.checkSquare,
                interactive = state.status == GameStatus.ONGOING && !state.engineThinking,
                onSquareTap = vm::onSquareTap,
                onDragMove = vm::onDragMove,
            )

            Spacer(Modifier.height(8.dp))
            PlayerBar(
                name = "You",
                subtitle = null,
                isTurn = state.sideToMove == state.playerSide && state.status == GameStatus.ONGOING,
                capturedPieces = state.capturedByPlayer,
                capturedSide = if (state.playerSide == Side.WHITE) Side.BLACK else Side.WHITE,
                materialAdvantage = 0,
            )

            AnimatedVisibility(visible = state.outcome != null) {
                val outcome = state.outcome
                if (outcome != null) {
                    val playerWon =
                        (outcome == GameOutcome.WHITE_WIN && state.playerSide == Side.WHITE) ||
                            (outcome == GameOutcome.BLACK_WIN && state.playerSide == Side.BLACK)
                    GameOverCard(
                        title = when {
                            outcome == GameOutcome.DRAW -> "Draw"
                            state.timedOut && playerWon -> "You win! 🏆"
                            state.timedOut -> "Time's up"
                            playerWon -> "You win! 🏆"
                            state.playerResigned -> "You resigned"
                            else -> "Checkmate"
                        },
                        subtitle = when {
                            outcome == GameOutcome.DRAW -> statusText(state.status)
                            state.timedOut && playerWon -> "Opponent ran out of time."
                            state.timedOut -> "You ran out of time."
                            playerWon -> "Beautifully played."
                            else -> "The ${difficulty.label} got you this time."
                        },
                        eloDelta = state.eloDelta,
                        onRematch = { vm.newGame() },
                        onExit = onExit,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            if (state.coachAvailable || state.coachInitializing) {
                Spacer(Modifier.height(10.dp))
                CoachPanel(
                    initializing = state.coachInitializing,
                    messages = state.coachMessages,
                    input = coachInput,
                    onInputChange = { coachInput = it },
                    onSend = {
                        vm.askCoach(coachInput)
                        coachInput = ""
                    },
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }

    state.pendingPromotion?.let {
        PromotionPicker(
            side = state.playerSide,
            onPick = vm::onPromotionPicked,
            onDismiss = vm::onPromotionDismissed,
        )
    }
}

private fun statusText(status: GameStatus): String = when (status) {
    GameStatus.STALEMATE -> "Stalemate"
    GameStatus.DRAW_FIFTY_MOVES -> "Fifty-move rule"
    GameStatus.DRAW_REPETITION -> "Threefold repetition"
    GameStatus.DRAW_INSUFFICIENT_MATERIAL -> "Insufficient material"
    else -> ""
}

@Composable
fun CoachPanel(
    initializing: Boolean,
    messages: List<CoachMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Coach Gemma", style = MaterialTheme.typography.titleMedium)
                if (initializing) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "warming up…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (messages.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                val listState = rememberLazyListState()
                LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
                    listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(messages) { msg ->
                        Text(
                            text = msg.text + if (msg.isStreaming) " ▌" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = if (msg.text.startsWith("You: ")) FontStyle.Italic else FontStyle.Normal,
                        )
                    }
                }
            }
            if (!initializing) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ask your coach…") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onSend, enabled = input.isNotBlank()) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                            )
                        }
                    },
                )
            }
        }
    }
}
