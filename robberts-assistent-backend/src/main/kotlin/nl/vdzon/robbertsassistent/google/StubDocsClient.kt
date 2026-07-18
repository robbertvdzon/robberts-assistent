package nl.vdzon.robbertsassistent.google

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Stub-docs met vaste inhoud, zodat de DocsTool-keten getest kan worden vóór de echte Google
 * Docs-koppeling er is. Geleverd via [ConditionalOnMissingBean] zodat de echte client (fase 3)
 * de plek overneemt.
 */
@Configuration
class StubDocsClientConfig {
    @Bean
    @ConditionalOnMissingBean(DocsClient::class)
    fun stubDocsClient(): DocsClient = StubDocsClient()
}

class StubDocsClient : DocsClient {
    override fun read(documentId: String): String =
        """
        (stub-document $documentId)
        Wifi-netwerk: Vdzon-Home
        Wifi-wachtwoord: kite-surf-2026
        Router reset: knopje 10s ingedrukt houden tot de lampjes knipperen.
        """.trimIndent()
}
