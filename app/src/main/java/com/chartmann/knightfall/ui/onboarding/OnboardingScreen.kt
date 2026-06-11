package com.chartmann.knightfall.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.R
import com.chartmann.knightfall.coach.CoachModelManager

@Composable
fun OnboardingScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state.step) {
            OnboardingStep.WELCOME -> Welcome(onNext = { vm.goTo(OnboardingStep.ACCOUNT) })
            OnboardingStep.ACCOUNT -> AccountChoice(vm, state)
            OnboardingStep.EMAIL_FORM -> EmailForm(vm, state)
            OnboardingStep.USERNAME -> UsernameForm(vm, state)
            OnboardingStep.COACH -> CoachOffer(vm, state) { vm.finish(onDone) }
            OnboardingStep.DONE -> {}
        }
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        if (state.working) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Image(
        painter = painterResource(R.drawable.ic_knight),
        contentDescription = null,
        modifier = Modifier.size(140.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text("Knightfall", style = MaterialTheme.typography.displayLarge)
    Spacer(Modifier.height(10.dp))
    Text(
        "Play beautiful chess.\nChallenge a powerful on-device AI, battle friends online, and climb the leaderboard.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text("Get started")
    }
}

@Composable
private fun AccountChoice(vm: OnboardingViewModel, state: OnboardingState) {
    Text("How do you want to play?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        "An account keeps your rating and stats safe across devices. Guests can play everything too — you can upgrade later.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = {
            vm.setSignInMode(false)
            vm.goTo(OnboardingStep.EMAIL_FORM)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.working,
    ) { Text("Create an account") }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = {
            vm.setSignInMode(true)
            vm.goTo(OnboardingStep.EMAIL_FORM)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.working,
    ) { Text("I already have an account") }
    Spacer(Modifier.height(10.dp))
    TextButton(
        onClick = { vm.continueAsGuest() },
        enabled = !state.working,
    ) { Text("Play as guest") }
}

@Composable
private fun EmailForm(vm: OnboardingViewModel, state: OnboardingState) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(
        if (state.isSignIn) "Welcome back" else "Create your account",
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { vm.submitEmail(email, password) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.working,
    ) { Text(if (state.isSignIn) "Sign in" else "Sign up") }
    TextButton(onClick = { vm.goTo(OnboardingStep.ACCOUNT) }) { Text("Back") }
}

@Composable
private fun UsernameForm(vm: OnboardingViewModel, state: OnboardingState) {
    var username by remember { mutableStateOf("") }
    Text("Pick a player name", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "This is how opponents see you.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = username,
        onValueChange = { if (it.length <= 24) username = it },
        label = { Text("Player name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { vm.submitUsername(username) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.working,
    ) { Text("Continue") }
}

@Composable
private fun CoachOffer(
    vm: OnboardingViewModel,
    state: OnboardingState,
    onFinish: () -> Unit,
) {
    Text("Meet your AI coach", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        "Gemma 4 runs entirely on your phone and comments on your games like a friendly coach — no internet needed once downloaded.\n\nThe model is a ~2.5 GB download. Wi-Fi recommended. You can also do this later in Settings.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    when (val ms = state.modelState) {
        is CoachModelManager.ModelState.NotDownloaded -> {
            Button(onClick = { vm.startCoachDownload() }, modifier = Modifier.fillMaxWidth()) {
                Text("Download coach (2.5 GB)")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
            }
        }
        is CoachModelManager.ModelState.Downloading -> {
            LinearProgressIndicator(
                progress = { ms.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Downloading… ${ms.progressPercent}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("Continue — it'll finish in the background")
            }
        }
        is CoachModelManager.ModelState.Ready -> {
            Text("✓ Coach ready!", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("Let's play")
            }
        }
        is CoachModelManager.ModelState.Failed -> {
            Text(ms.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.startCoachDownload() }, modifier = Modifier.fillMaxWidth()) {
                Text("Retry download")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
            }
        }
    }
}
