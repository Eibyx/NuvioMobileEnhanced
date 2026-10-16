package com.nuvio.app.core.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * Resolver for addon and playback clients: the DoH provider chosen in Settings (resolved at
 * lookup time, so a change applies without rebuilding clients), else IPv4-first system DNS.
 * If the DoH lookup fails, the system resolver is used so playback is not blocked.
 */
object AvixDns : Dns {
    private val systemDns = IPv4FirstDns()
    private var cached: Pair<DnsOverHttpsProvider, Dns>? = null

    override fun lookup(hostname: String): List<InetAddress> {
        val provider = DnsOverHttpsSettingsRepository.current()
        if (provider.endpointUrl == null) return systemDns.lookup(hostname)
        val doh = dohFor(provider)
        return runCatching { IPv4FirstDns(doh).lookup(hostname) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: systemDns.lookup(hostname)
    }

    @Synchronized
    private fun dohFor(provider: DnsOverHttpsProvider): Dns {
        cached?.takeIf { it.first == provider }?.let { return it.second }
        val dns = DnsOverHttps.Builder()
            .client(SharedOkHttpClient.base.newBuilder().dns(systemDns).build())
            .url(provider.endpointUrl!!.toHttpUrl())
            .bootstrapDnsHosts(provider.bootstrapIps.map(InetAddress::getByName))
            .build()
        cached = provider to dns
        return dns
    }
}
