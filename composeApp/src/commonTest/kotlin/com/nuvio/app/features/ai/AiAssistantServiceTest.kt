package com.nuvio.app.features.ai

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiAssistantServiceTest {
    private val title = AiTitleContext(
        name = "Dune",
        type = "movie",
        releaseInfo = "2021",
        genres = listOf("Sci-Fi"),
        cast = listOf("Timothée Chalamet"),
        summary = "A noble family becomes embroiled in a war.",
    )
    private val conversation = listOf(
        AiChatMessage(AiChatRole.USER, "Is it long?"),
        AiChatMessage(AiChatRole.ASSISTANT, "About 2.5 hours."),
        AiChatMessage(AiChatRole.USER, "Who directed it?"),
    )

    @Test
    fun openAiBodyHasSystemPromptThenConversation() {
        val body = buildOpenAiChatBody("m1", "SYS", conversation)
        assertEquals("m1", body["model"]!!.jsonPrimitive.content)
        val roles = body["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("system", "user", "assistant", "user"), roles)
    }

    @Test
    fun geminiBodyUsesModelRole() {
        val body = buildGeminiBody("SYS", conversation)
        val roles = body["contents"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("user", "model", "user"), roles)
    }

    @Test
    fun repliesAreParsedAndEmptyOnesRejected() {
        assertEquals("Denis Villeneuve.", parseOpenAiReply("""{"choices":[{"message":{"content":" Denis Villeneuve. "}}]}"""))
        assertNull(parseOpenAiReply("""{"choices":[]}"""))
        assertEquals("a\nb", parseGeminiReply("""{"candidates":[{"content":{"parts":[{"text":"a"},{"text":"b"}]}}]}"""))
        assertNull(parseGeminiReply("not json"))
    }

    @Test
    fun promptContainsTitleFactsOnly() {
        val prompt = aiSystemPrompt(title, web = null)
        assertTrue(prompt.contains("Title: Dune"))
        assertTrue(prompt.contains("Cast: Timothée Chalamet"))
        assertFalse(prompt.contains("END WEB SOURCES"))
    }

    @Test
    fun geminiModelCannotInjectPath() {
        assertEquals("gemini-2.5-pro", geminiModelPathSegment("models/gemini-2.5-pro"))
        assertEquals(AiProvider.GEMINI.defaultModel, geminiModelPathSegment("../../v1/files?x=1"))
    }

    @Test
    fun errorMessageKeepsProviderTextOnly() {
        assertEquals("HTTP 401: Invalid API Key", aiErrorMessage(401, """{"error":{"message":"Invalid API Key"}}"""))
        assertEquals("HTTP 500", aiErrorMessage(500, "<html>"))
    }

    @Test
    fun tavilyResultsRequireHttpsAndContent() {
        val context = AiWebSearchService.parseTavilyResults(
            """{"results":[{"title":"A","url":"https://a.test","content":"x"},{"title":"B","url":"http://b.test","content":"y"}]}""",
        )!!
        assertEquals(listOf("https://a.test"), context.sources.map { it.url })
        assertTrue(context.promptContext.startsWith("[1] A"))
    }

    @Test
    fun readinessNeedsEnabledAndKeyForSelectedProvider() {
        assertFalse(AiAssistantSettings(enabled = true, providersWithKey = setOf(AiProvider.GEMINI)).isReady)
        assertTrue(AiAssistantSettings(enabled = true, provider = AiProvider.GEMINI, providersWithKey = setOf(AiProvider.GEMINI)).isReady)
        assertFalse(AiAssistantSettings(enabled = false, providersWithKey = AiProvider.entries.toSet()).isReady)
    }
}
