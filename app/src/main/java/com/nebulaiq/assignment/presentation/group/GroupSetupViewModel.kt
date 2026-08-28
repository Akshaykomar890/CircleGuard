package com.nebulaiq.assignment.presentation.group

import com.google.firebase.auth.FirebaseAuth
import com.nebulaiq.assignment.domain.model.Group
import com.nebulaiq.assignment.domain.model.GeoPoint
import com.nebulaiq.assignment.domain.repository.GroupRepository
import com.nebulaiq.assignment.domain.repository.LocationRepository
import com.nebulaiq.assignment.presentation.core.BaseViewModel
import java.util.concurrent.atomic.AtomicBoolean

enum class GroupSetupMode {
    CREATE,
    JOIN,
}

data class GroupSetupState(
    val mode: GroupSetupMode = GroupSetupMode.CREATE,
    val groupName: String = "",
    val radiusMeters: String = "200",
    val invitationCode: String = "",
    val currentLocation: GeoPoint? = null,
    val isLocating: Boolean = false,
    val isLoading: Boolean = false,
    val group: Group? = null,
    val errorMessage: String? = null,
)

sealed interface GroupSetupEvent {
    data class ScreenOpened(val mode: GroupSetupMode) : GroupSetupEvent
    data class GroupNameChanged(val value: String) : GroupSetupEvent
    data class RadiusChanged(val value: String) : GroupSetupEvent
    data class InvitationCodeChanged(val value: String) : GroupSetupEvent
    data object CaptureLocationClicked : GroupSetupEvent
    data class LocationPermissionChanged(val granted: Boolean) : GroupSetupEvent
    data class CurrentLocationSucceeded(val location: GeoPoint) : GroupSetupEvent
    data class CurrentLocationFailed(val message: String) : GroupSetupEvent
    data object SubmitClicked : GroupSetupEvent
    data class GroupOperationSucceeded(val group: Group) : GroupSetupEvent
    data class GroupOperationFailed(val message: String) : GroupSetupEvent
}

