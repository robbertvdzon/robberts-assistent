package nl.vdzon.robbertsassistent.softwarefactory

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dekt de pure `parseStories`/`parseMyActions`-conversie zonder HTTP — geen precedent in deze
 * repo voor het mocken van `java.net.http.HttpClient` (zie o.a. `WindToolsTest`).
 */
class BridgeSoftwareFactoryClientTest {

    @Test
    fun `parseStories leest key, samenvatting, fase en gemerged-status`() {
        val json = jacksonObjectMapper().readTree(
            """
            {
              "issues": [
                {"key": "SF-1", "summary": "Eerste story", "fields": {"storyPhase": "developing", "paused": false, "error": null}},
                {"key": "SF-2", "summary": "Tweede story", "fields": {"storyPhase": "done", "paused": false, "error": null}},
                {"key": "SF-3", "summary": "Derde story", "fields": {"storyPhase": "developing", "paused": true, "error": null}},
                {"key": "SF-4", "summary": "Vierde story", "fields": {"storyPhase": "developing", "paused": false, "error": "boom"}}
              ],
              "mergedStoryKeys": ["SF-2"]
            }
            """.trimIndent(),
        )

        val result = BridgeSoftwareFactoryClient.parseStories(json)

        assertNull(result.error)
        assertEquals(4, result.stories.size)
        assertEquals(false, result.stories[0].merged)
        assertEquals(true, result.stories[1].merged)
        assertEquals(true, result.stories[2].paused)
        assertEquals("boom", result.stories[3].error)
    }

    @Test
    fun `parseMyActions groepeert items per story met storySummary en vraag`() {
        val json = jacksonObjectMapper().readTree(
            """
            {
              "groups": [
                {
                  "storyKey": "SF-1",
                  "storySummary": "Eerste story",
                  "items": [
                    {"question": "Mag ik dit zo mergen?"},
                    {"question": null}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val result = BridgeSoftwareFactoryClient.parseMyActions(json)

        assertNull(result.error)
        assertEquals(2, result.items.size)
        assertEquals("SF-1", result.items[0].storyKey)
        assertEquals("Eerste story", result.items[0].storySummary)
        assertEquals("Mag ik dit zo mergen?", result.items[0].question)
        assertTrue(result.items[1].question == null)
    }
}
