package com.nuvio.app.features.ai

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Optional Tavily web search (user's own key, off by default) that grounds the answer. */
data class AiWebSearchContext(val promptContext: String, val sources: List<AiWebSource>)

internal object AiWebSearchService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(apiKey: String, title: AiTitleContext, question: String): AiWebSearchContext? {
        if (apiKey.isBlank() || question.trim().length < 8) return null
        val body = buildJsonObject {
            put("query", aiWebSearchQuery(title, question))
            put("search_depth", "basic")
            put("topic", "general")
            put("max_results", MAX_SEARCH_RESULTS)
            put("include_answer", false)
            put("include_raw_content", false)
        }
        val response = httpRequestRaw(
            method = "POST",
            url = "https://api.tavily.com/search",
            headers = mapOf("Content-Type" to "application/json", "Authorization" to "Bearer $apiKey"),
            body = body.toString(),
        )
        if (response.status !in 200..299) return null
        return parseTavilyResults(response.body)
    }

    internal fun parseTavilyResults(body: String): AiWebSearchContext? {
        val results = runCatching {
            json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty().mapNotNull { element ->
                val item = element.jsonObject
                val title = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val url = item["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val content = item["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (title.isBlank() || !url.startsWith("https://") || content.isBlank()) null else Triple(title, url, content)
            }
        }.getOrNull().orEmpty()
        if (results.isEmpty()) return null
        return AiWebSearchContext(
            promptContext = results.mapIndexed { index, (title, url, content) ->
                "[${index + 1}] $title\nURL: $url\n${content.take(MAX_RESULT_CONTENT_LENGTH)}"
            }.joinToString("\n\n"),
            sources = results.map { (title, url, _) -> AiWebSource(title, url) },
        )
    }
}

internal fun aiWebSearchQuery(title: AiTitleContext, question: String): String = buildString {
    append('"').append(title.name).append("\" ")
    title.releaseInfo?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
    append(title.type).append(' ')
    append(question.trim().take(MAX_QUESTION_LENGTH))
}

private const val MAX_SEARCH_RESULTS = 5
private const val MAX_RESULT_CONTENT_LENGTH = 900
private const val MAX_QUESTION_LENGTH = 350
