package com.chartmann.knightfall.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.data.SettingsRepository
import com.chartmann.knightfall.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * App-wide session state: who is signed in, their profile, and the local
 * settings snapshot. Lives at the activity scope and is shared by screens.
 */
data class SessionState(
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val isAnonymous: Boolean = true,
    val uid: String? = null,
    val profile: UserProfile? = null,
    val settings: SettingsRepository.Snapshot? = null,
)

class SessionViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.snapshots.collectLatest { snap ->
                _state.value = _state.value.copy(settings = snap)
                container.sounds.enabled = snap.sounds
            }
        }
        viewModelScope.launch {
            container.auth.userFlow.collectLatest { user ->
                if (user == null) {
                    _state.value = _state.value.copy(
                        loading = false,
                        signedIn = false,
                        isAnonymous = true,
                        uid = null,
                        profile = null,
                    )
                } else {
                    _state.value = _state.value.copy(
                        loading = false,
                        signedIn = true,
                        isAnonymous = user.isAnonymous,
                        uid = user.uid,
                    )
                    container.users.profileFlow(user.uid).collectLatest { profile ->
                        _state.value = _state.value.copy(profile = profile)
                    }
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SessionViewModel(container) as T
            }
    }
}
