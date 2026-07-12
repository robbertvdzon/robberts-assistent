package nl.vdzon.wind

/**
 * Trampoline-activity voor de App Actions-capability "voorspelling".
 * Haalt de windvoorspelling op via de chat-assistent (zie [AnswerTrampolineActivity]); spreekt
 * het antwoord uit en post dezelfde tekst als notificatie.
 */
class ForecastActivity : AnswerTrampolineActivity() {
    override val question: String = "Wat is de windvoorspelling voor IJmuiden?"
    override val fallbackAnswer: String = WindAnswers.FORECAST
    override val notificationTitle: String = "Windvoorspelling"
}
