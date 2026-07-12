package nl.vdzon.wind

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Praat met `robberts-assistent-backend` (zelfde patroon als de Flutter-apps' `ApiClient`, maar
 * dan native): silent Google Sign-In -> sessie-token -> `/api/v1/assistant/message`. Bewust GEEN
 * interactieve sign-in-popup hier — dit wordt aangeroepen vanuit de headless trampoline-activities
 * (zie [AnswerTrampolineActivity]), die altijd snel moeten blijven en nooit UI mogen tonen. De
 * interactieve login (eenmalig, als er nog geen Google-account gekoppeld is) gebeurt in
 * [LoginActivity], bereikbaar via het app-icoon zelf (zie [MainActivity]).
 */
object AssistantClient {
    private const val PREFS_NAME = "assistant_session"
    private const val KEY_TOKEN = "session_token"
    private const val JSON = "application/json; charset=utf-8"
    private const val FETCH_TIMEOUT_MILLIS = 8_000L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_CLIENT_ID)
            .requestEmail()
            .build()

    fun signInClient(context: Context): GoogleSignInClient =
        GoogleSignIn.getClient(context, signInOptions())

    fun hasSession(context: Context): Boolean = sessionToken(context) != null

    private fun sessionToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    private fun storeSessionToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    /** Wist een verlopen/ongeldig sessie-token, zodat de volgende poging weer silent sign-in probeert. */
    private fun clearSessionToken(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Ruilt een Google ID-token in voor een backend-sessie-token en slaat 'm op. Aangeroepen door
     * [LoginActivity] na een geslaagde interactieve sign-in.
     */
    suspend fun completeLogin(context: Context, googleIdToken: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("idToken", googleIdToken).toString()
                    .toRequestBody(JSON.toMediaType())
                val request = Request.Builder()
                    .url("${BuildConfig.API_BASE_URL}/api/v1/auth/google")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val json = JSONObject(response.body?.string().orEmpty())
                    storeSessionToken(context, json.getString("token"))
                    true
                }
            }.getOrDefault(false)
        }

    /**
     * Probeert (alleen stil, geen popup) een antwoord van de assistent te krijgen op [question].
     * Geeft `null` bij elke soort falen (geen gecachete sign-in, netwerkfout, niet-geautoriseerd,
     * te traag, ...) — de caller valt dan terug op de statische [WindAnswers]-tekst. De trampoline-
     * activities moeten snel blijven, dus een harde totale timeout i.p.v. te vertrouwen op de
     * OkHttp-timeouts alleen (die zouden samen met silent-sign-in alsnog >20s kunnen duren).
     */
    suspend fun tryFetchAnswer(context: Context, question: String): String? =
        withTimeoutOrNull(FETCH_TIMEOUT_MILLIS) {
            val token = ensureSessionToken(context) ?: return@withTimeoutOrNull null
            val answer = askAssistant(token, question)
            if (answer != null) return@withTimeoutOrNull answer

            // Sessie-token kan verlopen zijn (backend-tokens gelden 30 dagen) — wis 'm en probeer
            // één keer opnieuw via silent sign-in, anders blijft de app na verloop stilzwijgend
            // permanent op de statische terugvaltekst hangen.
            clearSessionToken(context)
            val freshToken = ensureSessionToken(context) ?: return@withTimeoutOrNull null
            askAssistant(freshToken, question)
        }

    /** Stille Google-sign-in (geen UI) + backend-login, alleen als er al een gecachet account is. */
    private suspend fun ensureSessionToken(context: Context): String? {
        sessionToken(context)?.let { return it }
        val account = runCatching { signInClient(context).silentSignIn().await() }.getOrNull()
            ?: return null
        val idToken = account.idToken ?: return null
        return if (completeLogin(context, idToken)) sessionToken(context) else null
    }

    /** `null` bij elke fout, inclusief een niet-2xx-status (bv. 401 door een verlopen token). */
    private suspend fun askAssistant(token: String, question: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("text", question).toString()
                    .toRequestBody(JSON.toMediaType())
                val request = Request.Builder()
                    .url("${BuildConfig.API_BASE_URL}/api/v1/assistant/message")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    JSONObject(response.body?.string().orEmpty()).getString("text")
                }
            }.getOrNull()
        }
}
