package nl.vdzon.wind

/**
 * Trampoline-activity voor de App Actions-capability "huidige windsnelheid".
 * Spreekt de windsnelheid uit en post dezelfde tekst als notificatie.
 */
class WindSpeedActivity : AnswerTrampolineActivity() {
    override val answer: String = WindAnswers.WIND_SPEED
    override val notificationTitle: String = "Huidige windsnelheid"
}
