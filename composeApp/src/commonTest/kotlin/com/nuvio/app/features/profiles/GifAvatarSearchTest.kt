package com.nuvio.app.features.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GifAvatarSearchTest {
    @Test
    fun urlNeedsKeyAndQueryAndEncodesThem() {
        assertNull(buildGiphySearchUrl(apiKey = "", query = "cat", limit = 24))
        assertNull(buildGiphySearchUrl(apiKey = "k", query = "  ", limit = 24))
        val url = buildGiphySearchUrl(apiKey = "k1", query = "happy cat", limit = 200)!!
        assertTrue(url.startsWith("https://api.giphy.com/v1/gifs/search?api_key=k1&q=happy%20cat"), url)
        assertTrue(url.contains("&limit=50&"), url)
    }

    @Test
    fun parsesHttpsImagesAndSkipsOthers() {
        val payload = """
            {"data":[
              {"id":"a","title":"A","images":{"fixed_width":{"url":"https://media.giphy.com/a.gif"},"fixed_width_small":{"url":"https://media.giphy.com/a_s.gif"}},"extra":1},
              {"id":"b","title":"B","images":{"fixed_width":{"url":"http://insecure/b.gif"}}},
              {"id":"c","title":"C","images":{"fixed_width":{"url":"https://media.giphy.com/c.gif"}}}
            ],"meta":{"status":200}}
        """.trimIndent()
        val results = parseGiphyResults(payload)
        assertEquals(listOf("a", "c"), results.map { it.id })
        assertEquals("https://media.giphy.com/a_s.gif", results[0].previewUrl)
        assertEquals("https://media.giphy.com/c.gif", results[1].previewUrl)
    }
}
