package com.nuvio.app.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DnsOverHttpsProviderTest {
    @Test
    fun unknownIdsFallBackToSystemDns() {
        assertEquals(DnsOverHttpsProvider.None, DnsOverHttpsProvider.fromId(null))
        assertEquals(DnsOverHttpsProvider.None, DnsOverHttpsProvider.fromId("removed"))
        assertEquals(DnsOverHttpsProvider.Quad9, DnsOverHttpsProvider.fromId("quad9"))
    }

    @Test
    fun everyProviderUsesHttpsAndHasBootstrapAddresses() {
        DnsOverHttpsProvider.entries.filter { it != DnsOverHttpsProvider.None }.forEach { provider ->
            assertTrue(provider.endpointUrl!!.startsWith("https://"), provider.id)
            assertTrue(provider.bootstrapIps.size >= 2, provider.id)
        }
    }
}
