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
    data class GroupLookupCompleted(
        val group: Group?,
        val requiresName: Boolean = false,
        val displayName: String = "",
    ) : WelcomeEvent
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
                isComplete = !event.requiresName,
                existingGroup = event.group,
                name = event.displayName.ifBlank { state.name },
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
                    val displayName = authRepository.currentUserDisplayName().orEmpty()
                    groupRepository.findGroupForUser(userId).fold(
                        onSuccess = {
                            dispatch(
                                WelcomeEvent.GroupLookupCompleted(
                                    group = it,
                                    requiresName = displayName.isBlank(),
                                    displayName = displayName,
                                ),
                            )
                        },
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
                val displayName = state.value.name.trim()
                authRepository.updateDisplayName(displayName).fold(
                    onSuccess = {
                        groupRepository.findGroupForUser(event.userId).fold(
                            onSuccess = { group ->
                                if (group == null) {
                                    dispatch(WelcomeEvent.GroupLookupCompleted(null))
                                } else {
                                    groupRepository.updateMemberDisplayName(
                                        groupId = group.id,
                                        userId = event.userId,
                                        displayName = displayName,
                                    ).fold(
                                        onSuccess = { dispatch(WelcomeEvent.GroupLookupCompleted(group)) },
                                        onFailure = {
                                            dispatch(
                                                WelcomeEvent.AnonymousSignInFailed(
                                                    it.message ?: "Could not update your member name",
                                                ),
                                            )
                                        },
                                    )
                                }
                            },
                            onFailure = {
                                dispatch(
                                    WelcomeEvent.AnonymousSignInFailed(
                                        it.message ?: "Could not load your group",
                                    ),
                                )
                            },
                        )
                    },
                    onFailure = {
                        dispatch(
                            WelcomeEvent.AnonymousSignInFailed(
                                it.message ?: "Could not save your name",
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
