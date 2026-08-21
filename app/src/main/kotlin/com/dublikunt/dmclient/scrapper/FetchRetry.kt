package com.dublikunt.dmclient.scrapper

import kotlinx.coroutines.delay

sealed interface AttemptResult<out T> {
    data class Done<T>(val value: T?) : AttemptResult<T>
    data object Retry : AttemptResult<Nothing>
}

internal const val DEFAULT_RETRY_COUNT = 4
private const val BASE_BACKOFF_MS = 1000L

suspend fun <T> withRetries(
    retryCount: Int = DEFAULT_RETRY_COUNT,
    sleep: suspend (Long) -> Unit = { delay(it) },
    log: (String) -> Unit = ::println,
    attempt: () -> AttemptResult<T>,
): T? {
    var currentRetry = 0
    while (currentRetry < retryCount) {
        try {
            when (val result = attempt()) {
                is AttemptResult.Done -> return result.value
                AttemptResult.Retry -> {}
            }
        } catch (e: Exception) {
            if (currentRetry >= retryCount - 1) {
                e.printStackTrace()
                return null
            }
        }
        currentRetry++
        val waitTime = BASE_BACKOFF_MS * currentRetry
        log("Retrying in $waitTime ms...")
        sleep(waitTime)
    }
    return null
}
