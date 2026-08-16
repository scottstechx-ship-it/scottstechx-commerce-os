package com.scottstechx.commerceos.security

import android.util.Log
import java.security.MessageDigest

/**
 * Lightweight defensive utility for input validation and tamper-event
 * logging. Keep this class side-effect-free where possible; the only
 * exception is [logEvent], which writes to Logcat for the dev device
 * and (in release) should be wired to the production incident sink.
 *
 * The auth flow already strips/validates phone + password; this object
 * adds explicit guards against the shapes that historically caused
 * bugs in this codebase:
 *
 *   - price = negative or non-numeric
 *   - stock = negative
 *   - phone = empty / too short
 *   - free-text description = too long to send cleanly
 */
object InputValidator {

    const val MAX_TITLE_LEN = 80
    const val MAX_DESCRIPTION_LEN = 500
    const val MIN_PHONE_LEN = 6
    const val MAX_PRICE_MINOR = 9_999_999_999L  // server is BIGINT, keep sane upper bound
    const val MAX_STOCK = 1_000_000

    sealed class Result {
        object Ok : Result()
        data class Invalid(val reason: String) : Result()
    }

    fun validateTitle(s: String): Result =
        when {
            s.isBlank() -> Result.Invalid("Title cannot be empty")
            s.length > MAX_TITLE_LEN -> Result.Invalid("Title too long (max $MAX_TITLE_LEN)")
            else -> Result.Ok
        }

    fun validateDescription(s: String): Result =
        when {
            s.isBlank() -> Result.Invalid("Description cannot be empty")
            s.length > MAX_DESCRIPTION_LEN -> Result.Invalid(
                "Description too long (max $MAX_DESCRIPTION_LEN)"
            )
            else -> Result.Ok
        }

    fun validatePriceMinor(p: Long): Result =
        when {
            p < 0L -> Result.Invalid("Price cannot be negative")
            p > MAX_PRICE_MINOR -> Result.Invalid("Price out of range")
            else -> Result.Ok
        }

    fun validateStock(s: Int): Result =
        when {
            s < 0 -> Result.Invalid("Stock cannot be negative")
            s > MAX_STOCK -> Result.Invalid("Stock out of range")
            else -> Result.Ok
        }

    fun validatePhone(p: String): Result =
        when {
            p.isBlank() -> Result.Invalid("Phone cannot be empty")
            p.length < MIN_PHONE_LEN -> Result.Invalid("Phone too short")
            p.any { !it.isDigit() && it != '+' && it != '-' && it != ' ' } ->
                Result.Invalid("Phone contains invalid characters")
            else -> Result.Ok
        }
}

/**
 * Lightweight security event sink. The DEBUG tag goes to Logcat; a
 * production app would also fan out to the incident-response channel.
 * Tagged with a tag that's easy to grep for in `adb logcat`.
 */
object SecurityLog {
    private const val TAG = "ScottsTechX/Security"

    fun info(event: String, details: Map<String, String> = emptyMap()) {
        Log.i(TAG, render(event, details))
    }

    fun warn(event: String, details: Map<String, String> = emptyMap()) {
        Log.w(TAG, render(event, details))
    }

    private fun render(event: String, details: Map<String, String>): String {
        if (details.isEmpty()) return event
        return event + " " + details.entries.joinToString(" ") { (k, v) -> "$k=$v" }
    }
}

/**
 * Short-lived helper for hashing stable identifiers (seller IDs, order
 * IDs) so we don't accidentally log PII in plain text. NOT a security
 * boundary — collisions are fine, the use is only to make Logcat
 * unreadable to anyone casually browsing.
 */
object IdHasher {
    fun shortHash(id: String, length: Int = 8): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(id.toByteArray(Charsets.UTF_8))
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return hex.take(length)
    }
}
