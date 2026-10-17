package com.nuvio.app.core.ui

import coil3.EventListener
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.decode.Decoder
import coil3.decode.DecodeResult
import coil3.size.Size

internal actual fun createImagePerformanceEventListener(): EventListener = object : EventListener() {
    private val trace = ImagePerformance.Trace()
    override fun onStart(request: ImageRequest) = trace.onStart(request)
    override fun resolveSizeEnd(request: ImageRequest, size: Size) = trace.resolveSizeEnd(request, size)
    override fun keyEnd(request: ImageRequest, output: String?) = trace.keyEnd(request, output)
    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) = trace.fetchStart(request, fetcher, options)
    override fun fetchEnd(request: ImageRequest, fetcher: Fetcher, options: Options, result: FetchResult?) = trace.fetchEnd(request, fetcher, options, result)
    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) = trace.decodeStart(request, decoder, options)
    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) = trace.decodeEnd(request, decoder, options, result)
    override fun onSuccess(request: ImageRequest, result: SuccessResult) = trace.onSuccess(request, result)
    override fun onError(request: ImageRequest, result: ErrorResult) = trace.onError(request, result)
    override fun onCancel(request: ImageRequest) = trace.onCancel(request)
}
