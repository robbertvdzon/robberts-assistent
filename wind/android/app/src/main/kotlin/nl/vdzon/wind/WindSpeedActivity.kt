package nl.vdzon.wind

/**
 * Trampoline-activity voor de App Actions-capability "huidige windsnelheid".
 * Haalt de actuele wind op via de chat-assistent (zie [AnswerTrampolineActivity]); spreekt het
 * antwoord uit en post dezelfde tekst als notificatie.
 */
class WindSpeedActivity : AnswerTrampolineActivity() {
    override val question: String = "Wat is de huidige windsnelheid bij IJmuiden?"
    override val fallbackAnswer: String = WindAnswers.WIND_SPEED
    override val notificationTitle: String = "Huidige windsnelheid"
}
