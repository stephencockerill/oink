package com.oink.app.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes and verifies the private-area PIN.
 *
 * A PIN is never stored in the clear. It is stretched with PBKDF2-HMAC-SHA256
 * over a per-PIN random 16-byte salt, so two identical PINs produce different
 * stored hashes and a brute-force attacker pays the full iteration cost for
 * every guess. Verification recomputes the derived key from the stored salt and
 * iteration count and compares with [MessageDigest.isEqual], whose running time
 * does not depend on where the first differing byte is (constant-time), so a
 * timing side channel cannot leak how close a guess was.
 *
 * This is a pure object with no Android dependencies: it uses [java.util.Base64]
 * and the JDK crypto providers, so it runs in plain JVM unit tests without
 * Robolectric.
 */
object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()

    /**
     * A stored PIN: the Base64 derived key, the Base64 salt it was derived over,
     * and the iteration count used. All three are needed to verify a later guess.
     */
    data class HashedPin(
        val hashBase64: String,
        val saltBase64: String,
        val iterations: Int
    )

    /**
     * Hash [pin] with a fresh random salt at the current iteration count. Two
     * calls for the same PIN return different salts and therefore different
     * hashes, yet both verify against the same PIN.
     */
    fun hash(pin: String): HashedPin {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val derived = hash(pin, salt, ITERATIONS)
        return HashedPin(
            hashBase64 = encode(derived),
            saltBase64 = encode(salt),
            iterations = ITERATIONS
        )
    }

    /**
     * Deterministic PBKDF2 core: derive the key for [pin] over [salt] at
     * [iterations]. Given the same inputs it always returns the same bytes,
     * which is what makes verification possible.
     */
    fun hash(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Whether [pin] matches [stored], recomputing over the stored salt and
     * iteration count. The comparison is constant-time.
     */
    fun verify(pin: String, stored: HashedPin): Boolean {
        val expected = decode(stored.hashBase64)
        val actual = hash(pin, decode(stored.saltBase64), stored.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
