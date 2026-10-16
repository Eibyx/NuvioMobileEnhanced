package com.nuvio.app.features.profiles

import com.nuvio.app.core.avix.AvixSecretStore
import com.nuvio.app.features.addons.httpGetText
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Avix (Yesnt10 port of ProfileGifSearchService): GIF search for the custom avatar URL field.
 *
 * Only GIPHY, with an API key the user creates and types in; Avix ships no key (a key compiled into
 * the APK can be extracted) and does not use Yesnt10's DuckDuckGo scraping fallback. The key is
 * kept encrypted on this device only (AvixSecretStore) and is not part of any backup.
 */
data class GifAvatarResult(val id: String, val previewUrl: String, val avatarUrl: String, val title: String)

object GifAvatarSearchSettings {
    /** False where secrets cannot be stored securely (iOS v1): the search entry is hidden. */
    val isAvailable: Boolean get() = AvixSecretStore.isSupported
    private const val KEY = "giphy_api_key"
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _apiKey.value = AvixSecretStore.get(KEY).orEmpty()
    }

    fun setApiKey(value: String) {
        ensureLoaded()
        val trimmed = value.trim()
        _apiKey.value = trimmed
        AvixSecretStore.put(KEY, trimmed.ifBlank { null })
    }
}

object GifAvatarSearchService {
    suspend fun search(query: String, apiKey: String, limit: Int = 24): Result<List<GifAvatarResult>> {
        val url = buildGiphySearchUrl(apiKey, query, limit) ?: return Result.success(emptyList())
        return runCatching { parseGiphyResults(httpGetText(url)) }
    }
}

internal const val GIPHY_SEARCH_ENDPOINT = "https://api.giphy.com/v1/gifs/search"

internal fun buildGiphySearchUrl(apiKey: String, query: String, limit: Int): String? {
    val key = apiKey.trim()
    val q = query.trim()
    if (key.isEmpty() || q.isEmpty()) return null
    return "$GIPHY_SEARCH_ENDPOINT?api_key=${key.encodeURLParameter()}" +
        "&q=${q.encodeURLParameter()}&limit=${limit.coerceIn(1, 50)}&rating=pg-13"
}

private val giphyJson = Json { ignoreUnknownKeys = true }

internal fun parseGiphyResults(payload: String): List<GifAvatarResult> =
    giphyJson.decodeFromString(GiphySearchResponse.serializer(), payload).data.mapNotNull { gif ->
        val avatar = gif.images.fixedWidth?.url?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
        val preview = gif.images.fixedWidthSmall?.url?.takeIf { it.startsWith("https://") } ?: avatar
        GifAvatarResult(id = gif.id, previewUrl = preview, avatarUrl = avatar, title = gif.title)
    }

@Serializable
private data class GiphySearchResponse(val data: List<GiphyGif> = emptyList())

@Serializable
private data class GiphyGif(
    val id: String = "",
    val title: String = "",
    val images: GiphyImages = GiphyImages(),
)

@Serializable
private data class GiphyImages(
    @SerialName("fixed_width") val fixedWidth: GiphyImage? = null,
    @SerialName("fixed_width_small") val fixedWidthSmall: GiphyImage? = null,
)

@Serializable
private data class GiphyImage(val url: String? = null)
