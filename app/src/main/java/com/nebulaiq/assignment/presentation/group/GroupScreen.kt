package com.nebulaiq.assignment.presentation.group

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.nebulaiq.assignment.domain.model.Group
import com.nebulaiq.assignment.presentation.components.CircleGuardButton
import com.nebulaiq.assignment.presentation.components.CircleGuardScaffold
import com.nebulaiq.assignment.presentation.components.shareInvitationCode
import org.koin.androidx.compose.koinViewModel

private enum class TrackingPermissionStep {
    IDLE,
    WAITING,
    REQUEST_FOREGROUND,
    REQUEST_BACKGROUND,
    REQUEST_NOTIFICATIONS,
    START_TRACKING,
}

@Composable
fun GroupScreen(
    group: Group,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    viewModel: GroupTrackingViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionStep by remember { mutableStateOf(TrackingPermissionStep.IDLE) }
    var permissionRequestInProgress by remember { mutableStateOf(false) }

    val locationSettingsRequest = remember {
        LocationSettingsRequest.Builder()
            .addLocationRequest(
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1_000L,
                ).build(),
            )
            .build()
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun finishPermissionFlowWithError(message: String) {
        permissionStep = TrackingPermissionStep.IDLE
        permissionRequestInProgress = false
        viewModel.dispatch(GroupTrackingEvent.TrackingPermissionFailed(message))
    }

    val enableTrackingAfterSettings: () -> Unit = {
        permissionRequestInProgress = false
        viewModel.dispatch(GroupTrackingEvent.EnableTrackingClicked)
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            enableTrackingAfterSettings()
        } else {
            finishPermissionFlowWithError(
                "Turn on device location services to enable tracking",
            )
        }
    }

    val checkLocationSettingsAndStart: () -> Unit = {
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener {
                enableTrackingAfterSettings()
            }
            .addOnFailureListener { error ->
                if (error is ResolvableApiException) {
                    try {
                        locationSettingsLauncher.launch(
                            IntentSenderRequest.Builder(error.resolution).build(),
                        )
                    } catch (_: IntentSender.SendIntentException) {
                        finishPermissionFlowWithError(
                            "Could not open location settings",
                        )
                    }
                } else {
                    finishPermissionFlowWithError(
                        "Turn on device location services to enable tracking",
                    )
                }
            }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionStep = TrackingPermissionStep.START_TRACKING
        } else {
            finishPermissionFlowWithError("Allow notifications so group members can receive exit alerts")
        }
    }
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionStep = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                TrackingPermissionStep.REQUEST_NOTIFICATIONS
            } else {
                TrackingPermissionStep.START_TRACKING
            }
        } else {
            finishPermissionFlowWithError(
                "Allow background location so CircleGuard can detect an exit when the app is closed",
            )
        }
    }
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val preciseGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!preciseGranted) {
            finishPermissionFlowWithError("Allow Precise location to monitor this boundary")
        } else {
            permissionStep = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> {
                    TrackingPermissionStep.REQUEST_BACKGROUND
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasPermission(Manifest.permission.POST_NOTIFICATIONS) -> {
                    TrackingPermissionStep.REQUEST_NOTIFICATIONS
                }
                else -> TrackingPermissionStep.START_TRACKING
            }
        }
    }

    LaunchedEffect(permissionStep) {
        when (permissionStep) {
            TrackingPermissionStep.REQUEST_FOREGROUND -> {
                permissionStep = TrackingPermissionStep.WAITING
                foregroundLocationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
            TrackingPermissionStep.REQUEST_BACKGROUND -> {
                permissionStep = TrackingPermissionStep.WAITING
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            TrackingPermissionStep.REQUEST_NOTIFICATIONS -> {
                permissionStep = TrackingPermissionStep.WAITING
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            TrackingPermissionStep.START_TRACKING -> {
                permissionStep = TrackingPermissionStep.IDLE
                checkLocationSettingsAndStart()
            }
            TrackingPermissionStep.IDLE,
            TrackingPermissionStep.WAITING,
            -> Unit
        }
    }

    LaunchedEffect(group.id) {
        viewModel.dispatch(GroupTrackingEvent.GroupLoaded(group))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.dispatch(GroupTrackingEvent.ScreenResumed)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startTracking: () -> Unit = {
        permissionRequestInProgress = true
        permissionStep = when {
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                TrackingPermissionStep.REQUEST_FOREGROUND
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> {
                TrackingPermissionStep.REQUEST_BACKGROUND
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS) -> {
                TrackingPermissionStep.REQUEST_NOTIFICATIONS
            }
            else -> TrackingPermissionStep.START_TRACKING
        }
    }

    CircleGuardScaffold(
        title = "Your group",
        modifier = modifier,
        onBack = onBack,
        contentVerticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Your active CircleGuard group",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                GroupDetail(label = "Members", value = group.memberIds.distinct().size.toString())
                GroupDetail(label = "Invitation code", value = group.invitationCode)
                GroupDetail(label = "Boundary radius", value = "${group.radiusMeters.removeTrailingZeros()} m")
                GroupDetail(
                    label = "Boundary center",
                    value = if (group.centerLatitude != null && group.centerLongitude != null) {
                        "Saved from your current location"
                    } else {
                        "Not available"
                    },
                )

                CircleGuardButton(
                    text = "Share invitation code",
                    onClick = { shareInvitationCode(context, group.invitationCode) },
                    outlined = true,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (state.isTrackingEnabled) {
                        "Tracking is active"
                    } else {
                        "Tracking is not enabled"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = if (state.isTrackingEnabled) {
                        "CircleGuard is monitoring this boundary on this device."
                    } else {
                        "Allow background location and notifications to monitor this boundary."
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                CircleGuardButton(
                    text = if (state.isTrackingEnabled) "Stop tracking" else "Enable tracking",
                    onClick = if (state.isTrackingEnabled) {
                        { viewModel.dispatch(GroupTrackingEvent.DisableTrackingClicked) }
                    } else {
                        startTracking
                    },
                    outlined = state.isTrackingEnabled,
                    enabled = !permissionRequestInProgress,
                    loading = state.isLoading || permissionRequestInProgress,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupDetail(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 7.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun Double.removeTrailingZeros(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()
