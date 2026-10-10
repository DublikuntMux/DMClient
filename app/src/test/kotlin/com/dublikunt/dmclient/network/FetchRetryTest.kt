package com.dublikunt.dmclient.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class FetchRetryTest {
    @Test
    fun `successful first attempt does not sleep`() = runTest {
        assertEquals("body", withRetries(sleep = { fail("Unexpected sleep") }) { "body" })
    }

    @Test
    fun `IO and server errors retry with backoff`() = runTest {
        val sleeps = mutableListOf<Long>()
        var calls = 0
        val result = withRetries(sleep = { sleeps.add(it) }) {
            when (++calls) {
                1 -> throw IOException("offline"); 2 -> throw ApiException.Http(502); else -> 42
            }
        }
        assertEquals(42, result)
        assertEquals(listOf(1000L, 2000L), sleeps)
    }

    @Test
    fun `rate limiting fails fast`() = runTest {
        val limited = ApiException.RateLimited(retryAfterSeconds = 60)
        var calls = 0
        try {
            withRetries(sleep = { fail("Unexpected sleep") }) { calls++; throw limited }
            fail("Expected failure")
        } catch (error: IOException) {
            assertSame(limited, error)
        }

        assertEquals(1, calls)
    }

    @Test
    fun `exhaustion rethrows the final failure without trailing sleep`() = runTest {
        val sleeps = mutableListOf<Long>()
        val failure = ApiException.Http(503)
        var calls = 0
        try {
            withRetries(retryCount = 3, sleep = { sleeps.add(it) }) { calls++; throw failure }
            fail("Expected failure")
        } catch (error: IOException) {
            assertSame(failure, error)
        }
        assertEquals(3, calls)
        assertEquals(listOf(1000L, 2000L), sleeps)
    }

    @Test
    fun `terminal API failures programmer errors and cancellation do not retry`() = runTest {
        listOf(
            ApiException.NotFound(),
            ApiException.Blocked(),
            ApiException.Parse(Exception()),
            ApiException.Http(400),
            IllegalArgumentException(),
            CancellationException()
        ).forEach { failure ->
            var calls = 0
            try {
                withRetries(sleep = { fail("Unexpected retry") }) { calls++; throw failure }; fail("Expected failure")
            } catch (error: Exception) {
                assertSame(failure, error)
            }
            assertEquals(1, calls)
        }
    }
}
