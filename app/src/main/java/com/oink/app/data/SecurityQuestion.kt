package com.oink.app.data

import java.text.Normalizer
import java.util.Locale

/**
 * A user-authored security question and the hash of its expected answer, used as
 * the last-resort PIN recovery path when the device has neither a biometric nor a
 * lockscreen credential enrolled.
 *
 * The [prompt] is the user's own question text and is not secret, so it is stored
 * in the clear (it must be shown back to them at recovery time). The [answer] is
 * only ever a [PinHasher.HashedPin] over the *normalized* answer - the plaintext
 * answer is never persisted. See [SecurityAnswer] for the normalization.
 */
data class SecurityQuestion(
    val prompt: String,
    val answer: PinHasher.HashedPin
)

/**
 * Hashing and matching for security-question answers.
 *
 * Answers are matched case- and punctuation-insensitively: a stored "St. John's"
 * verifies against a typed "st johns". [normalize] is the single canonical
 * transform applied at both set and verify time, so the two can never drift:
 *
 * 1. Unicode NFKC folding, so visually identical characters compare equal.
 * 2. Lowercase in the root locale (locale-independent, so the result does not
 *    depend on the device's language).
 * 3. Strip Unicode punctuation, making punctuation irrelevant to the match.
 * 4. Collapse runs of whitespace to a single space and trim the ends, so
 *    incidental spacing does not matter.
 *
 * The normalized answer is then stretched with the same salted PBKDF2 as the PIN
 * ([PinHasher]), so a low-entropy answer still costs the full iteration count per
 * guess and two users with the same answer get different stored hashes.
 */
object SecurityAnswer {

    private val punctuation = Regex("\\p{P}+")
    private val whitespace = Regex("\\s+")

    /** The canonical form of [raw] used for both hashing and verification. */
    fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(punctuation, "")
            .replace(whitespace, " ")
            .trim()

    /** Hash the normalized form of [rawAnswer] with a fresh random salt. */
    fun hash(rawAnswer: String): PinHasher.HashedPin = PinHasher.hash(normalize(rawAnswer))

    /** Whether [rawAnswer], once normalized, matches [stored]. Constant-time. */
    fun verify(rawAnswer: String, stored: PinHasher.HashedPin): Boolean =
        PinHasher.verify(normalize(rawAnswer), stored)
}
