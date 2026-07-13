package com.oink.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PinHasher] on plain JVM (no Robolectric): it uses only JDK
 * crypto and [java.util.Base64].
 *
 * Focus:
 * - The right PIN verifies and the wrong PIN does not.
 * - Salting is per-hash: two hashes of the same PIN differ yet both verify.
 * - A tampered stored hash fails verification.
 */
class PinHasherTest {

    @Test
    fun `correct pin verifies and wrong pin does not`() {
        val stored = PinHasher.hash("1234")

        assertTrue("The originating PIN must verify", PinHasher.verify("1234", stored))
        assertFalse("A different PIN must not verify", PinHasher.verify("4321", stored))
        assertFalse("A longer PIN must not verify", PinHasher.verify("12345", stored))
    }

    @Test
    fun `two hashes of the same pin use different salts but both verify`() {
        val first = PinHasher.hash("2468")
        val second = PinHasher.hash("2468")

        // Random salt per call, so the salts and derived hashes differ.
        assertNotEquals(first.saltBase64, second.saltBase64)
        assertNotEquals(first.hashBase64, second.hashBase64)

        // Yet the same PIN verifies against either stored hash.
        assertTrue(PinHasher.verify("2468", first))
        assertTrue(PinHasher.verify("2468", second))
    }

    @Test
    fun `tampering with the stored hash fails verification`() {
        val stored = PinHasher.hash("1357")
        val tampered = stored.copy(hashBase64 = flipFirstChar(stored.hashBase64))

        assertFalse("A tampered hash must not verify", PinHasher.verify("1357", tampered))
    }

    @Test
    fun `deterministic core reproduces the same bytes for the same inputs`() {
        val salt = ByteArray(16) { it.toByte() }
        val a = PinHasher.hash("9999", salt, iterations = 10_000)
        val b = PinHasher.hash("9999", salt, iterations = 10_000)
        val differentPin = PinHasher.hash("8888", salt, iterations = 10_000)

        assertTrue("Same pin+salt+iterations must derive identical bytes", a.contentEquals(b))
        assertFalse("A different pin must derive different bytes", a.contentEquals(differentPin))
    }

    @Test
    fun `stored iteration count is used for verification`() {
        val stored = PinHasher.hash("0000")
        // Recompute with a mismatched iteration count: the derived key differs,
        // so verification only succeeds because verify uses the stored count.
        val wrongIterations = stored.copy(iterations = stored.iterations + 1)

        assertTrue(PinHasher.verify("0000", stored))
        assertFalse(PinHasher.verify("0000", wrongIterations))
    }

    private fun flipFirstChar(base64: String): String {
        val first = base64.first()
        val replacement = if (first == 'A') 'B' else 'A'
        return replacement + base64.drop(1)
    }
}
