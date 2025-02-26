package com.dublikunt.dmclient.scrapper

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NHentaiWebView(onCookiesReceived: (String, String) -> Unit) {
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = NHentaiApi.USER_AGENT
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    url?.let {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this@apply, true)
                        val cookies = cookieManager.getCookie(it)

                        var session = ""
                        var token = ""

                        cookies?.split("; ")?.forEach { cookie ->
                            val parts = cookie.split("=")
                            if (parts.size == 2) {
                                val name = parts[0].trim()
                                val value = parts[1].trim()
                                if (name == "session-affinity") {
                                    session = value
                                } else if (name == "csrftoken") {
                                    token = value
                                }
                            }
                        }

                        onCookiesReceived(session, token)
                    }
                }
            }
            loadUrl(NHentaiApi.BASE_URL)
        }
    })
}