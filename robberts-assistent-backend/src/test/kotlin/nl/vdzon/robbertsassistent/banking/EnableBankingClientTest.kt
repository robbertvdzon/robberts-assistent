package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jose.crypto.RSASSAVerifier
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.LocalDate
import java.util.Base64
import java.net.http.HttpRequest

class EnableBankingClientTest {
    private fun client(exchange: (HttpRequest) -> BankHttpReply): Pair<EnableBankingClient, RSAPublicKey> {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val file = Files.createTempFile("bank-test-key", ".pem")
        Files.writeString(file, "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----")
        file.toFile().deleteOnExit()
        return EnableBankingClient(BankSettings("test-app", file.toString(), "unused", "test@example.invalid", "https://example.invalid/callback"),
            jacksonObjectMapper(), exchange) to (pair.public as RSAPublicKey)
    }
    @Test fun `JWT is kortlevend en ondertekend en pagination blijft op de API host`() {
        val requests = mutableListOf<HttpRequest>()
        val (client, publicKey) = client { request ->
            requests += request
            if (requests.size == 1) BankHttpReply(200, """{"transactions":[{"entry_reference":"one"}],"continuation_key":"page +/&two"}""")
            else BankHttpReply(200, """{"transactions":[{"entry_reference":"two"}]}""")
        }
        val rows = client.transactions("00000000-0000-0000-0000-000000000001", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
        assertEquals(2, rows.size)
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.uri().host == "api.enablebanking.com" && it.method() == "GET" })
        assertTrue(requests[1].uri().rawQuery.contains("continuation_key=page+%2B%2F%26two"))
        val jwt = SignedJWT.parse(requests[0].headers().firstValue("Authorization").get().removePrefix("Bearer "))
        assertTrue(jwt.verify(RSASSAVerifier(publicKey)))
        assertEquals("test-app", jwt.header.keyID)
        assertEquals("enablebanking.com", jwt.jwtClaimsSet.issuer)
        assertEquals(listOf("api.enablebanking.com"), jwt.jwtClaimsSet.audience)
        assertEquals(300000, jwt.jwtClaimsSet.expirationTime.time - jwt.jwtClaimsSet.issueTime.time)
    }
    @Test fun `gericht koppelen vraagt expliciet de gewenste rekeningen aan`() {
        var posted = ""
        val (client, _) = client { request ->
            if (request.uri().path == "/aspsps") BankHttpReply(200, """{"aspsps":[{"name":"ABN AMRO","maximum_consent_validity":3600}]}""")
            else {
                val subscriber = object : java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
                    lateinit var subscription: java.util.concurrent.Flow.Subscription
                    override fun onSubscribe(s: java.util.concurrent.Flow.Subscription) { subscription = s; s.request(Long.MAX_VALUE) }
                    override fun onNext(b: java.nio.ByteBuffer) { posted += java.nio.charset.StandardCharsets.UTF_8.decode(b).toString() }
                    override fun onComplete() {}
                    override fun onError(t: Throwable) { throw t }
                }
                request.bodyPublisher().get().subscribe(subscriber)
                BankHttpReply(200, """{"url":"https://auth.enablebanking.com/ais/start"}""")
            }
        }
        client.start("state", listOf("NL00TEST0000000001", "NL00TEST0000000002"))
        val body = jacksonObjectMapper().readTree(posted)
        assertEquals(2, body.path("access").path("accounts").size())
        assertEquals("NL00TEST0000000002", body.path("access").path("accounts")[1].path("iban").asText())
    }
    @Test fun `herhaalde pagina cursor stopt de import`() {
        val (client, _) = client { BankHttpReply(200, """{"transactions":[],"continuation_key":"same"}""") }
        assertThrows(IllegalStateException::class.java) {
            client.transactions("00000000-0000-0000-0000-000000000001", LocalDate.now(), LocalDate.now())
        }
    }
    @Test fun `provider foutmeldingen lekken geen gegevens naar foutresponse`() {
        val (client, _) = client { BankHttpReply(403, "private account and token details") }
        val exception = assertThrows(BankUnavailableException::class.java) { client.check() }
        assertFalse(exception.message!!.contains("private account"))
    }
}
