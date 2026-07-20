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
    // OpenAI-API-key voor de chat-assistent (zie AiConfig). Null/leeg => mockAi wordt effectief
    // altijd true, ongeacht de RA_MOCK_AI-waarde: zonder key kan er toch geen echte call gedaan
    // worden, dus liever een voorspelbare mock dan een crash bij de eerste chat-vraag.
    val openAiApiKey: String? = null,
    // Expliciete override om de echte OpenAI-call over te slaan: altijd true in preview/tests
    // (geen kosten, geen netwerk-afhankelijkheid, deterministisch), altijd false in productie.
    val mockAi: Boolean = false,
    // -- Fundament-koppelingen (fase 0: allemaal optioneel; ontbrekend => stub-fallback) --
    // Telegram-bot voor uitgaande berichten (Notifier). Zonder token/chat-id valt de
    // Notifier terug op de LoggingNotifier.
    val telegramBotToken: String? = null,
    val telegramChatId: String? = null,
    // Firebase service-account (Firestore-database + Cloud Storage). Zonder deze waarden blijft
    // alles op de in-memory fallback. Credentials kan als bestandspad (lokaal) OF als JSON-inhoud
    // (productie/sealed secret) — de eerste die gezet is wint.
    val firebaseCredentialsFile: String? = null,
    val firebaseCredentialsJson: String? = null,
    val firebaseProjectId: String? = null,
    // Named Firestore-database (naast de default), bv. "robberts-assistent". Leeg => default DB.
    val firebaseDatabaseId: String? = null,
    // Cloud Storage-bucket voor de moestuin-foto's, bv. "tuinbewatering.firebasestorage.app".
    val firebaseStorageBucket: String? = null,
    // Google OAuth "offline access" refresh-token flow (Agenda + Docs, read-only). De
    // backend wisselt de refresh-token zelf in voor korte access-tokens (in memory).
    val googleOAuthClientId: String? = null,
    val googleOAuthClientSecret: String? = null,
    val googleOAuthRefreshToken: String? = null,
    // Husqvarna Automower Connect API — client_credentials-flow (app-key/secret, geen los
    // OAuth-consent nodig). Zonder deze waarden valt de robotmaaier-koppeling terug op de stub.
    val husqvarnaAppKey: String? = null,
    val husqvarnaAppSecret: String? = null,
    // Strava API v3 — OAuth "offline access" refresh-token flow, zelfde patroon als Google
    // Agenda/Docs. Zonder deze waarden valt de trainingen-koppeling terug op de stub.
    val stravaClientId: String? = null,
    val stravaClientSecret: String? = null,
    val stravaRefreshToken: String? = null,
) {
    /** Of de chat-assistent een [nl.vdzon.robbertsassistent.assistant.ai.MockChatModel] moet gebruiken. */
    val effectiveMockAi: Boolean get() = mockAi || openAiApiKey.isNullOrBlank()

    /** Of Firebase (Firestore + Storage) bruikbaar is: credentials (bestand of JSON) + project-id. */
    val firebaseConfigured: Boolean
        get() = (!firebaseCredentialsFile.isNullOrBlank() || !firebaseCredentialsJson.isNullOrBlank()) &&
            !firebaseProjectId.isNullOrBlank()
}

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
        val mockAi = optional("RA_MOCK_AI")?.lowercase() == "true"
        return AppSecrets(
            rememberSecret = required("RA_REMEMBER_SECRET"),
            googleClientId = required("RA_GOOGLE_CLIENT_ID"),
            allowedEmails = parseAllowedEmails(optional("RA_ALLOWED_EMAILS") ?: DEFAULT_ALLOWED_EMAIL),
            previewSkipGoogleAuth = previewSkipGoogleAuth,
            openAiApiKey = optional("RA_OPENAI_API_KEY"),
            mockAi = mockAi,
            telegramBotToken = optional("RA_TELEGRAM_BOT_TOKEN"),
            telegramChatId = optional("RA_TELEGRAM_CHAT_ID"),
            firebaseCredentialsFile = optional("RA_FIREBASE_CREDENTIALS_FILE"),
            firebaseCredentialsJson = optional("RA_FIREBASE_CREDENTIALS_JSON"),
            firebaseProjectId = optional("RA_FIREBASE_PROJECT_ID"),
            firebaseDatabaseId = optional("RA_FIREBASE_DATABASE_ID"),
            firebaseStorageBucket = optional("RA_FIREBASE_STORAGE_BUCKET"),
            googleOAuthClientId = optional("RA_GOOGLE_OAUTH_CLIENT_ID"),
            googleOAuthClientSecret = optional("RA_GOOGLE_OAUTH_CLIENT_SECRET"),
            googleOAuthRefreshToken = optional("RA_GOOGLE_OAUTH_REFRESH_TOKEN"),
            husqvarnaAppKey = optional("RA_HUSQVARNA_APP_KEY"),
            husqvarnaAppSecret = optional("RA_HUSQVARNA_APP_SECRET"),
            stravaClientId = optional("RA_STRAVA_CLIENT_ID"),
            stravaClientSecret = optional("RA_STRAVA_CLIENT_SECRET"),
            stravaRefreshToken = optional("RA_STRAVA_REFRESH_TOKEN"),
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
