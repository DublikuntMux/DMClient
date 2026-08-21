package com.dublikunt.dmclient.component

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.dublikunt.dmclient.auth.SessionCookies
import com.dublikunt.dmclient.auth.hasClearance
import com.dublikunt.dmclient.scrapper.NHentaiApi

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NHentaiWebView(onCookiesReceived: (List<Pair<String, String>>) -> Unit) {
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = NHentaiApi.USER_AGENT
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = true
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            fun readCookies(): List<Pair<String, String>> =
                cookieManager.getCookie(NHentaiApi.BASE_URL)
                    ?.split("; ")
                    ?.mapNotNull { cookie ->
                        val parts = cookie.split("=", limit = 2)
                        if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                    }
                    ?: emptyList()

            var lastSent = emptyList<Pair<String, String>>()

            fun checkAndSend() {
                val cookies = readCookies()
                if (!hasClearance(cookies) || cookies == lastSent) return
                lastSent = cookies
                onCookiesReceived(cookies)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.startsWith(NHentaiApi.BASE_URL) == true) {
                        checkAndSend()
                        view?.postDelayed({ checkAndSend() }, 1500)
                        view?.postDelayed({ checkAndSend() }, 4000)
                    }
                }
            }

            listOf(
                SessionCookies.CLEARANCE,
                SessionCookies.CSRF_TOKEN,
                SessionCookies.SESSION_AFFINITY
            ).forEach { name ->
                cookieManager.setCookie(NHentaiApi.BASE_URL, "$name=; Max-Age=0; Path=/")
            }
            cookieManager.flush()

            loadUrl(NHentaiApi.BASE_URL)
        }
    })
}
