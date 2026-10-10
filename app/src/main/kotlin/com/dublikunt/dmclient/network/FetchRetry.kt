package com.dublikunt.dmclient.network

import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Retries network failures and 5xx responses with linear backoff. Rate limiting is retried only
 * when [waitForRateLimit] is set (background work), honoring the server's Retry-After delay.
 */
suspend fun <T> withRetries(
    retryCount: Int = 4,
    waitForRateLimit: Boolean = false,
    sleep: suspend (Long) -> Unit = { delay(it) },
    attempt: suspend () -> T,
): T {
    require(retryCount > 0)
    repeat(retryCount) { index ->
        try {
            return attempt()
        } catch (error: IOException) {
            val retryable = when (error) {
                is ApiException.RateLimited -> waitForRateLimit
                is ApiException.Http -> error.code in 500..599
                is ApiException -> false
                else -> true
            }
            if (!retryable || index == retryCount - 1) throw error
            sleep(
                if (error is ApiException.RateLimited) error.retryAfterSeconds.coerceIn(1, 120) * 1000
                else 1000L * (index + 1)
            )
        }
    }
    error("Unreachable")
}
