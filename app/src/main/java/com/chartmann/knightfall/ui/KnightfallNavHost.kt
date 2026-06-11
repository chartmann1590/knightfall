package com.chartmann.knightfall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chartmann.knightfall.KnightfallApp
import com.chartmann.knightfall.chess.StockfishEngine
import com.chartmann.knightfall.ui.board.BoardTheme
import com.chartmann.knightfall.ui.game.AiGameScreen
import com.chartmann.knightfall.ui.game.OnlineGameScreen
import com.chartmann.knightfall.ui.home.AiSetupScreen
import com.chartmann.knightfall.ui.home.FriendScreen
import com.chartmann.knightfall.ui.home.HomeScreen
import com.chartmann.knightfall.ui.leaderboard.LeaderboardScreen
import com.chartmann.knightfall.ui.onboarding.OnboardingScreen
import com.chartmann.knightfall.ui.profile.ProfileScreen
import com.chartmann.knightfall.ui.session.SessionViewModel
import com.chartmann.knightfall.ui.settings.SettingsScreen
import com.github.bhlangonijr.chesslib.Side

@Composable
fun KnightfallNavHost() {
    val context = LocalContext.current
    val container = (context.applicationContext as KnightfallApp).container
    val session: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
    val sessionState by session.state.collectAsState()
    val navController = rememberNavController()

    val boardTheme = BoardTheme.fromId(sessionState.settings?.boardTheme ?: "walnut")

    if (sessionState.loading || sessionState.settings == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val needsOnboarding = !sessionState.settings!!.onboarded || !sessionState.signedIn

    NavHost(
        navController = navController,
        startDestination = if (needsOnboarding) "onboarding" else "home",
    ) {
        composable("onboarding") {
            OnboardingScreen(
                container = container,
                onDone = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
            )
        }

        composable("home") {
            HomeScreen(
                profile = sessionState.profile,
                isAnonymous = sessionState.isAnonymous,
                onPlayAi = { navController.navigate("ai-setup") },
                onQuickMatch = { navController.navigate("matchmaking") },
                onPlayFriend = { navController.navigate("friend") },
                onLeaderboard = { navController.navigate("leaderboard") },
                onProfile = { navController.navigate("profile") },
                onSettings = { navController.navigate("settings") },
            )
        }

        composable("ai-setup") {
            AiSetupScreen(
                onBack = { navController.popBackStack() },
                onStart = { difficulty, side ->
                    navController.navigate("game/ai/${difficulty.name}/${side.name}") {
                        popUpTo("home")
                    }
                },
            )
        }

        composable(
            route = "game/ai/{difficulty}/{side}",
            arguments = listOf(
                navArgument("difficulty") { type = NavType.StringType },
                navArgument("side") { type = NavType.StringType },
            ),
        ) { entry ->
            val difficulty = StockfishEngine.Difficulty.valueOf(
                entry.arguments?.getString("difficulty") ?: "KNIGHT",
            )
            val side = Side.valueOf(entry.arguments?.getString("side") ?: "WHITE")
            AiGameScreen(
                container = container,
                difficulty = difficulty,
                playerSide = side,
                boardTheme = boardTheme,
                onExit = { navController.popBackStack("home", inclusive = false) },
            )
        }

        composable("matchmaking") {
            MatchmakingScreen(
                container = container,
                onGameReady = { gameId ->
                    navController.navigate("game/online/$gameId") {
                        popUpTo("home")
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("friend") {
            FriendScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onGameReady = { gameId ->
                    navController.navigate("game/online/$gameId") {
                        popUpTo("home")
                    }
                },
            )
        }

        composable(
            route = "game/online/{gameId}",
            arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
        ) { entry ->
            val gameId = entry.arguments?.getString("gameId") ?: return@composable
            OnlineGameScreen(
                container = container,
                gameId = gameId,
                boardTheme = boardTheme,
                onExit = { navController.popBackStack("home", inclusive = false) },
                onPlayAi = {
                    navController.navigate("ai-setup") {
                        popUpTo("home")
                    }
                },
            )
        }

        composable("leaderboard") {
            LeaderboardScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }

        composable("profile") {
            ProfileScreen(
                container = container,
                profile = sessionState.profile,
                isAnonymous = sessionState.isAnonymous,
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings") {
            SettingsScreen(
                container = container,
                settings = sessionState.settings,
                isAnonymous = sessionState.isAnonymous,
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate("onboarding") {
                        popUpTo("home") { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
private fun MatchmakingScreen(
    container: com.chartmann.knightfall.AppContainer,
    onGameReady: (String) -> Unit,
    onBack: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val uid = container.auth.uid ?: error("Not signed in")
            val profile = container.users.getProfile(uid) ?: error("Profile not ready")
            val gameId = container.games.quickMatch(uid, profile.username, profile.elo)
            onGameReady(gameId)
        } catch (e: Exception) {
            error = e.message ?: "Matchmaking failed"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (error == null) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Setting up your match…", style = MaterialTheme.typography.titleLarge)
        } else {
            Text(
                error ?: "",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}
