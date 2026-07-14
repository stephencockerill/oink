package com.oink.app.ui.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val PROMPT_TITLE = "Confirm it's you"
private const val PROMPT_SUBTITLE = "Verify to reset your PIN"

/**
 * Remembers a trigger that runs the OS identity check for PIN recovery and calls
 * [onAuthenticated] once, only on success.
 *
 * The check is a biometric or lockscreen-credential prompt. It carries no
 * [BiometricPrompt.CryptoObject]: the PIN is a pure access gate, not an
 * encryption key, so a bare authentication-success signal is all recovery needs.
 * Errors and cancellation are surfaced by the OS dialog itself and intentionally
 * ignored here - the caller simply stays on the locked screen.
 *
 * API differences are hidden from callers:
 * - API 30+: a single prompt allowing `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
 * - API 26-29: the combined authenticator set is unsupported, so a biometric-only
 *   prompt is shown first (with the required negative button); tapping it, or the
 *   absence of any enrolled biometric, falls through to the lockscreen-credential
 *   confirmation via [KeyguardManager.createConfirmDeviceCredentialIntent].
 */
@Composable
fun rememberDeviceAuthLauncher(onAuthenticated: () -> Unit): () -> Unit {
    val activity = LocalContext.current.findFragmentActivity()
    val currentOnAuthenticated by rememberUpdatedState(onAuthenticated)

    val keyguardLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) currentOnAuthenticated()
    }

    return remember(activity, keyguardLauncher) {
        { launchDeviceAuth(activity, keyguardLauncher) { currentOnAuthenticated() } }
    }
}

private fun launchDeviceAuth(
    activity: FragmentActivity,
    keyguardLauncher: ActivityResultLauncher<Intent>,
    onAuthenticated: () -> Unit
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onAuthenticated()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // On pre-30 devices the "Use screen lock" negative button routes to
                // the lockscreen-credential confirmation; every other error (cancel,
                // lockout, no hardware) is already shown by the OS and left as-is.
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    launchKeyguard(activity, keyguardLauncher)
                }
            }
        }
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(PROMPT_TITLE)
                .setSubtitle(PROMPT_SUBTITLE)
                // No negative button: it is illegal alongside DEVICE_CREDENTIAL, and
                // the OS supplies its own "Use PIN/pattern/password" affordance.
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
        )
        return
    }

    val biometricEnrolled = BiometricManager.from(activity)
        .canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    if (biometricEnrolled) {
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(PROMPT_TITLE)
                .setSubtitle(PROMPT_SUBTITLE)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                // Required for a biometric-only prompt; taps route to the keyguard.
                .setNegativeButtonText("Use screen lock")
                .build()
        )
    } else {
        launchKeyguard(activity, keyguardLauncher)
    }
}

@Suppress("DEPRECATION") // The only pre-30 route to a lockscreen-credential check.
private fun launchKeyguard(
    activity: FragmentActivity,
    keyguardLauncher: ActivityResultLauncher<Intent>
) {
    val keyguard = activity.getSystemService(KeyguardManager::class.java) ?: return
    val intent = keyguard.createConfirmDeviceCredentialIntent(PROMPT_TITLE, PROMPT_SUBTITLE)
    if (intent != null) keyguardLauncher.launch(intent)
}

/**
 * Unwrap this context's [ContextWrapper] chain to the hosting [FragmentActivity],
 * which [BiometricPrompt] requires. The Compose `LocalContext` is a wrapper around
 * the Activity, so the cast must walk the base-context chain.
 */
private fun Context.findFragmentActivity(): FragmentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    error("Expected a FragmentActivity in the context chain for BiometricPrompt")
}
