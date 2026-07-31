package nl.vdzon.robbertsassistent.weather

import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession

/**
 * Minimale testdouble voor [HttpClient]: geeft per aanroep het volgende geplande antwoord terug en
 * telt het aantal `send`-aanroepen. Zo zijn retry-, cache- en last-known-good-gedrag van
 * [ForecastFetcher] te testen zonder netwerk en zonder extra testdependency.
 *
 * [responses] wordt van voor naar achter afgelopen; is de lijst op, dan wordt het laatste antwoord
 * herhaald. Een `Throws`-antwoord simuleert een netwerk-/IO-fout.
 */
internal class FakeHttpClient(private val responses: List<Reply>) : HttpClient() {

    constructor(vararg responses: Reply) : this(responses.toList())

    sealed interface Reply {
        data class Status(val code: Int, val body: String = "") : Reply
        data class Throws(val error: Exception) : Reply
    }

    var calls = 0
        private set

    override fun <T : Any?> send(request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
        val reply = responses[minOf(calls, responses.lastIndex)]
        calls++
        return when (reply) {
            is Reply.Throws -> throw reply.error
            is Reply.Status -> {
                @Suppress("UNCHECKED_CAST")
                FakeResponse(request.uri(), reply.code, reply.body) as HttpResponse<T>
            }
        }
    }

    private class FakeResponse(
        private val uri: URI,
        private val status: Int,
        private val body: String,
    ) : HttpResponse<String> {
        override fun statusCode() = status
        override fun request(): HttpRequest = HttpRequest.newBuilder(uri).GET().build()
        override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()
        override fun headers(): HttpHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }
        override fun body(): String = body
        override fun sslSession(): Optional<SSLSession> = Optional.empty()
        override fun uri(): URI = uri
        override fun version(): Version = Version.HTTP_1_1
    }

    // --- Niet gebruikt in tests, maar verplicht door de abstracte HttpClient-API ---

    override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()
    override fun connectTimeout(): Optional<Duration> = Optional.empty()
    override fun followRedirects(): Redirect = Redirect.NEVER
    override fun proxy(): Optional<ProxySelector> = Optional.empty()
    override fun sslContext(): SSLContext = SSLContext.getDefault()
    override fun sslParameters(): SSLParameters = SSLParameters()
    override fun authenticator(): Optional<Authenticator> = Optional.empty()
    override fun version(): Version = Version.HTTP_1_1
    override fun executor(): Optional<Executor> = Optional.empty()

    override fun <T : Any?> sendAsync(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>,
    ): CompletableFuture<HttpResponse<T>> = CompletableFuture.supplyAsync { send(request, responseBodyHandler) }

    override fun <T : Any?> sendAsync(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>,
        pushPromiseHandler: HttpResponse.PushPromiseHandler<T>?,
    ): CompletableFuture<HttpResponse<T>> = sendAsync(request, responseBodyHandler)
}
