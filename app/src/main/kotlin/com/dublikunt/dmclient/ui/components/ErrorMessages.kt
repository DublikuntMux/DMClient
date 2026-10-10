package com.dublikunt.dmclient.ui.components

import com.dublikunt.dmclient.network.ApiException
import java.io.IOException

/** Short user-facing explanation of a load failure. */
fun Throwable.userMessage(): String = when (this) {
    is ApiException.Blocked -> "nhentai is asking for a browser check. Try again in a moment."
    is ApiException.NotFound -> "This gallery no longer exists."
    is ApiException.Http -> "The server answered with error $code."
    is ApiException.Parse -> "The site returned something unexpected."
    is IOException -> "Check your internet connection and try again."
    else -> message ?: "Unknown error"
}
