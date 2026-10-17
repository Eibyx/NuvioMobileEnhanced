package com.nuvio.app.core.poster

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.ErrorResult

/**
 * Coil interceptor that detects failed custom poster loads and retries with the
 * original poster URL stored in [memoryCacheKeyExtras] under [FALLBACK_URL_KEY].
 */
class CustomPosterFallbackInterceptor : Interceptor {

    companion object {
        val REQUEST_FALLBACK_URL = coil3.Extras.Key<String?>(null)
        const val FALLBACK_URL_KEY = "custom_poster_fallback_url"
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val extras = chain.request.memoryCacheKeyExtras
        val requestFallback = chain.request.extras[REQUEST_FALLBACK_URL]
        val hasFallback = requestFallback != null || extras.containsKey(FALLBACK_URL_KEY)

        val result = chain.proceed()

        if (result is ErrorResult && hasFallback) {
            val fallbackUrl = requestFallback ?: extras[FALLBACK_URL_KEY]
            if (!fallbackUrl.isNullOrBlank() && fallbackUrl != chain.request.data.toString()) {
                val fallbackRequest = chain.request.newBuilder()
                    .data(fallbackUrl)
                    .apply { this.extras[REQUEST_FALLBACK_URL] = null }
                    .memoryCacheKey(null as String?)
                    .diskCacheKey(null)
                    .memoryCacheKeyExtras(extras - FALLBACK_URL_KEY)
                    .build()
                return chain.withRequest(fallbackRequest).proceed()
            }
        }

        return result
    }
}
