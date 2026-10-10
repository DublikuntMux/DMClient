package com.dublikunt.dmclient.data.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityTest {
    @Test fun `PIN hashes are salted and verify without storing plaintext`() {
        val first = PinHasher.hash("1234")
        val second = PinHasher.hash("1234")
        assertNotEquals("1234", first.hash)
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
        assertTrue(PinHasher.verify("1234", first))
        assertFalse(PinHasher.verify("4321", first))
        assertFalse(PinHasher.verify("1234", first.copy(hash = "invalid")))
    }

    @Test fun `old short PINs can be hashed during migration`() {
        assertTrue(PinHasher.verify("12", PinHasher.hash("12")))
    }

    @Test fun `five failures lock out for exactly thirty seconds`() {
        var now = 100L
        val attempts = PinAttempts { now }
        repeat(4) { assertTrue(attempts.canAttempt()); attempts.failed() }
        assertTrue(attempts.canAttempt())
        attempts.failed()
        assertEquals(30_100L, attempts.cooldownUntil)
        assertFalse(attempts.canAttempt())
        now = 30_099
        assertFalse(attempts.canAttempt())
        now++
        assertTrue(attempts.canAttempt())
        assertEquals(0, attempts.failedAttempts)
        assertEquals(null, attempts.cooldownUntil)
    }

    @Test fun `successful authentication resets failure count`() {
        val attempts = PinAttempts { 0L }
        repeat(4) { attempts.failed() }
        attempts.reset()
        attempts.failed()
        assertTrue(attempts.canAttempt())
        assertEquals(1, attempts.failedAttempts)
    }

    @Test fun `restored attempts convert wall clock cooldown and clamp clock changes`() {
        var now = 100L
        val attempts = PinAttempts { now }
        attempts.restore(4, null, 1_000_000)
        assertEquals(4, attempts.failedAttempts)
        assertTrue(attempts.canAttempt())

        attempts.restore(5, 1_020_000, 1_000_000)
        assertEquals(20_100L, attempts.cooldownUntil)
        assertFalse(attempts.canAttempt())
        now = 20_100
        assertTrue(attempts.canAttempt())

        attempts.restore(5, 2_000_000, 1_000_000)
        assertEquals(now + 30_000, attempts.cooldownUntil)
        attempts.restore(5, 999_999, 1_000_000)
        assertEquals(0, attempts.failedAttempts)
        assertEquals(null, attempts.cooldownUntil)
        assertTrue(attempts.canAttempt())
    }
}
