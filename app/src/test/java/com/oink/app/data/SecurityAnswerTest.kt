package com.oink.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SecurityAnswer]: answers match case- and punctuation-insensitively,
 * yet a genuinely different answer is rejected, and equal answers still get
 * distinct salted hashes.
 */
class SecurityAnswerTest {

    @Test
    fun `matching ignores case, punctuation, and surrounding whitespace`() {
        val stored = SecurityAnswer.hash("St. John's")
        assertTrue(SecurityAnswer.verify("st johns", stored))
        assertTrue(SecurityAnswer.verify("  ST. JOHN'S  ", stored))
        assertTrue(SecurityAnswer.verify("st. john's", stored))
    }

    @Test
    fun `matching collapses internal whitespace`() {
        val stored = SecurityAnswer.hash("New   York")
        assertTrue(SecurityAnswer.verify("new york", stored))
    }

    @Test
    fun `a different answer is rejected`() {
        val stored = SecurityAnswer.hash("Fido")
        assertFalse(SecurityAnswer.verify("Rex", stored))
        // Punctuation-insensitive must not collapse distinct words together.
        assertFalse(SecurityAnswer.verify("Fido the dog", stored))
    }

    @Test
    fun `equal answers hash to different values via distinct salts`() {
        val first = SecurityAnswer.hash("Fido")
        val second = SecurityAnswer.hash("Fido")
        assertNotEquals(first.hashBase64, second.hashBase64)
        // Both still verify against the same plaintext.
        assertTrue(SecurityAnswer.verify("Fido", first))
        assertTrue(SecurityAnswer.verify("Fido", second))
    }
}
