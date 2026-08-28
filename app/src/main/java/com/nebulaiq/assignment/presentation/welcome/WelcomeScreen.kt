package com.nebulaiq.assignment.presentation.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nebulaiq.assignment.presentation.components.CircleGuardButton
import com.nebulaiq.assignment.presentation.components.CircleGuardScaffold
import com.nebulaiq.assignment.presentation.theme.AppTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun WelcomeRoute(
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = koinViewModel(),
    onComplete: (existingGroup: com.nebulaiq.assignment.domain.model.Group?) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.dispatch(WelcomeEvent.ScreenOpened)
    }

    LaunchedEffect(state.isComplete, state.existingGroup) {
        if (state.isComplete) onComplete(state.existingGroup)
    }

    if (state.isInitializing || state.isComplete) {
        CircleGuardScaffold(
            title = "",
            modifier = modifier,
            showTopBar = false,
            contentVerticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = if (state.isComplete) {
                    "Opening your CircleGuard group…"
                } else {
                    "Checking your CircleGuard session…"
                },
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        WelcomeScreen(
            state = state,
            modifier = modifier,
            onNameChanged = { viewModel.dispatch(WelcomeEvent.NameChanged(it)) },
            onContinueClicked = {
                viewModel.dispatch(WelcomeEvent.ContinueClicked)
            },
        )
    }
}

@Composable
fun WelcomeScreen(
    state: WelcomeState,
    modifier: Modifier = Modifier,
    onNameChanged: (String) -> Unit = {},
    onContinueClicked: () -> Unit = {},
) {
    CircleGuardScaffold(
        title = "",
        modifier = modifier,
        showTopBar = false,
        contentVerticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Stay connected to your circle",
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Know when someone leaves the shared area, without sharing continuous location history.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            label = { Text("Your name") },
            singleLine = true,
            isError = state.errorMessage != null,
            supportingText = {
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            },
        )
        CircleGuardButton(
            text = if (state.isComplete) "Signed in" else "Continue",
            onClick = onContinueClicked,
            enabled = !state.isLoading && !state.isComplete,
            modifier = Modifier
                .padding(top = 32.dp),
            loading = state.isLoading,
        )
        if (state.isComplete) {
            Text(
                text = "Your profile is ready. Group setup comes next.",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    AppTheme(dynamicColor = false) {
        WelcomeScreen(state = WelcomeState(name = "Akshay"))
    }
}
