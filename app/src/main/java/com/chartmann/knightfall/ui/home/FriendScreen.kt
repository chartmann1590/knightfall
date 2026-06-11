package com.chartmann.knightfall.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chartmann.knightfall.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onGameReady: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var rated by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun me(): Triple<String, String, Int>? {
        val uid = container.auth.uid ?: return null
        val profile = container.users.getProfile(uid) ?: return null
        return Triple(uid, profile.username, profile.elo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Play a friend") },
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
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Start a new game", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "You'll get a 6-letter code to share. Your friend enters it below on their phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = rated, onCheckedChange = { rated = it })
                Spacer(Modifier.height(0.dp))
                Text(
                    "  Rated game (affects Elo)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        working = true
                        error = null
                        try {
                            val (uid, name, elo) = me() ?: error("Profile not ready yet")
                            val (gameId, _) = container.games.createInviteGame(uid, name, elo, rated)
                            onGameReady(gameId)
                        } catch (e: Exception) {
                            error = e.message ?: "Couldn't create the game"
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create game code") }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(28.dp))

            Text("Join a friend's game", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(6) },
                label = { Text("Enter game code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        working = true
                        error = null
                        try {
                            val (uid, name, elo) = me() ?: error("Profile not ready yet")
                            val gameId = container.games.joinByInviteCode(code, uid, name, elo)
                            onGameReady(gameId)
                        } catch (e: Exception) {
                            error = "Couldn't join — double-check the code"
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = !working && code.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Join game") }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (working) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
