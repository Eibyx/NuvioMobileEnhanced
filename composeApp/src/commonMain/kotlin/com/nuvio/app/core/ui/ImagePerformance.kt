@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class, coil3.annotation.ExperimentalCoilApi::class)
package com.nuvio.app.core.ui

import androidx.compose.ui.draw.drawWithContent
import coil3.EventListener
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Size
import com.nuvio.app.core.logging.InAppLogger
import kotlin.concurrent.atomics.AtomicInt
import kotlin.time.TimeSource

/** Explicit developer opt-in (debugger: enabled = true). Off in every build by default.
 * Only opaque resource hashes are logged, never URLs, query strings or credentials.
 */
object ImagePerformance {
    @kotlin.concurrent.Volatile var enabled: Boolean = false
    private val origin = TimeSource.Monotonic.markNow()
    private val concurrent = AtomicInt(0)
    fun mark(event: String, resource: String = "", detail: String = "") {
        if (enabled) InAppLogger.debug("Images/Performance", "t=${origin.elapsedNow().inWholeMilliseconds} event=$event resource=${resource.hashCode().toUInt()} $detail")
    }
    val listenerFactory = EventListener.Factory {
        if (!enabled) EventListener.NONE else createImagePerformanceEventListener()
    }
    internal class Trace {
        private var fetch = TimeSource.Monotonic.markNow()
        private var decode = TimeSource.Monotonic.markNow()
        private var counted = false
        fun onStart(request: ImageRequest) {
            counted = true
            mark("coil.start", request.data.toString(), "concurrent=${concurrent.fetchAndAdd(1) + 1}")
        }
        fun resolveSizeEnd(request: ImageRequest, size: Size) = mark("coil.size", request.data.toString(), "size=$size")
        fun keyEnd(request: ImageRequest, output: String?) = mark("coil.key", request.data.toString(), "key=${output?.hashCode()?.toUInt()}")
        fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) { fetch = TimeSource.Monotonic.markNow() }
        fun fetchEnd(request: ImageRequest, fetcher: Fetcher, options: Options, result: FetchResult?) =
            mark("coil.fetch", request.data.toString(), "ms=${fetch.elapsedNow().inWholeMilliseconds}")
        fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) { decode = TimeSource.Monotonic.markNow() }
        fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) =
            mark("coil.decode", request.data.toString(), "ms=${decode.elapsedNow().inWholeMilliseconds}")
        private fun finish(request: ImageRequest, event: String, detail: String = "") {
            if (counted) { concurrent.fetchAndAdd(-1); counted = false }
            mark(event, request.data.toString(), detail)
        }
        fun onSuccess(request: ImageRequest, result: SuccessResult) = finish(request, "coil.success",
            "source=${result.dataSource} decoded=${result.image.width}x${result.image.height} memoryKey=${result.memoryCacheKey?.key?.hashCode()?.toUInt()} diskKey=${result.diskCacheKey?.hashCode()?.toUInt()}")
        fun onError(request: ImageRequest, result: ErrorResult) = finish(request, "coil.error", result.throwable::class.simpleName.orEmpty())
        fun onCancel(request: ImageRequest) = finish(request, "coil.cancel")
    }
}

/** Keeps Coil's HTTP policy unchanged, including no-store and conditional validation. */
internal class ObservedImageCacheStrategy : CacheStrategy {
    private val delegate = CacheControlCacheStrategy()
    override suspend fun read(cacheResponse: NetworkResponse, networkRequest: NetworkRequest, options: Options): CacheStrategy.ReadResult =
        delegate.read(cacheResponse, networkRequest, options)
    override suspend fun write(cacheResponse: NetworkResponse?, networkRequest: NetworkRequest, networkResponse: NetworkResponse, options: Options): CacheStrategy.WriteResult {
        ImagePerformance.mark("http.response", networkRequest.url,
            "status=${networkResponse.code} contentLength=${networkResponse.headers["Content-Length"]} conditional=${cacheResponse != null}")
        return delegate.write(cacheResponse, networkRequest, networkResponse, options)
    }
}

/** CPU draw submission marker; intentionally NOT advertised as a displayed hardware frame. */
@androidx.compose.runtime.Composable
internal fun androidx.compose.ui.Modifier.artworkDrawMarker(resource: String, ready: Boolean): androidx.compose.ui.Modifier {
    if (!ImagePerformance.enabled) return this
    val marker = androidx.compose.runtime.remember(resource, ready) {
        var submitted = false
        androidx.compose.ui.Modifier.drawWithContent {
            drawContent()
            if (ready && !submitted) {
                submitted = true
                ImagePerformance.mark("artwork.drawSubmitted", resource)
            }
        }
    }
    return this.then(marker)
}

internal expect fun createImagePerformanceEventListener(): EventListener
