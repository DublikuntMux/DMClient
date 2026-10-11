package com.dublikunt.dmclient.searchexport

import okhttp3.Dns
import okhttp3.dnsoverhttps.DnsOverHttps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SecureDnsTest {
    @Test
    fun `CLI defaults to Cloudflare`() {
        assertEquals(SecureDns.Cloudflare, parseArgs(emptyArray()).secureDns)
    }

    @Test
    fun `CLI accepts all DNS choices case insensitively`() {
        for (provider in SecureDns.entries) {
            assertEquals(
                provider,
                parseArgs(arrayOf("--secure-dns", provider.name.uppercase())).secureDns
            )
        }
    }

    @Test
    fun `CLI rejects missing and unknown DNS choices`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseArgs(arrayOf("--secure-dns"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseArgs(arrayOf("--secure-dns", "invalid"))
        }
    }

    @Test
    fun `resolvers match app endpoints and off uses system DNS`() {
        assertSame(Dns.SYSTEM, SecureDns.Off.resolver())
        assertEquals(
            "https://cloudflare-dns.com/dns-query",
            (SecureDns.Cloudflare.resolver() as DnsOverHttps).url.toString()
        )
        assertEquals(
            "https://dns.google/dns-query",
            (SecureDns.Google.resolver() as DnsOverHttps).url.toString()
        )
    }
}
