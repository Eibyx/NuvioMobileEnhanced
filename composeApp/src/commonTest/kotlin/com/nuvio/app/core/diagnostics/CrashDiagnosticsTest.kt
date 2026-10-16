package com.nuvio.app.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashDiagnosticsTest {
    @Test
    fun crashTextLosesSecretsAndQueryStrings() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.c2lnbmF0dXJlLXRlc3Q"
        val text = """
            java.lang.IllegalStateException: request failed
            URL: https://api.example.test/rest/v1/rpc/x?apikey=$jwt&user=1
            Headers: [Authorization=[Bearer $jwt]]
            at com.nuvio.app.Foo.bar(Foo.kt:12)
        """.trimIndent()
        val clean = sanitizeCrashText(text)
        assertFalse(clean.contains(jwt), clean)
        assertFalse(clean.contains("user=1"), clean)
        assertTrue(clean.contains("https://api.example.test/rest/v1/rpc/x?"))
        assertTrue(clean.contains("at com.nuvio.app.Foo.bar(Foo.kt:12)"))
    }
}
