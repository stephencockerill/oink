package com.oink.app.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Whether the device can authenticate the user via a biometric or its lockscreen
 * credential (PIN / pattern / password) right now.
 *
 * This is the gate for the primary PIN-recovery path: if it holds, the user can
 * prove ownership through the OS auth they already trust and no security question
 * is needed. It is an interface so [com.oink.app.viewmodel.PrivateViewModel] can
 * depend on the capability, not on a [Context], and tests inject a fake.
 *
 * It is re-evaluated at recovery time rather than cached at PIN-setup time,
 * because device capability changes: a user may enrol a fingerprint, or remove
 * their lockscreen, after the PIN was created.
 */
interface DeviceCredentialAvailability {
    /** True if biometric OR the device lockscreen credential can authenticate. */
    fun canAuthenticate(): Boolean
}

/**
 * Production [DeviceCredentialAvailability] backed by [BiometricManager].
 *
 * The check branches on API level because the combined
 * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` authenticator set is not supported
 * below API 30. On API 26-29 the two channels are probed separately:
 * [BiometricManager.canAuthenticate] for biometrics and
 * [KeyguardManager.isDeviceSecure] for a lockscreen credential.
 */
class AndroidDeviceCredentialAvailability(context: Context) : DeviceCredentialAvailability {

    private val appContext = context.applicationContext

    override fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(appContext)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } else {
            val biometricEnrolled = biometricManager.canAuthenticate(BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
            val keyguard = appContext.getSystemService(KeyguardManager::class.java)
            biometricEnrolled || keyguard?.isDeviceSecure == true
        }
    }
}