class GroupSetupViewModel(
    private val groupRepository: GroupRepository,
    private val locationRepository: LocationRepository,
) : BaseViewModel<GroupSetupState, GroupSetupEvent, Nothing>(GroupSetupState()) {
    private val submitInFlight = AtomicBoolean(false)

    override fun reduce(state: GroupSetupState, event: GroupSetupEvent): GroupSetupState =
        when (event) {
            is GroupSetupEvent.ScreenOpened -> state.copy(
                mode = event.mode,
                errorMessage = null,
                group = null,
                currentLocation = null,
                isLocating = false,
            )
            is GroupSetupEvent.GroupNameChanged -> state.copy(
                groupName = event.value,
                errorMessage = null,
            )
            is GroupSetupEvent.RadiusChanged -> state.copy(
                radiusMeters = event.value,
                errorMessage = null,
            )
            is GroupSetupEvent.InvitationCodeChanged -> state.copy(
                invitationCode = event.value
                    .filter { it.isLetterOrDigit() }
                    .uppercase()
                    .take(INVITATION_CODE_LENGTH),
                errorMessage = null,
            )
            GroupSetupEvent.CaptureLocationClicked -> state.copy(
                isLocating = true,
                errorMessage = null,
            )
            is GroupSetupEvent.LocationPermissionChanged -> state.copy(
                errorMessage = if (event.granted) {
                    null
                } else {
                    "Precise location is required. Choose Precise in the permission dialog or device settings"
                },
            )
            is GroupSetupEvent.CurrentLocationSucceeded -> state.copy(
                isLocating = false,
                currentLocation = event.location,
                errorMessage = null,
            )
            is GroupSetupEvent.CurrentLocationFailed -> state.copy(
                isLocating = false,
                errorMessage = event.message,
            )
            GroupSetupEvent.SubmitClicked -> {
                when {
                    state.mode == GroupSetupMode.CREATE && !state.groupName.isValidGroupName() -> {
                        state.copy(errorMessage = "Enter a group name between 2 and 50 characters")
                    }
                    state.mode == GroupSetupMode.CREATE && !state.radiusMeters.isValidRadius() -> {
                        state.copy(errorMessage = "Enter a radius greater than 0 meters")
                    }
                    state.mode == GroupSetupMode.JOIN && !state.invitationCode.isValidInvitationCode() -> {
                        state.copy(errorMessage = "Enter the 6-character invitation code")
                    }
                    else -> state.copy(isLoading = true, errorMessage = null)
                }
            }
            is GroupSetupEvent.GroupOperationSucceeded -> state.copy(
                isLoading = false,
                group = event.group,
                errorMessage = null,
            )
            is GroupSetupEvent.GroupOperationFailed -> state.copy(
                isLoading = false,
                errorMessage = event.message,
            )
        }

    override suspend fun handleSideEffect(event: GroupSetupEvent) {
        if (event == GroupSetupEvent.CaptureLocationClicked) {
            locationRepository.getCurrentLocation().fold(
                onSuccess = { dispatch(GroupSetupEvent.CurrentLocationSucceeded(it)) },
                onFailure = {
                    dispatch(
                        GroupSetupEvent.CurrentLocationFailed(
                            it.message ?: "Could not determine your current location",
                        ),
                    )
                },
            )
            return
        }
        if (event != GroupSetupEvent.SubmitClicked || !state.value.isLoading) return
        if (!submitInFlight.compareAndSet(false, true)) return

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            submitInFlight.set(false)
            dispatch(GroupSetupEvent.GroupOperationFailed("Please return to the welcome screen and sign in again"))
            return
        }

        val currentState = state.value
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "CircleGuard member"
        val existingGroupResult = groupRepository.findGroupForUser(user.uid)
        if (existingGroupResult.isFailure) {
            submitInFlight.set(false)
            dispatch(
                GroupSetupEvent.GroupOperationFailed(
                    existingGroupResult.exceptionOrNull()?.message
                        ?: "Could not check your existing group",
                ),
            )
            return
        }

        val existingGroup = existingGroupResult.getOrNull()
        if (existingGroup != null &&
            (currentState.mode == GroupSetupMode.CREATE ||
                existingGroup.invitationCode != currentState.invitationCode.trim().uppercase())
        ) {
            submitInFlight.set(false)
            dispatch(
                GroupSetupEvent.GroupOperationFailed(
                    "You already belong to an active group: ${existingGroup.name}",
                ),
            )
            return
        }

        val center = currentState.currentLocation
        if (currentState.mode == GroupSetupMode.CREATE && center == null) {
            submitInFlight.set(false)
            dispatch(
                GroupSetupEvent.GroupOperationFailed("Capture your current location first"),
            )
            return
        }

        val result = when (currentState.mode) {
            GroupSetupMode.CREATE -> groupRepository.createGroup(
                name = currentState.groupName,
                radiusMeters = currentState.radiusMeters.toDouble(),
                center = center!!,
                userId = user.uid,
                displayName = displayName,
            )
            GroupSetupMode.JOIN -> groupRepository.joinGroup(
                invitationCode = currentState.invitationCode,
                userId = user.uid,
                displayName = displayName,
            )
        }

        result.fold(
            onSuccess = { dispatch(GroupSetupEvent.GroupOperationSucceeded(it)) },
            onFailure = {
                dispatch(
                    GroupSetupEvent.GroupOperationFailed(
                        it.message ?: "Could not update the group",
                    ),
                )
            },
        )
        submitInFlight.set(false)
    }

    private fun String.isValidRadius(): Boolean =
        toDoubleOrNull()?.isFinite() == true && toDouble() > 0.0

    private fun String.isValidGroupName(): Boolean =
        trim().length in MIN_GROUP_NAME_LENGTH..MAX_GROUP_NAME_LENGTH

    private fun String.isValidInvitationCode(): Boolean =
        matches(INVITATION_CODE_PATTERN)

    private companion object {
        const val INVITATION_CODE_LENGTH = 6
        const val MIN_GROUP_NAME_LENGTH = 2
        const val MAX_GROUP_NAME_LENGTH = 50
        val INVITATION_CODE_PATTERN = Regex("[A-HJ-NP-Z2-9]{6}")
    }
}
