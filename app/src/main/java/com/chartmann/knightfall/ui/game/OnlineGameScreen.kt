package com.chartmann.knightfall.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.data.model.GameStatusValues
import com.chartmann.knightfall.ui.board.BoardTheme
import com.chartmann.knightfall.ui.board.ChessBoard
import com.github.bhlangonijr.chesslib.Side
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineGameScreen(
    container: AppContainer,
    gameId: String,
    boardTheme: BoardTheme,
    onExit: () -> Unit,
    onPlayAi: () -> Unit,
) {
    val vm: OnlineGameViewModel = viewModel(
        key = "online-$gameId",
        factory = OnlineGameViewModel.factory(container, gameId),
    )
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val myUid = container.auth.uid ?: ""

    var showAiOfferDialog by remember { mutableStateOf(false) }
    var hasDeclinedAiOffer by remember { mutableStateOf(false) }

    val showDialogTrigger = state.waitingForOpponent && state.game != null && state.game?.inviteCode == null

    LaunchedEffect(showDialogTrigger, hasDeclinedAiOffer) {
        if (showDialogTrigger && !hasDeclinedAiOffer) {
            delay(60_000L)
            showAiOfferDialog = true
        } else {
            showAiOfferDialog = false
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    val game = state.game
    val opponentName = game?.opponentName(myUid)?.ifBlank { "Opponent" } ?: "Opponent"
    val myName = if (state.mySide == Side.WHITE) game?.whiteName else game?.blackName

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (game?.rated == true) "Rated game" else "Friendly game",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (game?.inviteCode != null && state.waitingForOpponent) {
                            Text(
                                "Code: ${game.inviteCode}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.waitingForOpponent) vm.cancelWaiting()
                        onExit()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.finished && !state.waitingForOpponent) {
                        IconButton(onClick = { vm.offerDraw() }) {
                            Icon(Icons.Filled.Handshake, contentDescription = "Offer draw")
                        }
                        IconButton(onClick = { vm.resign() }) {
                            Icon(Icons.Filled.Flag, contentDescription = "Resign")
                        }
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
                .navigationBarsPadding(),
        ) {
            if (state.waitingForOpponent) {
                WaitingForOpponent(
                    inviteCode = game?.inviteCode,
                    onCancel = {
                        vm.cancelWaiting()
                        onExit()
                    },
                )
                return@Column
            }

            PlayerBar(
                name = opponentName,
                subtitle = eloLabel(game, state.mySide.opposite()),
                isTurn = state.sideToMove != state.mySide && !state.finished,
                capturedPieces = state.capturedByOpponent,
                capturedSide = state.mySide,
                materialAdvantage = 0,
            )
            Spacer(Modifier.height(8.dp))

            ChessBoard(
                position = state.position,
                flipped = state.mySide == Side.BLACK,
                theme = boardTheme,
                selected = state.selected,
                legalTargets = state.legalTargets,
                lastMove = state.lastMove,
                checkSquare = state.checkSquare,
                interactive = !state.finished && state.sideToMove == state.mySide,
                onSquareTap = vm::onSquareTap,
                onDragMove = vm::onDragMove,
            )

            Spacer(Modifier.height(8.dp))
            PlayerBar(
                name = myName?.ifBlank { "You" } ?: "You",
                subtitle = eloLabel(game, state.mySide),
                isTurn = state.sideToMove == state.mySide && !state.finished,
                capturedPieces = state.capturedByMe,
                capturedSide = state.mySide.opposite(),
                materialAdvantage = 0,
            )

            AnimatedVisibility(visible = state.opponentIdle && !state.finished) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Your opponent seems to be away.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.claimAbandonedWin() }) {
                            Text("Claim win")
                        }
                    }
                }
            }

            AnimatedVisibility(visible = state.finished) {
                GameOverCard(
                    title = state.resultTitle,
                    subtitle = state.resultSubtitle,
                    eloDelta = state.eloDelta,
                    onRematch = null,
                    onExit = onExit,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    if (state.drawOfferedByOpponent && !state.finished) {
        AlertDialog(
            onDismissRequest = { vm.respondToDraw(false) },
            title = { Text("Draw offer") },
            text = { Text("$opponentName offers a draw.") },
            confirmButton = {
                Button(onClick = { vm.respondToDraw(true) }) { Text("Accept") }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.respondToDraw(false) }) { Text("Decline") }
            },
        )
    }

    if (showAiOfferDialog) {
        AlertDialog(
            onDismissRequest = {
                hasDeclinedAiOffer = true
                showAiOfferDialog = false
            },
            title = { Text("No opponent found") },
            text = { Text("Would you like to play against the AI instead?") },
            confirmButton = {
                Button(onClick = {
                    showAiOfferDialog = false
                    vm.cancelWaiting()
                    onPlayAi()
                }) {
                    Text("Play AI")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    hasDeclinedAiOffer = true
                    showAiOfferDialog = false
                }) {
                    Text("Keep Waiting")
                }
            }
        )
    }

    state.pendingPromotion?.let {
        PromotionPicker(
            side = state.mySide,
            onPick = vm::onPromotionPicked,
            onDismiss = vm::onPromotionDismissed,
        )
    }
}

@Composable
private fun WaitingForOpponent(inviteCode: String?, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (inviteCode != null) "Waiting for your friend…" else "Finding an opponent…",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (inviteCode != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Share this code:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = inviteCode.chunked(1).joinToString(" "),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

private fun eloLabel(
    game: com.chartmann.knightfall.data.model.OnlineGame?,
    side: Side,
): String? {
    if (game == null || !game.rated) return null
    val elo = if (side == Side.WHITE) game.whiteEloStart else game.blackEloStart
    return "$elo Elo"
}

private fun Side.opposite(): Side = if (this == Side.WHITE) Side.BLACK else Side.WHITE
