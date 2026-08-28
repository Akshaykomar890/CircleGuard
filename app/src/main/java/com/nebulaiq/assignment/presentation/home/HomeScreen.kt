package com.nebulaiq.assignment.presentation.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nebulaiq.assignment.presentation.components.CircleGuardButton
import com.nebulaiq.assignment.presentation.components.CircleGuardScaffold

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
) {
    CircleGuardScaffold(
        title = "CircleGuard",
        modifier = modifier,
    ) {
        Text(
            text = "Start with your circle",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Create a shared boundary for your group, or join one with an invitation code.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CircleGuardButton(
            text = "Create a group",
            onClick = onCreateGroup,
            modifier = Modifier
                .padding(top = 32.dp),
        )
        Text(
            text = "Set the area and invite your people.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        CircleGuardButton(
            text = "Join a group",
            onClick = onJoinGroup,
            outlined = true,
            modifier = Modifier
                .padding(top = 12.dp),
        )
        Text(
            text = "Already have an invitation code?",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
