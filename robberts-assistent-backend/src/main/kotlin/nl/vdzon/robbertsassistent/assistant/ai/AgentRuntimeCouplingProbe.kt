package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor de Agent Runtime (chat-assistent, briefing en moestuin-foto-chat). */
@Component
class AgentRuntimeCouplingProbe(
    private val secrets: AppSecrets,
    private val gateway: AgentRuntimeGateway,
) : CouplingProbe {
    override val id = "agent-runtime"
    override val name = "Agent Runtime (chat + vision)"
    override val description = "De AI-assistent, de briefing en de moestuin-foto-chat, via de Agent Runtime."
    override val configured: Boolean get() = !secrets.agentRuntimeToken.isNullOrBlank()
    override val mode: String get() = if (secrets.effectiveMockAi) "fallback" else "echt"

    override fun test(): Pair<Boolean, String> = gateway.ping()
}
