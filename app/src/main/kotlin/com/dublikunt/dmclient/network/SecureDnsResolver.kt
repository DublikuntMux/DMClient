package com.dublikunt.dmclient.network

import com.dublikunt.dmclient.data.settings.SecureDns
import com.dublikunt.dmclient.data.settings.SettingsRepository
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves hosts through the DNS-over-HTTPS provider chosen in settings, which gets around
 * DNS-level blocking by ISPs. The choice is read on every lookup, so it applies to new connections.
 */
@Singleton
class SecureDnsResolver @Inject constructor(private val settings: SettingsRepository) : Dns {
    private val bootstrapClient = OkHttpClient()
    private val cloudflare by lazy {
        doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1")
    }
    private val google by lazy { doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4") }

    override fun lookup(hostname: String): List<InetAddress> =
        when (settings.settings.value.secureDns) {
            SecureDns.Off -> Dns.SYSTEM
            SecureDns.Cloudflare -> cloudflare
            SecureDns.Google -> google
        }.lookup(hostname)

    private fun doh(url: String, vararg bootstrapHosts: String): Dns = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url(url.toHttpUrl())
        .bootstrapDnsHosts(bootstrapHosts.map(InetAddress::getByName))
        .build()
}
