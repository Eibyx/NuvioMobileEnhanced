package com.nuvio.app.core.network

import com.nuvio.app.core.avix.AvixLocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Avix (Yesnt10 port of DnsOverHttpsSettings): optional DNS over HTTPS for addon requests and
 * stream playback. Off by default. The Nuvio account backend keeps the system resolver
 * (DECISIONS D-6). Device-local setting.
 */
enum class DnsOverHttpsProvider(
    val id: String,
    val label: String,
    val endpointUrl: String?,
    val bootstrapIps: List<String>,
) {
    None("none", "", null, emptyList()),
    Cloudflare("cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    Google("google", "Google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    Quad9("quad9", "Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    AdGuard("adguard", "AdGuard", "https://dns.adguard-dns.com/dns-query", listOf("94.140.14.14", "94.140.15.15")),
    DnsSb("dnssb", "DNS.SB", "https://doh.dns.sb/dns-query", listOf("185.222.222.222", "45.11.45.11")),
    ;

    companion object {
        fun fromId(id: String?): DnsOverHttpsProvider = entries.firstOrNull { it.id == id } ?: None
    }
}

object DnsOverHttpsSettingsRepository {
    private const val KEY = "network.doh_provider"
    private val _provider = MutableStateFlow(DnsOverHttpsProvider.None)
    val provider: StateFlow<DnsOverHttpsProvider> = _provider.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _provider.value = DnsOverHttpsProvider.fromId(AvixLocalStore.getString(KEY))
    }

    fun current(): DnsOverHttpsProvider {
        ensureLoaded()
        return _provider.value
    }

    fun setProvider(provider: DnsOverHttpsProvider) {
        ensureLoaded()
        _provider.value = provider
        AvixLocalStore.putString(KEY, provider.id.takeIf { provider != DnsOverHttpsProvider.None })
    }
}
