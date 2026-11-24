package com.chartmann.knightfall.ui.leaderboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.data.model.PublicProfile
import com.chartmann.knightfall.ui.components.BlockPlayerDialog
import com.chartmann.knightfall.ui.components.ReportPlayerDialog
import com.chartmann.knightfall.ui.theme.Gold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var players by remember { mutableStateOf<List<PublicProfile>>(emptyList()) }
    var blockedUids by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reportTarget by remember { mutableStateOf<PublicProfile?>(null) }
    var blockTarget by remember { mutableStateOf<PublicProfile?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val myUid = container.auth.uid

    LaunchedEffect(Unit) {
        try {
            if (myUid != null) {
                blockedUids = container.users.getProfile(myUid)?.blockedUids ?: emptyList()
            }
            players = container.users.leaderboard(blockedUids = blockedUids, myUid = myUid)
        } catch (e: Exception) {
            error = "Couldn't load the leaderboard — check your connection"
        } finally {
            loading = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(
                    error ?: "",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                players.isEmpty() -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No public players yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Be the first! Turn on “Public profile” in Settings to appear here and on the Knightfall website.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(players) { index, player ->
                        LeaderboardRow(
                            rank = index + 1,
                            player = player,
                            isMe = player.uid == myUid,
                            canModerate = myUid != null && player.uid != myUid,
                            onReport = { reportTarget = player },
                            onBlock = { blockTarget = player },
                        )
                    }
                }
            }
        }

        reportTarget?.let { target ->
            ReportPlayerDialog(
                playerName = target.username,
                onDismiss = { reportTarget = null },
                onReport = { reason ->
                    reportTarget = null
                    val reporter = myUid
                    scope.launch {
                        try {
                            if (reporter != null) {
                                container.moderation.reportPlayer(
                                    reporterUid = reporter,
                                    reportedUid = target.uid,
                                    reportedName = target.username,
                                    reason = reason,
                                    context = "leaderboard",
                                )
                            }
                            snackbar.showSnackbar("Report sent — thanks, we'll review it.")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Couldn't send the report. Please try again.")
                        }
                    }
                },
            )
        }

        blockTarget?.let { target ->
            BlockPlayerDialog(
                playerName = target.username,
                onDismiss = { blockTarget = null },
                onBlock = {
                    blockTarget = null
                    val me = myUid
                    scope.launch {
                        try {
                            if (me != null) {
                                container.moderation.blockPlayer(me, target.uid)
                                blockedUids = blockedUids + target.uid
                                players = players.filter { it.uid != target.uid }
                            }
                            snackbar.showSnackbar("${target.username} blocked.")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Couldn't block that player. Please try again.")
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    player: PublicProfile,
    isMe: Boolean,
    canModerate: Boolean = false,
    onReport: () -> Unit = {},
    onBlock: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isMe) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (rank <= 3) 4.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (rank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "$rank"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(36.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = if (rank <= 3) 1f else 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    player.username.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    player.username + if (isMe) " (you)" else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${player.wins}W · ${player.losses}L · ${player.draws}D",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${player.elo}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            if (canModerate) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Player options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Report player") },
                            onClick = { menuOpen = false; onReport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Block player") },
                            onClick = { menuOpen = false; onBlock() },
                        )
                    }
                }
            }
        }
    }
}
