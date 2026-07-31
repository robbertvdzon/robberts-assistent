package nl.vdzon.robbertsassistent.watches

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchPageFetcherTest {
    private val fetcher = JdkWatchPageFetcher()

    @Test
    fun `server-html wordt zonder scripts omgezet naar leesbare platte tekst`() {
        val text = fetcher.htmlToText(
            """
            <html><head><style>.verborgen { color: red }</style></head>
            <body><h1>Kaartjes &amp; prijzen</h1><script>steel()</script>
            <p>Vandaag&nbsp;beschikbaar</p><div><strong>Twee</strong> plaatsen</div></body></html>
            """.trimIndent(),
        )

        assertEquals("Kaartjes & prijzen\nVandaag beschikbaar\nTwee plaatsen", text)
        assertFalse(text.contains("steel"))
        assertFalse(text.contains("verborgen"))
    }

    @Test
    fun `platte tekst wordt begrensd`() {
        val text = fetcher.htmlToText("<p>${"x".repeat(JdkWatchPageFetcher.MAX_TEXT_CHARS + 100)}</p>")

        assertEquals(JdkWatchPageFetcher.MAX_TEXT_CHARS, text.length)
        assertTrue(text.all { it == 'x' })
    }
}
