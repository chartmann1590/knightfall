package com.chartmann.knightfall.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.badges.BadgeRegistry
import com.chartmann.knightfall.data.model.Puzzle
import com.chartmann.knightfall.ui.board.BoardTheme
import com.chartmann.knightfall.ui.board.ChessBoard
import com.chartmann.knightfall.ui.components.BannerAdView
import com.chartmann.knightfall.ui.theme.Gold
import com.chartmann.knightfall.ui.theme.LossRed
import com.chartmann.knightfall.ui.theme.WinGreen
import com.github.bhlangonijr.chesslib.Side

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    container: AppContainer,
    puzzle: Puzzle,
    boardTheme: BoardTheme,
    onBack: () -> Unit,
) {
    val vm: PuzzleViewModel = viewModel(factory = PuzzleViewModel.factory(container))
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(puzzle.id) {
        vm.loadPuzzle(puzzle)
    }

    LaunchedEffect(state.newBadges) {
        if (state.newBadges.isNotEmpty()) {
            val names = state.newBadges.mapNotNull { BadgeRegistry.getById(it)?.name }
            snackbarHostState.showSnackbar("🏅 Badge earned: ${names.joinToString(", ")}")
            vm.clearNewBadges()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val rating = puzzle.rating
                    val theme = puzzle.themes.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Puzzle"
                    Text("$theme · Rating $rating")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        bottomBar = { BannerAdView() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            val sideLabel = if (state.playerSide == Side.WHITE) "White to move" else "Black to move"
            Text(
                sideLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Box {
                ChessBoard(
                    position = state.position,
                    flipped = state.playerSide == Side.BLACK,
                    theme = boardTheme,
                    selected = state.selectedSquare,
                    legalTargets = state.legalTargets,
                    lastMove = state.lastMove,
                    checkSquare = null,
                    interactive = !state.solved && !state.failed,
                    onSquareTap = vm::onSquareTap,
                    onDragMove = vm::onDragMove,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Result overlay
                if (state.solved || state.failed) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 8.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (state.solved) {
                                Text("✓ Solved!", style = MaterialTheme.typography.headlineSmall,
                                    color = WinGreen, fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = onBack,
                                    colors = ButtonDefaults.buttonColors(containerColor = WinGreen),
                                ) { Text("Next puzzle") }
                            } else {
                                Text("✗ Incorrect", style = MaterialTheme.typography.headlineSmall,
                                    color = LossRed, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = vm::retry) { Text("Retry") }
                                    Button(onClick = onBack) { Text("Next puzzle") }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                if (!state.solved && !state.failed) "Find the best move" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = Gold,
            )
        }
    }
}
