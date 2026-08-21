package com.dublikunt.dmclient.scrapper

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FetchRetryTest {

    private val sleeps = mutableListOf<Long>()

    private val sleeper: suspend (Long) -> Unit = { sleeps.add(it) }

    @Test
    fun `first-attempt success returns immediately with no delay`() = runTest {
        var attempts = 0

        val result = withRetries(retryCount = 4, sleep = sleeper) {
            attempts++
            AttemptResult.Done("body")
        }

        assertEquals("body", result)
        assertEquals(1, attempts)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `rate-limited attempt retries with linear backoff`() = runTest {
        var calls = 0

        val result = withRetries(retryCount = 4, sleep = sleeper) {
            if (++calls == 1) AttemptResult.Retry else AttemptResult.Done("ok")
        }

        assertEquals("ok", result)
        assertEquals(listOf(1000L), sleeps)
    }

    @Test
    fun `thrown exceptions are retried until success`() = runTest {
        var calls = 0

        val result = withRetries(retryCount = 4, sleep = sleeper) {
            if (++calls < 3) throw java.io.IOException("boom")
            AttemptResult.Done(42)
        }

        assertEquals(42, result)
        assertEquals(listOf(1000L, 2000L), sleeps)
    }

    @Test
    fun `exhausted retries give up and report every backoff step`() = runTest {
        val logged = mutableListOf<String>()

        val result = withRetries(retryCount = 3, sleep = sleeper, log = logged::add) {
            AttemptResult.Retry
        }

        assertNull(result)
        assertEquals(listOf(1000L, 2000L, 3000L), sleeps)
        assertEquals(3, logged.size)
    }

    @Test
    fun `terminal null is returned without retrying`() = runTest {
        var attempts = 0

        val result = withRetries(retryCount = 4, sleep = sleeper) {
            attempts++
            AttemptResult.Done<String>(null)
        }

        assertNull(result)
        assertEquals(1, attempts)
        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `exception on the final attempt stops without a trailing delay`() = runTest {
        val result = withRetries<Any?>(retryCount = 2, sleep = sleeper) {
            throw java.io.IOException("always")
        }

        assertNull(result)
        assertEquals(listOf(1000L), sleeps)
    }
}
