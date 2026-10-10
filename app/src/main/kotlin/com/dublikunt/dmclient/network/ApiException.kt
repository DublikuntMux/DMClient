package com.dublikunt.dmclient.network

import java.io.IOException

sealed class ApiException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Http(val code: Int) : ApiException("HTTP $code")
    class Blocked : ApiException("The site requires a Cloudflare challenge")
    class Parse(cause: Throwable) : ApiException("Invalid API response", cause)
    class NotFound : ApiException("Gallery not found")
    class RateLimited(val retryAfterSeconds: Long) : ApiException("Too many requests")
}

internal fun isChallenge(body: String): Boolean =
    listOf("cloudflare", "cf-chl-", "challenge-platform", "just a moment", "checking your browser")
        .any { body.contains(it, ignoreCase = true) }
