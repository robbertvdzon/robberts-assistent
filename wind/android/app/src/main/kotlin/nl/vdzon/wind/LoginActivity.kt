package nl.vdzon.wind

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

/**
 * Eenmalige, zichtbare inlogstap: koppelt een Google-account aan de app zodat
 * [AnswerTrampolineActivity] daarna stil (silent sign-in) bij `robberts-assistent-backend` kan
 * inloggen. Bereikbaar door het app-icoon te openen zolang er nog geen gecachete sessie is (zie
 * [MainActivity]) — daarna gaat het app-icoon weer direct terug naar de headless trampoline-flow.
 */
class LoginActivity : ComponentActivity() {

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleSignInResult(result.data)
        }

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusText = TextView(this).apply {
            text = "Log in met je Google-account om de assistent te koppelen."
            textSize = 16f
        }
        val loginButton = Button(this).apply {
            text = "Inloggen met Google"
            setOnClickListener { signInLauncher.launch(AssistantClient.signInClient(this@LoginActivity).signInIntent) }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            addView(statusText)
            addView(loginButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 32
                gravity = Gravity.CENTER
            })
        }
        setContentView(layout)
    }

    private fun handleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken == null) {
                showError("Geen Google ID-token ontvangen.")
                return
            }
            statusText.text = "Bezig met inloggen…"
            lifecycleScope.launch {
                val success = AssistantClient.completeLogin(this@LoginActivity, idToken)
                if (success) {
                    Toast.makeText(this@LoginActivity, "Ingelogd — je kunt dit scherm sluiten.", Toast.LENGTH_LONG).show()
                    statusText.text = "Ingelogd! Je kunt dit scherm sluiten en de wind-knoppen gebruiken."
                } else {
                    showError("Inloggen bij de backend is mislukt. Probeer het later opnieuw.")
                }
            }
        } catch (e: ApiException) {
            showError("Google-inloggen mislukt (${e.statusCode}).")
        }
    }

    private fun showError(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
