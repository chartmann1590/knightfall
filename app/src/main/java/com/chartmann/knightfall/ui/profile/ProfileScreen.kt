package com.chartmann.knightfall.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.data.model.GameResultValues
import com.chartmann.knightfall.data.model.OnlineGame
import com.chartmann.knightfall.data.model.UserProfile
import com.chartmann.knightfall.ui.theme.DrawGray
import com.chartmann.knightfall.ui.theme.Gold
import com.chartmann.knightfall.ui.theme.LossRed
import com.chartmann.knightfall.ui.theme.WinGreen
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    container: AppContainer,
    profile: UserProfile?,
    isAnonymous: Boolean,
    onBack: () -> Unit,
) {
    var history by remember { mutableStateOf<List<OnlineGame>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(true) }
    val uid = container.auth.uid

    LaunchedEffect(uid) {
        if (uid != null) {
            try {
                history = container.games.recentGames(uid)
            } catch (_: Exception) {
            }
        }
        historyLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My profile") },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Gold),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (profile?.username?.take(1) ?: "?").uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.background,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(profile?.username ?: "—", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (isAnonymous) "Guest player" else "Registered player",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (profile?.isPublic == true) {
                        Text(
                            "Public profile · visible on the website",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Elo", "${profile?.elo ?: 1200}", Modifier.weight(1f))
                StatCard("Best streak", "${profile?.bestWinStreak ?: 0}", Modifier.weight(1f))
                StatCard("AI wins", "${profile?.aiWins ?: 0}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Wins", "${profile?.wins ?: 0}", Modifier.weight(1f), WinGreen)
                StatCard("Losses", "${profile?.losses ?: 0}", Modifier.weight(1f), LossRed)
                StatCard("Draws", "${profile?.draws ?: 0}", Modifier.weight(1f), DrawGray)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "RECENT ONLINE GAMES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            when {
                historyLoading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp),
                )
                history.isEmpty() -> Text(
                    "No online games yet. Hit Quick match to play your first!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (game in history) {
                        HistoryRow(game, uid ?: "")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(game: OnlineGame, myUid: String) {
    val mySeat = game.seatOf(myUid)
    val iWon = (game.result == GameResultValues.WHITE_WIN && mySeat == "white") ||
        (game.result == GameResultValues.BLACK_WIN && mySeat == "black")
    val isDraw = game.result == GameResultValues.DRAW
    val opponent = game.opponentName(myUid).ifBlank { "Opponent" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    isDraw -> "½"
                    iWon -> "W"
                    else -> "L"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    isDraw -> DrawGray
                    iWon -> WinGreen
                    else -> LossRed
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(28.dp),
            )
            Column(Modifier.weight(1f)) {
                Text("vs $opponent", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${game.moves.size} moves" + (game.finishedAt?.let {
                        " · " + DateFormat.getDateInstance(DateFormat.SHORT).format(it.toDate())
                    } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!game.rated) {
                Text(
                    "casual",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
