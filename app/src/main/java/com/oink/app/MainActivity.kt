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
import com.oink.app.data.PreferencesRepository
import com.oink.app.ui.navigation.OinkNavHost
import com.oink.app.ui.theme.OinkTheme
import com.oink.app.viewmodel.MainViewModel
import com.oink.app.viewmodel.RewardsViewModel

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
        val preferencesRepository = PreferencesRepository(applicationContext)
        val checkInRepository = CheckInRepository(database.checkInDao(), preferencesRepository)
        val cashOutRepository = CashOutRepository(database.cashOutDao(), checkInRepository, preferencesRepository)

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
                        factory = MainViewModel.Factory(application, checkInRepository, preferencesRepository, cashOutRepository)
                    )

                    val rewardsViewModel: RewardsViewModel = viewModel(
                        factory = RewardsViewModel.Factory(application, cashOutRepository, checkInRepository, preferencesRepository)
                    )

                    // The NavHost handles all screen navigation
                    OinkNavHost(
                        navController = navController,
                        mainViewModel = mainViewModel,
                        rewardsViewModel = rewardsViewModel
                    )
                }
            }
        }
    }
}
