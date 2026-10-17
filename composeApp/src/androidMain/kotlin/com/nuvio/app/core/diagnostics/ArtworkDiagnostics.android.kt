package com.nuvio.app.core.diagnostics

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.ktor3.asNetworkClient
import io.ktor.client.HttpClient
import kotlinx.atomicfu.atomic

internal fun initializeArtworkDiagnostics(context: Context) {
    val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    val debug = info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    ArtworkDiagnostics.configure(available = debug && info.metaData?.getBoolean("com.eibyx.nuvio.ARTWORK_DIAGNOSTICS", false) == true)
}

/** Uses precisely Coil's default Ktor HttpClient. Passes responses through without reading,
 * buffering or changing bodies/headers. Declared length is NOT measured bytes transferred. */
internal class ArtworkDiagnosticNetworkClient(
    private val delegate: NetworkClient = HttpClient().asNetworkClient(),
) : NetworkClient {
    override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
        val tag = request.extras[artworkRequestTagKey]
        if (!ArtworkDiagnostics.enabled || tag == null) return delegate.executeRequest(request, block)
        val epoch = ArtworkDiagnostics.epoch()
        val id = ArtworkDiagnostics.nextId()
        val key = ArtworkDiagnostics.token(request.url)
        val concurrent = active.incrementAndGet()
        ArtworkDiagnostics.event("http.start") { "http=$id load=${tag.loadId} identity=${tag.identity} purpose=${ArtworkDiagnostics.purpose(tag)} resource=$key active=$concurrent " +
            "conditional=${request.headers["If-None-Match"] != null || request.headers["If-Modified-Since"] != null}" }
        try {
            return delegate.executeRequest(request) { response ->
                if (epoch == ArtworkDiagnostics.epoch()) ArtworkDiagnostics.event("http.headers") {
                    "http=$id load=${tag.loadId} status=${response.code} declaredBytes=${response.headers["Content-Length"]?.toLongOrNull()} " +
                        "etagPresent=${response.headers["ETag"] != null} lastModifiedPresent=${response.headers["Last-Modified"] != null} " +
                        "noStore=${response.headers["Cache-Control"]?.contains("no-store", ignoreCase = true)} " +
                        "noCache=${response.headers["Cache-Control"]?.contains("no-cache", ignoreCase = true)} " +
                        "maxAge=${Regex("(?:^|,)\\s*max-age=(\\d+)").find(response.headers["Cache-Control"].orEmpty())?.groupValues?.get(1)?.toLongOrNull()}"
                }
                block(response)
            }
        } finally {
            val remaining = active.decrementAndGet()
            if (epoch == ArtworkDiagnostics.epoch()) ArtworkDiagnostics.event("http.end") { "http=$id load=${tag.loadId} active=$remaining" }
        }
    }
    companion object { private val active = atomic(0) }
}
