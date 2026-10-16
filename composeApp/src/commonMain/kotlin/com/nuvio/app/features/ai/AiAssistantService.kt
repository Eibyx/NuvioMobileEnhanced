package com.nuvio.app.features.ai

import com.nuvio.app.core.logging.SecretRedactor
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AiAssistantException(message: String) : IllegalStateException(message)

data class AiAssistantReply(val answer: String, val sources: List<AiWebSource> = emptyList())

object AiAssistantService {
    /** Sends the conversation to the selected provider only. */
    suspend fun chat(title: AiTitleContext, messages: List<AiChatMessage>): AiAssistantReply {
        AiAssistantSettingsRepository.ensureLoaded()
        val settings = AiAssistantSettingsRepository.settings.value
        if (!settings.isReady) throw AiAssistantException("not_configured")
        val provider = settings.provider
        val apiKey = AiAssistantSettingsRepository.apiKey(provider)?.takeIf { it.isNotBlank() }
            ?: throw AiAssistantException("not_configured")
        val question = messages.lastOrNull { it.role == AiChatRole.USER }?.text
            ?: throw AiAssistantException("empty")
        val web = if (settings.webSearchEnabled) {
            AiAssistantSettingsRepository.tavilyKey()?.takeIf { it.isNotBlank() }?.let { key ->
                runCatching { AiWebSearchService.search(key, title, question) }.getOrNull()
            }
        } else {
            null
        }
        val history = messages.takeLast(MAX_HISTORY_MESSAGES)
        val model = settings.modelFor(provider)
        val prompt = aiSystemPrompt(title, web)
        val response = if (provider.chatEndpoint != null) {
            httpRequestRaw(
                method = "POST",
                url = provider.chatEndpoint,
                headers = buildMap {
                    put("Content-Type", "application/json")
                    put("Authorization", "Bearer $apiKey")
                    if (provider == AiProvider.OPENROUTER) {
                        put("HTTP-Referer", "https://github.com/Eibyx/NuvioMobileEnhanced")
                        put("X-Title", "Nuvio Eibyx")
                    }
                },
                body = buildOpenAiChatBody(model, prompt, history).toString(),
            )
        } else {
            httpRequestRaw(
                method = "POST",
                url = "https://generativelanguage.googleapis.com/v1beta/models/${geminiModelPathSegment(model)}:generateContent",
                headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to apiKey),
                body = buildGeminiBody(prompt, history).toString(),
            )
        }
        if (response.status !in 200..299) {
            throw AiAssistantException(SecretRedactor.redact(aiErrorMessage(response.status, response.body)))
        }
        val answer = if (provider.chatEndpoint != null) parseOpenAiReply(response.body) else parseGeminiReply(response.body)
        return AiAssistantReply(
            answer = answer ?: throw AiAssistantException("empty_response"),
            sources = web?.sources.orEmpty(),
        )
    }
}

private val aiJson = Json { ignoreUnknownKeys = true }

internal fun buildOpenAiChatBody(model: String, systemPrompt: String, messages: List<AiChatMessage>): JsonObject =
    buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            }
            messages.forEach { message ->
                addJsonObject {
                    put("role", if (message.role == AiChatRole.USER) "user" else "assistant")
                    put("content", message.text)
                }
            }
        }
        put("temperature", RESPONSE_TEMPERATURE)
        put("max_tokens", MAX_OUTPUT_TOKENS)
    }

internal fun buildGeminiBody(systemPrompt: String, messages: List<AiChatMessage>): JsonObject =
    buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", systemPrompt) } }
        }
        putJsonArray("contents") {
            messages.forEach { message ->
                addJsonObject {
                    put("role", if (message.role == AiChatRole.USER) "user" else "model")
                    putJsonArray("parts") { addJsonObject { put("text", message.text) } }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", RESPONSE_TEMPERATURE)
            put("maxOutputTokens", MAX_OUTPUT_TOKENS)
        }
    }

/** The Gemini model goes into the URL path: only plain model ids are accepted. */
internal fun geminiModelPathSegment(model: String): String =
    model.trim().removePrefix("models/").takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,80}")) }
        ?: AiProvider.GEMINI.defaultModel

internal fun parseOpenAiReply(body: String): String? = runCatching {
    aiJson.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
}.getOrNull()?.trim()?.takeIf { it.isNotBlank() }

internal fun parseGeminiReply(body: String): String? = runCatching {
    aiJson.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("content")?.jsonObject?.get("parts")?.jsonArray
        ?.joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
}.getOrNull()?.trim()?.takeIf { it.isNotBlank() }

internal fun aiErrorMessage(status: Int, body: String): String {
    val apiMessage = runCatching {
        when (val error = aiJson.parseToJsonElement(body).jsonObject["error"]) {
            is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
            else -> error?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull()?.take(300)
    return apiMessage?.let { "HTTP $status: $it" } ?: "HTTP $status"
}

internal fun aiSystemPrompt(title: AiTitleContext, web: AiWebSearchContext?): String = buildString {
    appendLine("You are the content assistant inside the Nuvio app.")
    appendLine("Reply in the same language as the user. Keep the answer concise and natural.")
    appendLine("Treat only CONTENT DATA and WEB SOURCES below as verified facts about this title.")
    appendLine("Never invent plot points, characters, cast, production details, ratings, or events.")
    appendLine("If the requested fact is absent from both sections, say you could not find reliable information.")
    appendLine("When WEB SOURCES are present, cite factual claims with [1], [2], and so on.")
    appendLine("Do not reveal spoilers unless the user explicitly asks for spoilers.")
    appendLine()
    appendLine("CONTENT DATA")
    appendLine("Title: ${title.name}")
    appendLine("Type: ${title.type}")
    title.releaseInfo?.takeIf { it.isNotBlank() }?.let { appendLine("Release: $it") }
    title.genres.takeIf { it.isNotEmpty() }?.let { appendLine("Genres: ${it.joinToString()}") }
    title.runtime?.takeIf { it.isNotBlank() }?.let { appendLine("Runtime: $it") }
    title.imdbRating?.takeIf { it.isNotBlank() }?.let { appendLine("IMDb rating: $it") }
    title.directors.takeIf { it.isNotEmpty() }?.let { appendLine("Directors: ${it.joinToString()}") }
    title.writers.takeIf { it.isNotEmpty() }?.let { appendLine("Writers: ${it.joinToString()}") }
    title.cast.takeIf { it.isNotEmpty() }?.let { appendLine("Cast: ${it.joinToString()}") }
    title.summary?.takeIf { it.isNotBlank() }?.let { appendLine("Summary: $it") }
    appendLine("END CONTENT DATA")
    web?.let {
        appendLine()
        appendLine("WEB SOURCES")
        appendLine(it.promptContext)
        appendLine("END WEB SOURCES")
    }
}.trimEnd()

private const val MAX_HISTORY_MESSAGES = 8
private const val MAX_OUTPUT_TOKENS = 500
private const val RESPONSE_TEMPERATURE = 0.2
