package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.push.PushService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Geeft de chat-assistent de mogelijkheid een push-notificatie naar Robberts telefoon te sturen
 * (FCM). Via deze tool test je de keten agent -> FCM -> app ("stuur me een push met mijn agenda").
 */
@Component
class PushTools(private val pushService: PushService) {

    @Tool(
        description = "Stuur een push-notificatie naar Robberts telefoon. Gebruik dit wanneer Robbert " +
            "expliciet vraagt om iets als push/melding/notificatie op zijn telefoon te ontvangen. Stel " +
            "een korte titel + duidelijke body samen (bv. de gevraagde agenda-punten).",
    )
    fun sendPush(
        @ToolParam(description = "Korte titel van de notificatie") title: String,
        @ToolParam(description = "De inhoud/tekst van de notificatie") body: String,
    ): String {
        val count = pushService.sendToAll(title, body)
        return if (count > 0) {
            "Push verstuurd naar $count toestel(len)."
        } else {
            "Kon geen push versturen — geen geregistreerd toestel (open de assistent-app zodat 'ie " +
                "zich registreert) of Firebase is niet actief."
        }
    }
}
