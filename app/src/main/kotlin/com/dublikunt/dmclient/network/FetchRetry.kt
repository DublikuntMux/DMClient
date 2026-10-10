package com.dublikunt.dmclient.network

import kotlinx.coroutines.delay
import java.io.IOException

/** Retries network failures and 5xx responses with linear backoff; rate limiting fails fast. */
suspend fun <T> withRetries(
    retryCount: Int = 4,
    sleep: suspend (Long) -> Unit = { delay(it) },
    attempt: suspend () -> T,
): T {
    require(retryCount > 0)
    repeat(retryCount) { index ->
        try {
            return attempt()
        } catch (error: IOException) {
            val retryable = when (error) {
                is ApiException.Http -> error.code in 500..599
                is ApiException -> false
                else -> true
            }
            if (!retryable || index == retryCount - 1) throw error
            sleep(1000L * (index + 1))
        }
    }
    error("Unreachable")
}
