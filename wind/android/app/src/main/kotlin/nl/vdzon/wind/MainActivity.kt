package nl.vdzon.wind

/**
 * Launcher-activity. Gedraagt zich als trampoline-activity (zie
 * [AnswerTrampolineActivity]): geen UI, spreekt de huidige windsnelheid uit,
 * post 'm als notificatie en sluit zichzelf daarna direct af. Zo kunnen we
 * testen of "Hey Google, open Wind" dit gedrag triggert.
 */
class MainActivity : AnswerTrampolineActivity() {
    override val answer: String = WindAnswers.WIND_SPEED
    override val notificationTitle: String = "Huidige windsnelheid"
}
