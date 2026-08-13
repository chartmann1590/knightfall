package com.chartmann.knightfall.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.coach.CoachModelManager
import com.chartmann.knightfall.data.SettingsRepository
import com.chartmann.knightfall.ui.board.BoardTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: SettingsRepository.Snapshot?,
    isAnonymous: Boolean,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onMoreApps: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = container.settings
    val modelState by container.modelManager.stateFlow()
        .collectAsState(initial = CoachModelManager.ModelState.NotDownloaded)
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            SectionHeader("PROFILE & PRIVACY")
            ToggleRow(
                title = "Public profile",
                subtitle = "Show your name, rating and stats on the in-app leaderboard and the Knightfall website",
                checked = if (isAnonymous) false else (settings?.publicProfile ?: false),
                enabled = !isAnonymous,
                onChange = { on ->
                    scope.launch {
                        try {
                            repo.setPublicProfile(on)
                            container.auth.uid?.let { container.users.setPublic(it, on) }
                        } catch (e: Exception) {
                            repo.setPublicProfile(!on)
                        }
                    }
                },
            )

            SectionHeader("DATA SHARING")
            ToggleRow(
                title = "Crash reports",
                subtitle = "Send anonymous crash reports so bugs get fixed faster (Crashlytics)",
                checked = settings?.crashlytics ?: true,
                onChange = { scope.launch { repo.setCrashlytics(it) } },
            )
            ToggleRow(
                title = "Usage analytics",
                subtitle = "Share anonymous usage statistics (Google Analytics for Firebase)",
                checked = settings?.analytics ?: true,
                onChange = { scope.launch { repo.setAnalytics(it) } },
            )
            ToggleRow(
                title = "Performance monitoring",
                subtitle = "Share anonymous app performance data",
                checked = settings?.performance ?: true,
                onChange = { scope.launch { repo.setPerformance(it) } },
            )
            ToggleRow(
                title = "Notifications & messaging",
                subtitle = "Allow Knightfall news and updates (Firebase Cloud Messaging)",
                checked = settings?.messaging ?: true,
                onChange = { scope.launch { repo.setMessaging(it) } },
            )

            SectionHeader("GAME")
            ToggleRow(
                title = "Sounds",
                subtitle = "Move, capture and game-end sounds",
                checked = settings?.sounds ?: true,
                onChange = { scope.launch { repo.setSounds(it) } },
            )
            ToggleRow(
                title = "Haptics",
                subtitle = "Gentle vibration on moves",
                checked = settings?.haptics ?: true,
                onChange = { scope.launch { repo.setHaptics(it) } },
            )

            Spacer(Modifier.height(12.dp))
            Text("Board theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (theme in BoardTheme.entries) {
                    FilterChip(
                        selected = (settings?.boardTheme ?: "walnut") == theme.id,
                        onClick = { scope.launch { repo.setBoardTheme(theme.id) } },
                        label = { Text(theme.displayName) },
                    )
                }
            }

            SectionHeader("AI COACH (GEMMA 4)")
            when (val ms = modelState) {
                is CoachModelManager.ModelState.Ready -> {
                    ToggleRow(
                        title = "Coach commentary",
                        subtitle = "Let Gemma 4 comment on your AI games",
                        checked = settings?.coachEnabled ?: true,
                        onChange = { scope.launch { repo.setCoachEnabled(it) } },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            container.coach.shutdown()
                            container.modelManager.deleteModel()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Delete model (frees 2.5 GB)") }
                }
                is CoachModelManager.ModelState.Downloading -> {
                    Text(
                        "Downloading coach model… ${ms.progressPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { ms.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { container.modelManager.cancelDownload() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel download") }
                }
                else -> {
                    Text(
                        "The on-device coach watches your games and explains what's happening in plain language. ~2.5 GB download, Wi-Fi recommended.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { container.modelManager.startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Download coach (2.5 GB)") }
                }
            }

            SectionHeader("SUPPORT & FEEDBACK")
            com.chartmann.knightfall.ui.settings.SupportCard(container)

            SectionHeader("MORE APPS")
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onMoreApps),
            ) {
                Text(
                    "More apps from this developer",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }

            SectionHeader("ACCOUNT")
            if (isAnonymous) {
                Text(
                    "You're playing as a guest. Your stats live only on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val credManager = CredentialManager.create(context)
                                val googleOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(com.chartmann.knightfall.BuildConfig.GOOGLE_CLIENT_ID)
                                    .setAutoSelectEnabled(false)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleOption)
                                    .build()
                                val result = credManager.getCredential(context, request)
                                val cred = result.credential
                                if (cred is CustomCredential &&
                                    cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                ) {
                                    val tokenCred = GoogleIdTokenCredential.createFrom(cred.data)
                                    try {
                                        container.auth.linkAnonymousToGoogle(tokenCred.idToken)
                                    } catch (_: Exception) {
                                        container.auth.signInWithGoogle(tokenCred.idToken)
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Link Google account") }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick = {
                    container.auth.signOut()
                    onSignedOut()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out") }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Delete account & data") }

            Spacer(Modifier.height(24.dp))
            Text(
                "Knightfall · Chess pieces by Colin M.L. Burnett · Engine: Stockfish 18 (GPLv3) · Coach: Gemma 4 via LiteRT-LM",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = { Text("Your profile, rating and game history will be permanently removed. This cannot be undone.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            try {
                                container.auth.uid?.let { container.users.deleteAllUserData(it) }
                                container.auth.deleteAccount()
                            } catch (_: Exception) {
                                container.auth.signOut()
                            }
                            onSignedOut()
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}
