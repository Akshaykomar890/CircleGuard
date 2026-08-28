package com.nebulaiq.assignment.presentation.group

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nebulaiq.assignment.presentation.components.CircleGuardButton
import com.nebulaiq.assignment.presentation.components.CircleGuardScaffold
import com.nebulaiq.assignment.presentation.components.shareInvitationCode
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import org.koin.androidx.compose.koinViewModel

@Composable
fun GroupSetupScreen(
    mode: GroupSetupMode,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onGroupReady: (com.nebulaiq.assignment.domain.model.Group) -> Unit = {},
    viewModel: GroupSetupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasPreciseLocationPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val locationSettingsRequest = LocationSettingsRequest.Builder()
        .addLocationRequest(
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1_000L,
            ).build(),
        )
        .build()
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.dispatch(GroupSetupEvent.CaptureLocationClicked)
        } else {
            viewModel.dispatch(
                GroupSetupEvent.CurrentLocationFailed(
                    "Turn on device location services to continue",
                ),
            )
        }
    }
    val checkLocationSettingsAndCapture: () -> Unit = {
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener {
                viewModel.dispatch(GroupSetupEvent.CaptureLocationClicked)
            }
            .addOnFailureListener { error ->
                if (error is ResolvableApiException) {
                    try {
                        locationSettingsLauncher.launch(
                            IntentSenderRequest.Builder(error.resolution).build(),
                        )
                    } catch (_: IntentSender.SendIntentException) {
                        viewModel.dispatch(
                            GroupSetupEvent.CurrentLocationFailed(
                                "Could not open location settings",
                            ),
                        )
                    }
                } else {
                    viewModel.dispatch(
                        GroupSetupEvent.CurrentLocationFailed(
                            "Turn on device location services to continue",
                        ),
                    )
                }
            }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val preciseGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        viewModel.dispatch(GroupSetupEvent.LocationPermissionChanged(preciseGranted))
        if (preciseGranted) checkLocationSettingsAndCapture()
    }

    LaunchedEffect(mode) {
        viewModel.dispatch(GroupSetupEvent.ScreenOpened(mode))
    }

    LaunchedEffect(state.group?.id) {
        state.group?.let(onGroupReady)
    }

    GroupSetupContent(
        state = state,
        modifier = modifier,
        onGroupNameChanged = { viewModel.dispatch(GroupSetupEvent.GroupNameChanged(it)) },
        onRadiusChanged = { viewModel.dispatch(GroupSetupEvent.RadiusChanged(it)) },
        onInvitationCodeChanged = { viewModel.dispatch(GroupSetupEvent.InvitationCodeChanged(it)) },
        onCaptureLocation = {
            if (hasPreciseLocationPermission) {
                checkLocationSettingsAndCapture()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        },
        onSubmit = { viewModel.dispatch(GroupSetupEvent.SubmitClicked) },
        onBack = onBack,
    )
}

@Composable
private fun GroupSetupContent(
    state: GroupSetupState,
    modifier: Modifier = Modifier,
    onGroupNameChanged: (String) -> Unit,
    onRadiusChanged: (String) -> Unit,
    onInvitationCodeChanged: (String) -> Unit,
    onCaptureLocation: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val isCreate = state.mode == GroupSetupMode.CREATE
    val context = LocalContext.current

    CircleGuardScaffold(
        title = if (isCreate) "Create group" else "Join group",
        modifier = modifier,
        onBack = onBack,
    ) {
        Text(
            text = if (isCreate) "Build your shared boundary" else "Join your circle",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (isCreate) {
                "Choose a name and boundary size. We will add your current location next."
            } else {
                "Enter the invitation code shared by your group creator."
            },
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
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
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(20.dp),
            ) {
                if (isCreate) {
                    OutlinedTextField(
                        value = state.groupName,
                        onValueChange = onGroupNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Group name") },
                        readOnly = state.group != null,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.radiusMeters,
                        onValueChange = onRadiusChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        label = { Text("Radius in meters") },
                        supportingText = { Text("Default: 200 meters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        readOnly = state.group != null,
                        singleLine = true,
                    )
                    CircleGuardButton(
                        text = if (state.currentLocation == null) "Use current location" else "Location ready",
                        onClick = onCaptureLocation,
                        outlined = true,
                        enabled = state.currentLocation == null,
                        loading = state.isLocating,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    if (state.currentLocation != null) {
                        Text(
                            text = "Boundary center captured. You can create the group now.",
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = state.invitationCode,
                        onValueChange = onInvitationCodeChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Invitation code") },
                        readOnly = state.group != null,
                        singleLine = true,
                    )
                }
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.group?.let { group ->
                    Text(
                        text = if (isCreate) {
                            "Group created. Share this code: ${group.invitationCode}"
                        } else {
                            "You joined ${group.name}."
                        },
                        modifier = Modifier.padding(top = 20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Saved group details are locked.",
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (isCreate) {
                        CircleGuardButton(
                            text = "Share invitation code",
                            onClick = { shareInvitationCode(context, group.invitationCode) },
                            outlined = true,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                CircleGuardButton(
                    text = if (isCreate) "Create group" else "Join group",
                    onClick = onSubmit,
                    enabled = state.group == null && (!isCreate || state.currentLocation != null),
                    loading = state.isLoading,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}
