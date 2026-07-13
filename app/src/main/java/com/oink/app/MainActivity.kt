package com.oink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckInRepository
import com.oink.app.data.DataStorePreferencesRepository
import com.oink.app.data.DefaultDeductionProvider
import com.oink.app.data.FreezeRepository
import com.oink.app.data.HabitCashOutPreferencesProvider
import com.oink.app.data.HabitRepository
import com.oink.app.data.HabitRewardProvider
import com.oink.app.ui.navigation.OinkNavHost
import com.oink.app.ui.theme.OinkTheme
import com.oink.app.viewmodel.MainViewModel
import com.oink.app.viewmodel.RewardsViewModel
import com.oink.app.viewmodel.SettingsViewModel

/**
 * Main entry point for the Oink app.
 *
 * This is where Compose gets wired up with the Activity.
 * enableEdgeToEdge() gives us that modern edge-to-edge look
 * where the app draws behind the status bar and nav bar.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for that clean, modern look
        enableEdgeToEdge()

        // Get our database from the Application class
        // This is manual DI - if the app grows, consider Hilt
        val database = (application as OinkApplication).database
        val preferencesRepository = DataStorePreferencesRepository(applicationContext)
        val habitRepository = HabitRepository(database.habitDao())
        val freezeRepository = FreezeRepository(database.habitDao(), database.frozenDayDao())
        // Cash-out balance math is single-habit for now: freeze spending is
        // sourced from the default habit.
        val cashOutPreferencesProvider = HabitCashOutPreferencesProvider(freezeRepository)
        val checkInRepository = CheckInRepository(
            database.checkInDao(),
            HabitRewardProvider(database.habitDao()),
            DefaultDeductionProvider(
                database.cashOutDao(),
                database.cashOutAllocationDao(),
                freezeRepository
            )
        )
        val cashOutRepository = CashOutRepository(
            database.cashOutDao(),
            database.cashOutAllocationDao(),
            checkInRepository,
            cashOutPreferencesProvider
        )

        setContent {
            OinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Set up navigation
                    val navController = rememberNavController()

                    // Create ViewModels with our factories
                    val mainViewModel: MainViewModel = viewModel(
                        factory = MainViewModel.Factory(application, checkInRepository, habitRepository, cashOutRepository, freezeRepository)
                    )

                    val rewardsViewModel: RewardsViewModel = viewModel(
                        factory = RewardsViewModel.Factory(application, cashOutRepository, checkInRepository, freezeRepository)
                    )

                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(application, preferencesRepository, habitRepository, freezeRepository)
                    )

                    // The NavHost handles all screen navigation
                    OinkNavHost(
                        navController = navController,
                        mainViewModel = mainViewModel,
                        rewardsViewModel = rewardsViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
