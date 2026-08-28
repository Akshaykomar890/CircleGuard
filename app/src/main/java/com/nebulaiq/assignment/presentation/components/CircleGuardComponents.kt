package com.nebulaiq.assignment.presentation.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import com.nebulaiq.assignment.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleGuardScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showTopBar: Boolean = true,
    contentVerticalArrangement: Arrangement.Vertical = Arrangement.Center,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (showTopBar) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_back),
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
    ) { contentPadding ->
        CircleGuardScreen(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = contentVerticalArrangement,
            content = content,
        )
    }
}

@Composable
fun CircleGuardScreen(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun CircleGuardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val buttonModifier = modifier
        .fillMaxWidth()
        .height(56.dp)

    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = buttonModifier,
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            CircleGuardButtonContent(text = text, loading = loading)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = buttonModifier,
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            CircleGuardButtonContent(text = text, loading = loading)
        }
    }
}

fun shareInvitationCode(context: Context, invitationCode: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Join my CircleGuard group with invitation code: $invitationCode",
        )
    }
    ContextCompat.startActivity(
        context,
        Intent.createChooser(shareIntent, "Share CircleGuard invitation code"),
        null,
    )
}

@Composable
private fun CircleGuardButtonContent(
    text: String,
    loading: Boolean,
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
    } else {
        Text(text)
    }
}
