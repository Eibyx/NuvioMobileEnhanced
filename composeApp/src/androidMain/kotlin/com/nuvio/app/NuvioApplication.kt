package com.nuvio.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor

/**
 * Custom Application class that implements [SingletonImageLoader.Factory] to guarantee
 * the Coil ImageLoader (with [CustomPosterFallbackInterceptor]) is created before any
 * image request. Without this, Coil's auto-init ContentProvider creates the singleton
 * before MainActivity.onCreate, and interceptors registered later are ignored.
 */
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        com.nuvio.app.core.diagnostics.initializeArtworkDiagnostics(this)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val diagnosticLoaderId = if (com.nuvio.app.core.diagnostics.ArtworkDiagnostics.available)
            com.nuvio.app.core.diagnostics.ArtworkDiagnostics.nextId() else 0L
        return ImageLoader.Builder(context)
            .apply {
                if (com.nuvio.app.core.diagnostics.ArtworkDiagnostics.available) {
                    eventListenerFactory { request ->
                        val tag = request.extras[com.nuvio.app.core.diagnostics.artworkRequestTagKey]
                        if (tag != null && com.nuvio.app.core.diagnostics.ArtworkDiagnostics.enabled)
                            com.nuvio.app.core.diagnostics.ArtworkCoilEvents(diagnosticLoaderId, tag)
                        else coil3.EventListener.NONE
                    }
                }
            }
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                add(CustomPosterFallbackInterceptor())
                add(SvgDecoder.Factory())
                add(
                    if (com.nuvio.app.core.diagnostics.ArtworkDiagnostics.available) {
                        coil3.network.NetworkFetcher.Factory(
                            networkClient = { com.nuvio.app.core.diagnostics.ArtworkDiagnosticNetworkClient() },
                            cacheStrategy = { CacheControlCacheStrategy() },
                        )
                    } else {
                        KtorNetworkFetcherFactory(cacheStrategy = { CacheControlCacheStrategy() })
                    },
                )
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build().also { loader ->
                com.nuvio.app.core.diagnostics.ArtworkDiagnostics.event("loader.created") {
                    "loader=$diagnosticLoaderId memoryLimit=${loader.memoryCache?.maxSize} diskLimit=${loader.diskCache?.maxSize}"
                }
            }
    }
}
