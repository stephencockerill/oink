package com.oink.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.oink.app.ui.screens.CalendarScreen
import com.oink.app.ui.screens.HistoryScreen
import com.oink.app.ui.screens.HomeScreen
import com.oink.app.ui.screens.RewardsScreen
import com.oink.app.ui.screens.SettingsScreen
import com.oink.app.viewmodel.MainViewModel
import com.oink.app.viewmodel.RewardsViewModel

/**
 * Navigation destinations for the app.
 *
 * Using sealed class instead of enum because it's more flexible
 * for future route parameters.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object History : Screen("history")
    data object Calendar : Screen("calendar")
    data object Settings : Screen("settings")
    data object Rewards : Screen("rewards")
}

/**
 * Main navigation host for the app.
 */
@Composable
fun OinkNavHost(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    rewardsViewModel: RewardsViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = mainViewModel,
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToCalendar = {
                    navController.navigate(Screen.Calendar.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToRewards = {
                    navController.navigate(Screen.Rewards.route)
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = mainViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Calendar.route) {
            CalendarScreen(
                viewModel = mainViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Rewards.route) {
            RewardsScreen(
                viewModel = rewardsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

