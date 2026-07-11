package nl.vdzon.robbertsassistent.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path

data class AppSecrets(
    // Ondertekent (HMAC) het sessie-token dat de backend na een geslaagde Google-login afgeeft.
    val rememberSecret: String,
    // OAuth-client-ID (audience) waartegen Google-ID-tokens worden gevalideerd.
    val googleClientId: String,
    // Toegestane, geverifieerde e-mailadressen (genormaliseerd naar lowercase). Alleen deze
    // adressen krijgen een sessie-token, ongeacht een verder geldig Google-token.
    val allowedEmails: Set<String>,
    // Alleen aan in branch-preview-omgevingen (zie deploy/overlays/preview): slaat de
    // Google-login volledig over zodat de tester-agent zonder account kan inloggen. Moet in
    // productie altijd false zijn.
    val previewSkipGoogleAuth: Boolean = false,
)

@Configuration
class AppConfig {
    @Bean
    fun appSecrets(): AppSecrets = AppSecretsLoader().load()
}

class AppSecretsLoader(
    private val environment: Map<String, String> = System.getenv(),
    private val secretFiles: List<Path>? = null,
) {
    fun load(): AppSecrets {
        val fileValues = candidateSecretFiles()
            .firstOrNull { Files.exists(it) }
            ?.let { parse(it) }
            ?: emptyMap()
        fun required(key: String): String =
            resolve(key, fileValues) ?: error("Missing required robberts-assistent configuration: $key")
        fun optional(key: String): String? = resolve(key, fileValues)

        val previewSkipGoogleAuth = optional("RA_PREVIEW_SKIP_GOOGLE_AUTH")?.lowercase() == "true"
        return AppSecrets(
            rememberSecret = required("RA_REMEMBER_SECRET"),
            googleClientId = required("RA_GOOGLE_CLIENT_ID"),
            allowedEmails = parseAllowedEmails(optional("RA_ALLOWED_EMAILS") ?: DEFAULT_ALLOWED_EMAIL),
            previewSkipGoogleAuth = previewSkipGoogleAuth,
        )
    }

    private fun parseAllowedEmails(raw: String): Set<String> =
        raw.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun resolve(key: String, fileValues: Map<String, String>): String? =
        fileValues[key]?.takeIf { it.isNotBlank() } ?: environment[key]?.takeIf { it.isNotBlank() }

    private fun candidateSecretFiles(): List<Path> {
        secretFiles?.let { return it }
        val override = environment["RA_SECRETS_FILE"]?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        return listOfNotNull(override) + listOf(Path.of("secrets.env"), Path.of("../secrets.env"))
    }

    private fun parse(path: Path): Map<String, String> =
        Files.readAllLines(path).mapNotNull { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
            val normalized = line.removePrefix("export ").trim()
            val separator = normalized.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            normalized.substring(0, separator).trim() to normalized.substring(separator + 1).trim().stripQuotes()
        }.toMap()

    private fun String.stripQuotes(): String =
        if ((startsWith("\"") && endsWith("\"")) || (startsWith("'") && endsWith("'"))) substring(1, length - 1) else this

    private companion object {
        const val DEFAULT_ALLOWED_EMAIL = "robbert@vdzon.com"
    }
}
