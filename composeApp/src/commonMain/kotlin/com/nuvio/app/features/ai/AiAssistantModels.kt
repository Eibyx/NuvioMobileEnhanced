package com.nuvio.app.features.ai

import com.nuvio.app.core.avix.AvixLocalStore
import com.nuvio.app.core.avix.AvixSecretStore
import com.nuvio.app.core.avix.getBoolean
import com.nuvio.app.core.avix.putBoolean
import com.nuvio.app.features.details.MetaDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Avix (Yesnt10 port of features/ai): optional assistant on the details page.
 *
 * Off by default. Avix ships no keys: the user creates an API key with the provider of their
 * choice and types it in; keys are stored encrypted on this device (AvixSecretStore), never synced,
 * logged or exported. Only the selected provider receives the conversation (Yesnt10 silently fell
 * back to every other configured provider).
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    /** OpenAI-compatible chat completions endpoint; null for Gemini. */
    val chatEndpoint: String?,
) {
    GROQ("groq", "Groq", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1/chat/completions"),
    CEREBRAS("cerebras", "Cerebras", "gpt-oss-120b", "https://api.cerebras.ai/v1/chat/completions"),
    GEMINI("gemini", "Gemini", "gemini-2.5-flash", null),
    OPENROUTER("openrouter", "OpenRouter", "openrouter/auto", "https://openrouter.ai/api/v1/chat/completions"),
    ;

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: GROQ
    }
}

data class AiAssistantSettings(
    val enabled: Boolean = false,
    val provider: AiProvider = AiProvider.GROQ,
    val models: Map<AiProvider, String> = emptyMap(),
    val webSearchEnabled: Boolean = false,
    /** Providers with a stored key (the keys themselves are not kept in UI state). */
    val providersWithKey: Set<AiProvider> = emptySet(),
    val hasTavilyKey: Boolean = false,
) {
    fun modelFor(provider: AiProvider): String = models[provider]?.takeIf { it.isNotBlank() } ?: provider.defaultModel
    val isReady: Boolean get() = enabled && provider in providersWithKey
}

enum class AiChatRole { USER, ASSISTANT }

data class AiChatMessage(val role: AiChatRole, val text: String, val sources: List<AiWebSource> = emptyList())

data class AiWebSource(val title: String, val url: String)

/** Facts about the title given to the model; nothing about the user or their account. */
data class AiTitleContext(
    val name: String,
    val type: String,
    val releaseInfo: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    val imdbRating: String? = null,
    val directors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val summary: String? = null,
)

fun MetaDetails.toAiTitleContext(): AiTitleContext = AiTitleContext(
    name = name,
    type = type,
    releaseInfo = releaseInfo,
    genres = genres,
    runtime = runtime,
    imdbRating = imdbRating,
    directors = director,
    writers = writer,
    cast = cast.take(10).map { it.name },
    summary = description,
)

object AiAssistantSettingsRepository {
    private const val KEY_ENABLED = "ai.enabled"
    private const val KEY_PROVIDER = "ai.provider"
    private const val KEY_WEB_SEARCH = "ai.web_search"
    private fun modelKey(provider: AiProvider) = "ai.model.${provider.id}"
    private fun secretKey(provider: AiProvider) = "ai.key.${provider.id}"
    private const val SECRET_TAVILY = "ai.key.tavily"

    private val _settings = MutableStateFlow(AiAssistantSettings())
    val settings: StateFlow<AiAssistantSettings> = _settings.asStateFlow()
    private var loaded = false

    /** The assistant needs secure key storage (Android); hidden elsewhere. */
    val isAvailable: Boolean get() = AvixSecretStore.isSupported

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        reload()
    }

    private fun reload() {
        _settings.value = AiAssistantSettings(
            enabled = isAvailable && (AvixLocalStore.getBoolean(KEY_ENABLED) ?: false),
            provider = AiProvider.fromId(AvixLocalStore.getString(KEY_PROVIDER)),
            models = AiProvider.entries.mapNotNull { p -> AvixLocalStore.getString(modelKey(p))?.let { p to it } }.toMap(),
            webSearchEnabled = AvixLocalStore.getBoolean(KEY_WEB_SEARCH) ?: false,
            providersWithKey = AiProvider.entries.filterTo(mutableSetOf()) { !AvixSecretStore.get(secretKey(it)).isNullOrBlank() },
            hasTavilyKey = !AvixSecretStore.get(SECRET_TAVILY).isNullOrBlank(),
        )
    }

    fun setEnabled(enabled: Boolean) {
        AvixLocalStore.putBoolean(KEY_ENABLED, enabled)
        reload()
    }

    fun setProvider(provider: AiProvider) {
        AvixLocalStore.putString(KEY_PROVIDER, provider.id)
        reload()
    }

    fun setModel(provider: AiProvider, model: String) {
        AvixLocalStore.putString(modelKey(provider), model.trim().ifBlank { null })
        reload()
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        AvixLocalStore.putBoolean(KEY_WEB_SEARCH, enabled)
        reload()
    }

    fun setApiKey(provider: AiProvider, key: String?) {
        AvixSecretStore.put(secretKey(provider), key?.trim()?.ifBlank { null })
        reload()
    }

    fun setTavilyKey(key: String?) {
        AvixSecretStore.put(SECRET_TAVILY, key?.trim()?.ifBlank { null })
        reload()
    }

    internal fun apiKey(provider: AiProvider): String? = AvixSecretStore.get(secretKey(provider))
    internal fun tavilyKey(): String? = AvixSecretStore.get(SECRET_TAVILY)
}
