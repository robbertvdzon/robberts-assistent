package nl.vdzon.wind

/**
 * Trampoline-activity voor de App Actions-capability "voorspelling".
 * Spreekt de voorspelling uit en post dezelfde tekst als notificatie.
 */
class ForecastActivity : AnswerTrampolineActivity() {
    override val answer: String = WindAnswers.FORECAST
    override val notificationTitle: String = "Windvoorspelling"
}
