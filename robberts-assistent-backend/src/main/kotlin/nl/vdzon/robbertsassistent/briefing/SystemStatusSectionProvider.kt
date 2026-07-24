package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.automower.AutomowerClient
import nl.vdzon.robbertsassistent.automower.activityDescription
import nl.vdzon.robbertsassistent.automower.stateDescription
import nl.vdzon.robbertsassistent.openshift.OpenShiftClient
import nl.vdzon.robbertsassistent.openshift.describe
import nl.vdzon.robbertsassistent.softwarefactory.SoftwareFactoryClient
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Systeem-checkrapport-briefingsectie (story 2 van 2): bundelt vijf checks — zonnepanelen
 * (dummy), backups (dummy), OpenShift-gezondheid ([OpenShiftClient]), robotmaaier
 * ([AutomowerClient]) en Software Factory ([SoftwareFactoryClient]) — tot één AI-beoordeeld
 * rapport. De code verzamelt alleen ruwe feiten per check; welke check "aandacht nodig" heeft
 * bepaalt uitsluitend de AI-aanroep ([SYSTEM_STATUS_SYSTEM_PROMPT] in `BriefingAiConfig`), geen
 * hardcoded drempel in code. Faalt de AI-call (of levert 'ie geen herkenbaar antwoord), dan valt
 * de sectie stil terug op een neutrale tekst zonder aandachtspunten — zelfde beschermende patroon
 * als [WeekTasksSectionProvider]. Een falende onderliggende client (OpenShift/Automower/Software
 * Factory) crasht de sectie niet: die ene check meldt dan gewoon "kon niet opgehaald worden" als
 * ruwe data aan de AI.
 */
@Component
class SystemStatusSectionProvider(
    private val openShiftClient: OpenShiftClient,
    private val automowerClient: AutomowerClient,
    private val softwareFactoryClient: SoftwareFactoryClient,
    @Qualifier("systemStatusChatClient") private val chatClient: ChatClient,
) : BriefingSectionProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val order = 40

    override fun section(): BriefingSection =
        BriefingSection(key = "system-status", title = "Systeemstatus", text = assess().text)

    override fun shortSummary(): String? {
        val result = assess()
        if (!result.hasAttention) return null
        return "⚠️ " + result.attentionItems.joinToString(", ")
    }

    private fun assess(): SystemStatusResult {
        val rawData = listOf(
            SOLAR_DUMMY_TEXT,
            BACKUPS_DUMMY_TEXT,
            runCatching { buildOpenShiftText() }.getOrElse { "OpenShift: kon status niet ophalen (${it.message})." },
            runCatching { buildAutomowerText() }.getOrElse { "Robotmaaier: kon status niet ophalen (${it.message})." },
            runCatching { buildSoftwareFactoryText() }.getOrElse { "Software Factory: kon stories niet ophalen (${it.message})." },
        ).joinToString("\n")

        return runCatching { callAi(rawData) }
            .getOrElse {
                logger.warn("Systeemstatus-AI-beoordeling mislukt", it)
                SystemStatusResult(text = FALLBACK_TEXT, attentionItems = emptyList())
            }
    }

    private fun callAi(rawData: String): SystemStatusResult {
        val reply = chatClient.prompt().user(rawData).call().content()?.trim().orEmpty()
        if (reply.isBlank()) return SystemStatusResult(text = FALLBACK_TEXT, attentionItems = emptyList())
        return parseAiReply(reply)
    }

    private fun buildOpenShiftText(): String {
        val health = openShiftClient.clusterHealth()
        health.error?.let { return "OpenShift: kon status niet ophalen ($it)." }
        val degraded = health.degradedOperators.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "geen"
        val updates = if (health.updateAvailable) health.availableUpdateVersions.joinToString(" of ") else "geen"
        val metrics = health.nodeMetrics?.let { ", ${it.describe()}" }.orEmpty()
        return "OpenShift: gezond=${health.healthy}, versie=${health.clusterVersion ?: "onbekend"}, " +
            "beschikbare update=$updates, gedegradeerde operators=$degraded$metrics."
    }

    private fun buildAutomowerText(): String {
        val result = automowerClient.status()
        result.error?.let { return "Robotmaaier: kon status niet ophalen ($it)." }
        if (result.mowers.isEmpty()) return "Robotmaaier: geen maaier gevonden op het account."
        return result.mowers.joinToString("\n") { mower ->
            "Robotmaaier ${mower.name}: activiteit=${activityDescription(mower.activity)}, " +
                "status=${stateDescription(mower.state)}, errorCode=${mower.errorCode}, verbonden=${mower.connected}."
        }
    }

    private fun buildSoftwareFactoryText(): String {
        val result = softwareFactoryClient.stories()
        result.error?.let { return "Software Factory: kon stories niet ophalen ($it)." }
        if (result.stories.isEmpty()) return "Software Factory: geen stories gevonden."
        return result.stories.joinToString("\n") { story ->
            "Software factory-story ${story.key}: fase=${story.phase ?: "onbekend"}, " +
                "merged=${story.merged}, error=${story.error ?: "geen"}."
        }
    }

    internal data class SystemStatusResult(val text: String, val attentionItems: List<String>) {
        val hasAttention: Boolean get() = attentionItems.isNotEmpty()
    }

    internal companion object {
        private const val SOLAR_DUMMY_TEXT = "Zonnepanelen: (nog geen koppeling, placeholder) geen afwijkingen bekend."
        private const val BACKUPS_DUMMY_TEXT = "Backups: (nog geen koppeling, placeholder) geen fouten gemeld."
        private const val FALLBACK_TEXT = "Kon het systeemstatusrapport niet ophalen."

        private val ATTENTION_LINE = Regex("(?i)^AANDACHT:\\s*(.*)$")

        /**
         * Parseert het AI-antwoord conform [SYSTEM_STATUS_SYSTEM_PROMPT]: eerste regel
         * "AANDACHT: <lijst>"/"AANDACHT: geen", gevolgd door de rapporttekst. Herkent de AI het
         * verwachte formaat niet (bv. [nl.vdzon.robbertsassistent.assistant.ai.MockChatModel]'s
         * echo-antwoord), dan wordt het hele antwoord als rapporttekst getoond zonder
         * aandachtspunten — nooit een crash, altijd deterministisch.
         */
        internal fun parseAiReply(reply: String): SystemStatusResult {
            val lines = reply.lines()
            val match = ATTENTION_LINE.find(lines.firstOrNull()?.trim().orEmpty())
                ?: return SystemStatusResult(text = reply.trim(), attentionItems = emptyList())

            val value = match.groupValues[1].trim()
            val items = if (value.isBlank() || value.equals("geen", ignoreCase = true)) {
                emptyList()
            } else {
                value.split(",").map { it.trim() }.filter { it.isNotBlank() }
            }
            val bodyText = lines.drop(1).joinToString("\n").trim()
            return SystemStatusResult(text = bodyText.ifBlank { reply.trim() }, attentionItems = items)
        }
    }
}
