package com.dublikunt.dmclient.searchexport

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

enum class SecureDns {
    Off, Cloudflare, Google;

    fun resolver(): Dns = when (this) {
        Off -> Dns.SYSTEM
        Cloudflare -> doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1")
        Google -> doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4")
    }

    private fun doh(url: String, vararg bootstrapHosts: String): Dns = DnsOverHttps.Builder()
        .client(OkHttpClient())
        .url(url.toHttpUrl())
        .bootstrapDnsHosts(bootstrapHosts.map(InetAddress::getByName))
        .build()
}
