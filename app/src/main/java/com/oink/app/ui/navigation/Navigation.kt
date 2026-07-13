package com.oink.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.oink.app.AppContainer
import com.oink.app.data.HabitRepository
import com.oink.app.ui.screens.CalendarScreen
import com.oink.app.ui.screens.HabitDetailScreen
import com.oink.app.ui.screens.HabitListScreen
import com.oink.app.ui.screens.HistoryScreen
import com.oink.app.ui.screens.RewardsScreen
import com.oink.app.ui.screens.SettingsScreen
import com.oink.app.viewmodel.HabitListViewModel
import com.oink.app.viewmodel.MainViewModel
import com.oink.app.viewmodel.RewardsViewModel
import com.oink.app.viewmodel.SettingsViewModel

/**
 * Navigation destinations for the app.
 *
 * The per-habit destinations carry the habit id in their path
 * (`habit/{habitId}/...`). Navigation passes that argument into each
 * back-stack entry's [androidx.lifecycle.SavedStateHandle], so a ViewModel
 * scoped to the entry reads it via [MainViewModel.HABIT_ID_KEY] and binds to
 * the right habit. [route] builds a concrete path from an id.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Rewards : Screen("rewards")

    data object HabitDetail : Screen("habit/{$HABIT_ID_ARG}") {
        fun route(habitId: Long) = "habit/$habitId"
    }

    data object HabitHistory : Screen("habit/{$HABIT_ID_ARG}/history") {
        fun route(habitId: Long) = "habit/$habitId/history"
    }

    data object HabitCalendar : Screen("habit/{$HABIT_ID_ARG}/calendar") {
        fun route(habitId: Long) = "habit/$habitId/calendar"
    }

    data object HabitSettings : Screen("habit/{$HABIT_ID_ARG}/settings") {
        fun route(habitId: Long) = "habit/$habitId/settings"
    }

    companion object {
        /**
         * Path argument name for the habit id, kept in sync with the
         * [SavedStateHandle][androidx.lifecycle.SavedStateHandle] key the
         * per-habit ViewModels read.
         */
        const val HABIT_ID_ARG = MainViewModel.HABIT_ID_KEY
    }
}

/**
 * Main navigation host for the app.
 *
 * Takes the [AppContainer] (the repository graph) and hands it to each
 * ViewModel factory. ViewModels are created per back-stack entry via
 * `viewModel(factory = ...)`, so each `habit/{habitId}...` entry gets its own
 * habit-scoped instance and no state leaks between habits.
 */
@Composable
fun OinkNavHost(
    navController: NavHostController,
    container: AppContainer
) {
    val habitIdArguments = listOf(
        navArgument(Screen.HABIT_ID_ARG) { type = NavType.LongType }
    )

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            val habitListViewModel: HabitListViewModel =
                viewModel(factory = HabitListViewModel.provideFactory(container))
            HabitListScreen(
                viewModel = habitListViewModel,
                onHabitClick = { habitId ->
                    navController.navigate(Screen.HabitDetail.route(habitId))
                },
                onNavigateToRewards = {
                    navController.navigate(Screen.Rewards.route)
                },
                // Add-habit is out of scope (#37); no add screen exists yet, so
                // this is a deliberate no-op rather than a dead-end navigation.
                onAddHabit = { /* TODO(#37): add-habit flow */ }
            )
        }

        composable(
            route = Screen.HabitDetail.route,
            arguments = habitIdArguments
        ) {
            val mainViewModel: MainViewModel =
                viewModel(factory = MainViewModel.provideFactory(container))
            val habitId = it.arguments?.getLong(Screen.HABIT_ID_ARG)
                ?: HabitRepository.DEFAULT_HABIT_ID
            HabitDetailScreen(
                viewModel = mainViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHistory = {
                    navController.navigate(Screen.HabitHistory.route(habitId))
                },
                onNavigateToCalendar = {
                    navController.navigate(Screen.HabitCalendar.route(habitId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.HabitSettings.route(habitId))
                },
                onNavigateToRewards = {
                    navController.navigate(Screen.Rewards.route)
                }
            )
        }

        composable(
            route = Screen.HabitHistory.route,
            arguments = habitIdArguments
        ) {
            val mainViewModel: MainViewModel =
                viewModel(factory = MainViewModel.provideFactory(container))
            HistoryScreen(
                viewModel = mainViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.HabitCalendar.route,
            arguments = habitIdArguments
        ) {
            val mainViewModel: MainViewModel =
                viewModel(factory = MainViewModel.provideFactory(container))
            CalendarScreen(
                viewModel = mainViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.HabitSettings.route,
            arguments = habitIdArguments
        ) {
            val settingsViewModel: SettingsViewModel =
                viewModel(factory = SettingsViewModel.provideFactory(container))
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Rewards.route) {
            val rewardsViewModel: RewardsViewModel =
                viewModel(factory = RewardsViewModel.provideFactory(container))
            RewardsScreen(
                viewModel = rewardsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
