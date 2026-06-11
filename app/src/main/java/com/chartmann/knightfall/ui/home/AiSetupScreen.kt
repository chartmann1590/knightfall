package com.chartmann.knightfall.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chartmann.knightfall.chess.StockfishEngine
import com.chartmann.knightfall.ui.game.clickableNoIndication
import com.github.bhlangonijr.chesslib.Side
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSetupScreen(
    onBack: () -> Unit,
    onStart: (StockfishEngine.Difficulty, Side) -> Unit,
) {
    var difficulty by remember { mutableStateOf(StockfishEngine.Difficulty.KNIGHT) }
    var sideChoice by remember { mutableStateOf("white") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Play the AI") },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "DIFFICULTY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            for (level in StockfishEngine.Difficulty.entries) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickableNoIndication { difficulty = level },
                    shape = RoundedCornerShape(12.dp),
                    color = if (difficulty == level) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (difficulty == level) 4.dp else 1.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                level.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (difficulty == level) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                "~${level.approxElo} Elo",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (difficulty == level) {
                            Text("●", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "YOU PLAY AS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = sideChoice == "white",
                    onClick = { sideChoice = "white" },
                    label = { Text("White") },
                )
                FilterChip(
                    selected = sideChoice == "black",
                    onClick = { sideChoice = "black" },
                    label = { Text("Black") },
                )
                FilterChip(
                    selected = sideChoice == "random",
                    onClick = { sideChoice = "random" },
                    label = { Text("Random") },
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    val side = when (sideChoice) {
                        "white" -> Side.WHITE
                        "black" -> Side.BLACK
                        else -> if (Random.nextBoolean()) Side.WHITE else Side.BLACK
                    }
                    onStart(difficulty, side)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start game")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
