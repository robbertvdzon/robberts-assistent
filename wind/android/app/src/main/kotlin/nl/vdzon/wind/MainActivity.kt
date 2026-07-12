package nl.vdzon.wind

import android.content.Intent
import android.os.Bundle

/**
 * Launcher-activity. Zolang er nog geen gecachete backend-sessie is (zie [AssistantClient]),
 * stuurt deze meteen door naar [LoginActivity] — een eenmalige, zichtbare inlogstap, want de
 * trampoline-flow (zie [AnswerTrampolineActivity]) mag zelf nooit UI tonen en kan zonder sessie
 * alleen stil (silent sign-in) proberen in te loggen. Ná een geslaagde login gedraagt het
 * app-icoon zich weer als trampoline: geen UI, spreekt de huidige windsnelheid uit, post 'm als
 * notificatie en sluit zichzelf direct af. Zo kunnen we ook testen of "Hey Google, open Wind" dit
 * gedrag triggert.
 */
class MainActivity : AnswerTrampolineActivity() {
    override val question: String = "Wat is de huidige windsnelheid bij IJmuiden?"
    override val fallbackAnswer: String = WindAnswers.WIND_SPEED
    override val notificationTitle: String = "Huidige windsnelheid"

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!AssistantClient.hasSession(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        super.onCreate(savedInstanceState)
    }
}
