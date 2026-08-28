package com.nebulaiq.assignment.presentation.welcome

import com.nebulaiq.assignment.domain.model.Group
import com.nebulaiq.assignment.domain.repository.AuthRepository
import com.nebulaiq.assignment.domain.repository.GroupRepository
import com.nebulaiq.assignment.presentation.core.BaseViewModel
import java.util.concurrent.atomic.AtomicBoolean

data class WelcomeState(
    val name: String = "",
    val isInitializing: Boolean = true,
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val existingGroup: Group? = null,
    val errorMessage: String? = null,
)

sealed interface WelcomeEvent {
    data object ScreenOpened : WelcomeEvent
    data class NameChanged(val value: String) : WelcomeEvent
    data object ContinueClicked : WelcomeEvent
    data class AnonymousSignInSucceeded(val userId: String) : WelcomeEvent
    data class GroupLookupCompleted(val group: Group?) : WelcomeEvent
    data object SessionCheckCompleted : WelcomeEvent
    data class AnonymousSignInFailed(val message: String) : WelcomeEvent
}

sealed interface WelcomeEffect {
    data class ShowMessage(val message: String) : WelcomeEffect
}

class WelcomeViewModel(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
) : BaseViewModel<WelcomeState, WelcomeEvent, WelcomeEffect>(WelcomeState()) {
    private val continueInFlight = AtomicBoolean(false)

    override fun reduce(state: WelcomeState, event: WelcomeEvent): WelcomeState =
        when (event) {
            WelcomeEvent.ScreenOpened -> state
            is WelcomeEvent.NameChanged -> state.copy(name = event.value, errorMessage = null)
            WelcomeEvent.ContinueClicked -> {
                if (state.name.isBlank()) {
                    state.copy(errorMessage = "Enter your name to continue")
                } else {
                    state.copy(isLoading = true, errorMessage = null)
                }
            }
            is WelcomeEvent.AnonymousSignInSucceeded -> state.copy(
                isLoading = true,
                isComplete = false,
                errorMessage = null,
            )
            is WelcomeEvent.GroupLookupCompleted -> state.copy(
                isInitializing = false,
                isLoading = false,
                isComplete = true,
                existingGroup = event.group,
                errorMessage = null,
            )
            WelcomeEvent.SessionCheckCompleted -> state.copy(
                isInitializing = false,
                isLoading = false,
            )
            is WelcomeEvent.AnonymousSignInFailed -> state.copy(
                isInitializing = false,
                isLoading = false,
                errorMessage = event.message,
            )
        }

    override suspend fun handleSideEffect(event: WelcomeEvent) {
        when (event) {
            WelcomeEvent.ScreenOpened -> {
                val userId = authRepository.currentUserId()
                if (userId == null) {
                    dispatch(WelcomeEvent.SessionCheckCompleted)
                } else {
                    groupRepository.findGroupForUser(userId).fold(
                        onSuccess = { dispatch(WelcomeEvent.GroupLookupCompleted(it)) },
                        onFailure = {
                            dispatch(
                                WelcomeEvent.AnonymousSignInFailed(
                                    it.message ?: "Could not load your group",
                                ),
                            )
                        },
                    )
                }
            }
            WelcomeEvent.ContinueClicked -> {
                if (state.value.name.isBlank() || !continueInFlight.compareAndSet(false, true)) return

                authRepository.signInAnonymously().fold(
                    onSuccess = { dispatch(WelcomeEvent.AnonymousSignInSucceeded(it)) },
                    onFailure = {
                        continueInFlight.set(false)
                        dispatch(
                            WelcomeEvent.AnonymousSignInFailed(
                                it.message ?: "Could not sign in anonymously",
                            ),
                        )
                    },
                )
            }
            is WelcomeEvent.AnonymousSignInSucceeded -> {
                groupRepository.findGroupForUser(event.userId).fold(
                    onSuccess = { dispatch(WelcomeEvent.GroupLookupCompleted(it)) },
                    onFailure = {
                        dispatch(
                            WelcomeEvent.AnonymousSignInFailed(
                                it.message ?: "Could not load your group",
                            ),
                        )
                    },
                )
                continueInFlight.set(false)
            }
            else -> Unit
        }
    }
}
