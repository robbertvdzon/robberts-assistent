package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.Date

/** Alleen AIS-operaties; geen generieke proxy en geen betaal-API. Geen responsebody's loggen. */
class EnableBankingClient(
    private val settings: BankSettings,
    private val mapper: ObjectMapper,
    private val exchange: ((HttpRequest) -> BankHttpReply)? = null,
) : BankingGateway {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build()
    private val privateKey: RSAPrivateKey by lazy {
        val pem = Files.readString(Path.of(settings.privateKeyFile)).replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "").replace(Regex("\\s"), "")
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))) as RSAPrivateKey
    }
    internal fun token(now: Instant = Instant.now()): String {
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(settings.appId).build(),
            JWTClaimsSet.Builder().issuer("enablebanking.com").audience("api.enablebanking.com")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).build())
        jwt.sign(RSASSASigner(privateKey))
        return jwt.serialize()
    }
    private fun request(path: String, body: Any? = null): JsonNode {
        val builder = HttpRequest.newBuilder(URI.create("https://api.enablebanking.com$path"))
            .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer ${token()}").header("Accept", "application/json")
        if (body == null) builder.GET() else builder.header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        val request = builder.build()
        val response = exchange?.invoke(request) ?: http.send(request, HttpResponse.BodyHandlers.ofString()).let {
            BankHttpReply(it.statusCode(), it.body())
        }
        if (response.status !in 200..299) throw BankUnavailableException(response.status)
        return mapper.readTree(response.body)
    }
    override fun start(state: String, accounts: List<String>): String {
        val bank = request("/aspsps?country=NL").path("aspsps").firstOrNull { it.path("name").asText() == "ABN AMRO" }
            ?: throw BankUnavailableException()
        val validity = bank.path("maximum_consent_validity").asLong()
        check(validity > 0)
        return request("/auth", mapOf("access" to buildMap<String, Any> {
            put("valid_until", Instant.now().plusSeconds(validity).toString())
            put("transactions", true)
            if (accounts.isNotEmpty()) put("accounts", accounts.map { mapOf("iban" to it) })
        },
            "aspsp" to mapOf("name" to "ABN AMRO", "country" to "NL"), "state" to state,
            "redirect_url" to settings.redirectUrl, "psu_type" to "personal"))
            .path("url").asText().also {
                val uri = URI.create(it)
                check(uri.scheme == "https" && (uri.host == "enablebanking.com" || uri.host.endsWith(".enablebanking.com")))
            }
    }
    override fun session(code: String): JsonNode = request("/sessions", mapOf("code" to code))
    override fun transactions(uid: String, from: LocalDate, to: LocalDate): List<JsonNode> {
        // Alleen door de provider verstrekte UUID's in het pad; pagination blijft op vaste API-host.
        java.util.UUID.fromString(uid)
        val rows = mutableListOf<JsonNode>()
        val seen = mutableSetOf<String>()
        var continuation = ""
        val deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos()
        do {
            check(System.nanoTime() < deadline) { "Bankimport duurt te lang; niets opgeslagen" }
            val query = "date_from=$from&date_to=$to&transaction_status=BOOK" +
                if (continuation.isBlank()) "" else "&continuation_key=" + URLEncoder.encode(continuation, Charsets.UTF_8)
            val page = request("/accounts/$uid/transactions?$query")
            rows.addAll(page.path("transactions").toList())
            continuation = page.path("continuation_key").asText("")
            check(continuation.isBlank() || (seen.add(continuation) && seen.size < 1000)) { "Onvolledige bankpaginering" }
        } while (continuation.isNotBlank())
        return rows
    }
    override fun check() = request("/application").isObject
}
class BankUnavailableException(val httpStatus: Int? = null) : RuntimeException("Enable Banking is tijdelijk niet beschikbaar of toestemming moet worden vernieuwd.")

data class BankHttpReply(val status: Int, val body: String)
