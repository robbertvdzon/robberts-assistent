package nl.vdzon.robbertsassistent.google

/**
 * Stub-docs met vaste inhoud, zodat de DocsTool-keten getest kan worden vóór de echte Google
 * Docs-koppeling er is. [GoogleClientsConfig] kiest tussen deze en [GoogleDocsClient].
 */
class StubDocsClient : DocsClient {
    override fun read(documentId: String): String =
        """
        (stub-document $documentId)
        Wifi-netwerk: Vdzon-Home
        Wifi-wachtwoord: kite-surf-2026
        Router reset: knopje 10s ingedrukt houden tot de lampjes knipperen.
        """.trimIndent()
}
