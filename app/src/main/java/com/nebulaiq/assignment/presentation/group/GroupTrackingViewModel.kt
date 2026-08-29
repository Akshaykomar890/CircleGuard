package com.nebulaiq.assignment.presentation.group

import com.nebulaiq.assignment.domain.model.Group
import com.nebulaiq.assignment.domain.repository.GeofenceRepository
import com.nebulaiq.assignment.domain.repository.PushTokenRepository
import com.nebulaiq.assignment.presentation.core.BaseViewModel
import java.util.concurrent.atomic.AtomicBoolean

data class GroupTrackingState(
    val group: Group? = null,
    val isTrackingEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface GroupTrackingEvent {
    data class GroupLoaded(val group: Group) : GroupTrackingEvent
    data object ScreenResumed : GroupTrackingEvent
    data class LocationServicesChanged(val enabled: Boolean) : GroupTrackingEvent
    data object EnableTrackingClicked : GroupTrackingEvent
    data object DisableTrackingClicked : GroupTrackingEvent
    data class TrackingPermissionFailed(val message: String) : GroupTrackingEvent
    data class TrackingOperationSucceeded(val enabled: Boolean) : GroupTrackingEvent
    data class TrackingOperationFailed(val message: String) : GroupTrackingEvent
}

class GroupTrackingViewModel(
    private val geofenceRepository: GeofenceRepository,
    private val pushTokenRepository: PushTokenRepository,
) : BaseViewModel<GroupTrackingState, GroupTrackingEvent, Nothing>(GroupTrackingState()) {
    private val operationInFlight = AtomicBoolean(false)

    override fun reduce(state: GroupTrackingState, event: GroupTrackingEvent): GroupTrackingState =
        when (event) {
            is GroupTrackingEvent.GroupLoaded -> state.copy(
                group = event.group,
                isTrackingEnabled = geofenceRepository.isTrackingRegistered(event.group.id),
                errorMessage = null,
            )
            GroupTrackingEvent.ScreenResumed -> state
            is GroupTrackingEvent.LocationServicesChanged -> if (event.enabled) {
                state
            } else {
                state.copy(
                    isTrackingEnabled = false,
                    errorMessage = "Turn on device location services before enabling tracking",
                )
            }
            GroupTrackingEvent.EnableTrackingClicked,
            GroupTrackingEvent.DisableTrackingClicked,
            -> state.copy(isLoading = true, errorMessage = null)
            is GroupTrackingEvent.TrackingPermissionFailed -> state.copy(
                isLoading = false,
                errorMessage = event.message,
            )
            is GroupTrackingEvent.TrackingOperationSucceeded -> state.copy(
                isLoading = false,
                isTrackingEnabled = event.enabled,
                errorMessage = null,
            )
            is GroupTrackingEvent.TrackingOperationFailed -> state.copy(
                isLoading = false,
                errorMessage = event.message,
            )
        }

    override suspend fun handleSideEffect(event: GroupTrackingEvent) {
        if (event == GroupTrackingEvent.ScreenResumed) {
            dispatch(
                GroupTrackingEvent.LocationServicesChanged(
                    enabled = geofenceRepository.isLocationEnabled(),
                ),
            )
            return
        }
        if (event is GroupTrackingEvent.GroupLoaded) {
            // Token registration is best-effort; tracking itself must remain usable if FCM is offline.
            pushTokenRepository.registerCurrentUserToken()
            return
        }
        if (event !in setOf(
                GroupTrackingEvent.EnableTrackingClicked,
                GroupTrackingEvent.DisableTrackingClicked,
            ) || !operationInFlight.compareAndSet(false, true)
        ) return

        val group = state.value.group
        if (group == null) {
            operationInFlight.set(false)
            dispatch(GroupTrackingEvent.TrackingOperationFailed("Group details are not ready"))
            return
        }

        val result = if (event == GroupTrackingEvent.EnableTrackingClicked) {
            geofenceRepository.register(group).map { true }
        } else {
            geofenceRepository.unregister().map { false }
        }
        result.fold(
            onSuccess = { dispatch(GroupTrackingEvent.TrackingOperationSucceeded(it)) },
            onFailure = {
                dispatch(
                    GroupTrackingEvent.TrackingOperationFailed(
                        it.message ?: "Could not update tracking",
                    ),
                )
            },
        )
        operationInFlight.set(false)
    }
}
