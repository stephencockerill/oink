package com.oink.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.oink.app.ui.navigation.OinkNavHost
import com.oink.app.ui.theme.OinkTheme

/**
 * Main entry point for the Oink app.
 *
 * This is where Compose gets wired up with the Activity.
 * enableEdgeToEdge() gives us that modern edge-to-edge look
 * where the app draws behind the status bar and nav bar.
 *
 * The dependency graph lives in [AppContainer] (built by [OinkApplication]);
 * the Activity just hands it to the nav host, and the ViewModel factories pull
 * what they need from it. No repositories or ViewModels are constructed here.
 *
 * It extends [FragmentActivity] (a [androidx.activity.ComponentActivity] subclass,
 * so Compose, edge-to-edge, and navigation are unaffected) because
 * [androidx.biometric.BiometricPrompt], used by the PIN-recovery flow, hosts an
 * invisible fragment and therefore requires a fragment-capable host.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val container = (application as OinkApplication).container

        setContent {
            OinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    OinkNavHost(
                        navController = navController,
                        container = container
                    )
                }
            }
        }
    }
}
