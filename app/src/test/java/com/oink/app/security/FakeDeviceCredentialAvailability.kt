package com.oink.app.security

/**
 * Test double for [DeviceCredentialAvailability] with no Android dependency.
 * Flip [canAuthenticate] to drive the two PIN-recovery branches: device auth vs
 * security question.
 */
class FakeDeviceCredentialAvailability(
    var canAuthenticate: Boolean = true
) : DeviceCredentialAvailability {
    override fun canAuthenticate(): Boolean = canAuthenticate
}
