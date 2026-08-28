package com.nebulaiq.assignment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nebulaiq.assignment.domain.model.Group
import com.nebulaiq.assignment.presentation.group.GroupScreen
import com.nebulaiq.assignment.presentation.group.GroupSetupMode
import com.nebulaiq.assignment.presentation.group.GroupSetupScreen
import com.nebulaiq.assignment.presentation.home.HomeScreen
import com.nebulaiq.assignment.presentation.welcome.WelcomeRoute
import com.nebulaiq.assignment.presentation.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    var selectedGroup by remember { mutableStateOf<Group?>(null) }
                    NavHost(
                        navController = navController,
                        startDestination = "welcome",
                    ) {
                        composable("welcome") {
                            WelcomeRoute(
                                onComplete = { existingGroup ->
                                    if (existingGroup != null) {
                                        selectedGroup = existingGroup
                                        navController.navigate("group") {
                                            // Returning users should not revisit the name screen.
                                            popUpTo("welcome") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("home") {
                                            popUpTo("welcome") { inclusive = true }
                                        }
                                    }
                                },
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                onCreateGroup = { navController.navigate("create-group") },
                                onJoinGroup = { navController.navigate("join-group") },
                            )
                        }
                        composable("create-group") {
                            GroupSetupScreen(
                                mode = GroupSetupMode.CREATE,
                                onBack = { navController.popBackStack() },
                                onGroupReady = { group ->
                                    selectedGroup = group
                                    navController.navigate("group") {
                                        popUpTo("home") { inclusive = false }
                                    }
                                },
                            )
                        }
                        composable("join-group") {
                            GroupSetupScreen(
                                mode = GroupSetupMode.JOIN,
                                onBack = { navController.popBackStack() },
                                onGroupReady = { group ->
                                    selectedGroup = group
                                    navController.navigate("group") {
                                        popUpTo("home") { inclusive = false }
                                    }
                                },
                            )
                        }
                        composable("group") {
                            selectedGroup?.let { group ->
                                GroupScreen(
                                    group = group,
                                    onBack = {
                                        if (!navController.popBackStack()) finish()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
