package nl.vdzon.robbertsassistent.assistant

import org.springframework.stereotype.Service

/**
 * Verwerkt een bericht van de gebruiker (spraak of getypt) en geeft een antwoord terug. Nu nog
 * een vaste dummy-reactie; wordt later vervangen door echte intentherkenning/afhandeling.
 */
@Service
class AssistantService {
    fun reply(message: String): String = "Ga ik doen"
}
