package nl.vdzon.robbertsassistent.notifier

/**
 * Uitgaande push-poort: één plek waarlangs alle skills (reminders, "goede wind"-alerts,
 * agenda-herinneringen) een bericht naar Robbert sturen. Wie het bericht daadwerkelijk
 * aflevert — Telegram, FCM, of voorlopig alleen een logregel — zit achter deze interface,
 * zodat de skills onafhankelijk zijn van het kanaal.
 */
interface Notifier {
    fun send(message: String)
}
