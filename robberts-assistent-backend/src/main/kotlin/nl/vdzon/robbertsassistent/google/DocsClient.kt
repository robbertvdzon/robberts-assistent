package nl.vdzon.robbertsassistent.google

/**
 * Read-only toegang tot een Google Doc. Fase 0 gebruikt [StubDocsClient]; fase 3 vervangt die door
 * een echte Google Docs-implementatie (OAuth refresh-token, scope documents.readonly).
 */
interface DocsClient {
    /** De platte tekst van het document met id [documentId]. */
    fun read(documentId: String): String
}
