package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.google.DocsClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Geeft de chat-assistent read-only toegang tot een Google Doc. Via deze tool test je de docs-
 * koppeling ("zoek X op in mijn google doc"); de assistent leest de tekst en beantwoordt de vraag.
 */
@Component
class DocsTools(private val docsClient: DocsClient) {

    @Tool(
        description = "Lees de volledige (platte) tekst van een Google Doc op basis van het " +
            "document-id (read-only). Gebruik dit om een vraag te beantwoorden uit een van Robberts docs.",
    )
    fun readDoc(
        @ToolParam(description = "Het Google Docs document-id") documentId: String,
    ): String = docsClient.read(documentId)
}
