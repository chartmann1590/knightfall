package com.chartmann.knightfall.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.coach.CoachModelManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class OnboardingStep { WELCOME, ACCOUNT, EMAIL_FORM, USERNAME, COACH, DONE }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val working: Boolean = false,
    val error: String? = null,
    val isSignIn: Boolean = false,
    val username: String = "",
    val modelState: CoachModelManager.ModelState = CoachModelManager.ModelState.NotDownloaded,
)

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.modelManager.stateFlow().collectLatest {
                _state.value = _state.value.copy(modelState = it)
            }
        }
    }

    fun goTo(step: OnboardingStep) {
        _state.value = _state.value.copy(step = step, error = null)
    }

    fun setSignInMode(signIn: Boolean) {
        _state.value = _state.value.copy(isSignIn = signIn)
    }

    fun signInWithGoogle(idToken: String) {
        runAuth {
            val isGuest = container.auth.isAnonymous
            if (isGuest) {
                // Keep the existing anonymous uid + any stats by linking
                try {
                    container.auth.linkAnonymousToGoogle(idToken)
                } catch (_: Exception) {
                    // Account already exists — flush any pending Firestore writes for the
                    // anonymous UID before switching auth; otherwise the SDK may replay them
                    // under the new UID and receive PERMISSION_DENIED, which escapes to the
                    // main thread due to Firebase SDK bug #6443.
                    try { FirebaseFirestore.getInstance().waitForPendingWrites().await() } catch (_: Exception) {}
                    container.auth.signInWithGoogle(idToken)
                }
            } else {
                container.auth.signInWithGoogle(idToken)
            }
            val uid = container.auth.uid ?: error("Sign-in succeeded but uid is null")
            val profile = container.users.getProfile(uid)
            _state.value = _state.value.copy(
                step = if (profile != null) OnboardingStep.COACH else OnboardingStep.USERNAME,
            )
        }
    }

    fun continueAsGuest() {
        runAuth {
            container.auth.signInAnonymously()
            _state.value = _state.value.copy(step = OnboardingStep.USERNAME)
        }
    }

    fun submitEmail(email: String, password: String) {
        if (email.isBlank() || password.length < 6) {
            _state.value = _state.value.copy(error = "Enter an email and a password of 6+ characters")
            return
        }
        runAuth {
            if (_state.value.isSignIn) {
                container.auth.signInWithEmail(email.trim(), password)
                // Existing account: skip username if a profile already exists.
                val uid = container.auth.uid
                val profile = uid?.let { container.users.getProfile(it) }
                _state.value = _state.value.copy(
                    step = if (profile != null) OnboardingStep.COACH else OnboardingStep.USERNAME,
                )
            } else {
                container.auth.signUpWithEmail(email.trim(), password)
                _state.value = _state.value.copy(step = OnboardingStep.USERNAME)
            }
        }
    }

    fun submitUsername(username: String) {
        val name = username.trim()
        if (name.length < 2) {
            _state.value = _state.value.copy(error = "Pick a name with at least 2 characters")
            return
        }
        runAuth {
            val uid = container.auth.uid ?: error("Not signed in")
            container.users.createProfile(uid, name, avatarColor = "gold")
            _state.value = _state.value.copy(step = OnboardingStep.COACH, username = name)
        }
    }

    fun startCoachDownload() {
        container.modelManager.startDownload()
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            container.settings.setOnboarded()
            container.settings.applyPrivacyChoices()
            onDone()
        }
    }

    private fun runAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, error = null)
            try {
                block()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message ?: "Something went wrong — try again",
                )
            } finally {
                _state.value = _state.value.copy(working = false)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OnboardingViewModel(container) as T
            }
    }
}
