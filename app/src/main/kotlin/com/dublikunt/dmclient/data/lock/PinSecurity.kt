package com.dublikunt.dmclient.data.lock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal data class PinCredential(val hash: String, val salt: String)

internal object PinHasher {
    private const val ITERATIONS = 120_000

    fun hash(pin: String): PinCredential {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return PinCredential(
            Base64.getEncoder().encodeToString(derive(pin, salt)),
            Base64.getEncoder().encodeToString(salt)
        )
    }

    fun verify(pin: String, credential: PinCredential): Boolean = try {
        MessageDigest.isEqual(
            Base64.getDecoder().decode(credential.hash),
            derive(pin, Base64.getDecoder().decode(credential.salt))
        )
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

internal class PinAttempts(private val clock: () -> Long) {
    var failedAttempts: Int = 0
        private set
    var cooldownUntil: Long? = null
        private set

    fun canAttempt(): Boolean {
        if (cooldownUntil?.let { clock() >= it } == true) reset()
        return cooldownUntil == null
    }

    fun failed() {
        failedAttempts++
        if (failedAttempts >= 5) cooldownUntil = clock() + 30_000
    }

    fun restore(failures: Int, deadline: Long?, wallClockNow: Long) {
        reset()
        val remaining = deadline?.let { (it - wallClockNow).coerceIn(0, 30_000) }
        if (remaining == 0L) return
        failedAttempts = failures.coerceAtLeast(0)
        cooldownUntil = remaining?.let { clock() + it }
    }

    fun reset() {
        failedAttempts = 0; cooldownUntil = null
    }
}
