package com.nuvio.app.core.diagnostics

import coil3.EventListener
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.size.SizeResolver
import kotlinx.atomicfu.atomic

internal class ArtworkCoilEvents(private val loaderId: Long = 0) : EventListener() {
    private val id = ArtworkDiagnostics.nextId()
    private val finished = atomic(false)
    private fun record(name: String, fields: String = "") =
        ArtworkDiagnostics.event(name) { "loader=$loaderId request=$id $fields" }

    override fun onStart(request: ImageRequest) {
        record("coil.start", "resource=${ArtworkDiagnostics.token(request.data)} active=${active.incrementAndGet()} " +
            "memory=${request.memoryCachePolicy} disk=${request.diskCachePolicy}")
    }
    override fun resolveSizeStart(request: ImageRequest, sizeResolver: SizeResolver) { record("coil.size.start") }
    override fun resolveSizeEnd(request: ImageRequest, size: Size) { record("coil.size", "requested=$size") }
    override fun keyEnd(request: ImageRequest, output: String?) { record("coil.key", "key=${ArtworkDiagnostics.token(output)}") }
    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) {
        record("coil.fetch.start", "size=${options.size} scale=${options.scale}")
    }
    override fun fetchEnd(request: ImageRequest, fetcher: Fetcher, options: Options, result: FetchResult?) {
        record("coil.fetch.end", "source=${(result as? SourceFetchResult)?.dataSource ?: "non-source"}")
    }
    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) { record("coil.decode.start") }
    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) {
        record("coil.decode.end", "decoded=${result?.image?.width}x${result?.image?.height} sampled=${result?.isSampled}")
    }
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        finish("coil.success", "source=${result.dataSource} decoded=${result.image.width}x${result.image.height} " +
            "memoryKey=${ArtworkDiagnostics.token(result.memoryCacheKey?.key)} " +
            "memoryExtras=${ArtworkDiagnostics.token(result.memoryCacheKey?.extras?.toSortedMap())} " +
            "diskKey=${ArtworkDiagnostics.token(result.diskCacheKey)}")
    }
    override fun onError(request: ImageRequest, result: ErrorResult) {
        finish("coil.error", "kind=${result.throwable::class.simpleName}")
    }
    override fun onCancel(request: ImageRequest) { finish("coil.cancel") }
    private fun finish(name: String, fields: String = "") {
        if (finished.compareAndSet(false, true)) record(name, "$fields active=${active.decrementAndGet()}")
    }
    companion object { private val active = atomic(0) }
}
