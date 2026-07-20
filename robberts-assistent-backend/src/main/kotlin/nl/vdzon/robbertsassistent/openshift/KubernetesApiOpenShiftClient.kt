package nl.vdzon.robbertsassistent.openshift

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Echte implementatie: praat met de Kubernetes-API-server via de in-cluster ServiceAccount van de
 * pod zelf — token + CA-cert worden door Kubernetes automatisch op elke pod gemount, dus geen los
 * secret nodig. Leest het token bij elke aanroep opnieuw in (kubelet roteert het projected
 * token-bestand in place, dus cachen zou op termijn een verlopen token gebruiken).
 *
 * Vereist RBAC (ServiceAccount + ClusterRole met `get`/`list` op `clusterversions` en
 * `clusteroperators` in `config.openshift.io`) die nog niet in de cluster bestaat — zie
 * `docs/nightly-checks.md`. Tot die tijd geeft elke aanroep een nette 403-gebaseerde foutmelding
 * terug (geen crash) via [clusterHealth]'s `error`-veld.
 */
class KubernetesApiOpenShiftClient(
    private val tokenFile: Path = Path.of(TOKEN_PATH),
    private val caCertFile: Path = Path.of(CA_CERT_PATH),
    private val apiServerUrl: String = defaultApiServerUrl(),
) : OpenShiftClient {

    private val objectMapper = jacksonObjectMapper()
    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder().sslContext(buildSslContext(caCertFile)).build()
    }

    override fun clusterHealth(): ClusterHealthResult =
        runCatching {
            val clusterVersion = get("/apis/config.openshift.io/v1/clusterversions/version")
            val clusterOperators = get("/apis/config.openshift.io/v1/clusteroperators")
            parseClusterHealth(clusterVersion, clusterOperators)
        }.getOrElse { ClusterHealthResult(false, null, false, emptyList(), it.message ?: "Onbekende fout") }

    private fun get(path: String): JsonNode {
        val token = Files.readString(tokenFile).trim()
        val request = HttpRequest.newBuilder(URI.create("$apiServerUrl$path"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("Kubernetes-API gaf HTTP ${response.statusCode()} terug voor $path.")
        }
        return objectMapper.readTree(response.body())
    }

    private fun buildSslContext(caCertFile: Path): SSLContext {
        val cert = Files.newInputStream(caCertFile).use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("kube-ca", cert)
        }
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        return SSLContext.getInstance("TLS").apply { init(null, trustManagerFactory.trustManagers, null) }
    }

    internal companion object {
        const val TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token"
        const val CA_CERT_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"

        /** Kubernetes zet deze env-vars automatisch op elke pod. */
        fun defaultApiServerUrl(): String {
            val host = System.getenv("KUBERNETES_SERVICE_HOST") ?: "kubernetes.default.svc"
            val port = System.getenv("KUBERNETES_SERVICE_PORT_HTTPS") ?: System.getenv("KUBERNETES_SERVICE_PORT") ?: "443"
            return "https://$host:$port"
        }

        /** Zet de ruwe ClusterVersion- + ClusterOperators-JSON om naar [ClusterHealthResult]. */
        internal fun parseClusterHealth(clusterVersion: JsonNode, clusterOperators: JsonNode): ClusterHealthResult {
            val version = clusterVersion.path("status").path("desired").path("version").asText(null)
            val availableUpdates = clusterVersion.path("status").path("availableUpdates")
            val updateAvailable = availableUpdates.isArray && availableUpdates.size() > 0

            val degraded = clusterOperators.path("items").mapNotNull { operator ->
                val isDegraded = operator.path("status").path("conditions")
                    .any { it.path("type").asText() == "Degraded" && it.path("status").asText() == "True" }
                operator.path("metadata").path("name").asText().takeIf { isDegraded }
            }

            return ClusterHealthResult(
                healthy = degraded.isEmpty(),
                clusterVersion = version,
                updateAvailable = updateAvailable,
                degradedOperators = degraded,
            )
        }
    }
}
