package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.automower.AutomowerClient
import nl.vdzon.robbertsassistent.automower.activityDescription
import nl.vdzon.robbertsassistent.automower.stateDescription
import nl.vdzon.robbertsassistent.openshift.ClusterHealthResult
import nl.vdzon.robbertsassistent.openshift.OpenShiftClient
import nl.vdzon.robbertsassistent.openshift.describe
import nl.vdzon.robbertsassistent.softwarefactory.SoftwareFactoryClient
import nl.vdzon.robbertsassistent.zonneplan.ZonneplanClient
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Systeem-checkrapport-briefingsectie (story 2 van 2): bundelt vijf checks — zonnepanelen
 * ([ZonneplanClient], Zonneplan via Home Assistant), Time Machine-backups (via
 * [OpenShiftClient]'s `nodeMetrics.timeMachine`, zelfde node-metrics-route als de
 * OpenShift-check hieronder), OpenShift-gezondheid ([OpenShiftClient]), robotmaaier
 * ([AutomowerClient]) en Software Factory ([SoftwareFactoryClient]) — tot één AI-beoordeeld
 * rapport. De code verzamelt alleen ruwe feiten per check; welke check "aandacht nodig" heeft
 * bepaalt uitsluitend de AI-aanroep ([SYSTEM_STATUS_SYSTEM_PROMPT] in `BriefingAiConfig`), geen
 * hardcoded drempel in code (voor een harde, deterministische drempel op nagenoeg-nul-opbrengst,
 * zie `zonneplan.ZonneplanCouplingProbe` op het Koppelingen-scherm, of voor backups
 * [nl.vdzon.robbertsassistent.openshift.TimeMachineNightlyCheck]). Faalt de AI-call (of levert 'ie
 * geen herkenbaar antwoord), dan valt de sectie stil terug op een neutrale tekst zonder
 * aandachtspunten — zelfde beschermende patroon als [WeekTasksSectionProvider]. Een falende
 * onderliggende client (Zonneplan/OpenShift/Automower/Software Factory) crasht de sectie niet: die
 * ene check meldt dan gewoon "kon niet opgehaald worden" als ruwe data aan de AI.
 */
@Component
class SystemStatusSectionProvider(
    private val zonneplanClient: ZonneplanClient,
    private val openShiftClient: OpenShiftClient,
    private val automowerClient: AutomowerClient,
    private val softwareFactoryClient: SoftwareFactoryClient,
    @Qualifier("systemStatusChatClient") private val chatClient: ChatClient,
) : BriefingSectionProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val order = 40

    override fun section(): BriefingSection {
        val result = assess()
        return BriefingSection(key = "system-status", title = "Systeemstatus", text = result.text, items = result.items)
    }

    override fun shortSummary(): String? {
        val result = assess()
        if (!result.hasAttention) return null
        return "⚠️ " + result.attentionItems.joinToString(", ")
    }

    /**
     * Verzamelt de vijf ruwe per-check statusregels ([buildChecks]) en laat die door de AI
     * beoordelen op "aandacht nodig" ([callAi]/[parseAiReply]). De ruwe [CheckData]-regels gaan
     * ongewijzigd mee als [SystemStatusResult.items] (heading + de bestaande ruwe statustekst,
     * zonder AI-parafrasering) — dit voedt de "Health check"-pagina in de app; de AI-beoordeelde
     * [SystemStatusResult.text]/[SystemStatusResult.attentionItems] blijven ongewijzigd de bron
     * voor de 18:00-push.
     */
    private fun assess(): SystemStatusResult {
        val checks = buildChecks()
        val rawData = checks.joinToString("\n") { "${it.heading}: ${it.content}" }
        val items = checks.map { BriefingItem(text = it.content, heading = it.heading) }

        return runCatching { callAi(rawData) }
            .map { it.copy(items = items) }
            .getOrElse {
                logger.warn("Systeemstatus-AI-beoordeling mislukt", it)
                SystemStatusResult(text = FALLBACK_TEXT, attentionItems = emptyList(), items = items)
            }
    }

    private fun callAi(rawData: String): SystemStatusResult {
        val reply = chatClient.prompt().user(rawData).call().content()?.trim().orEmpty()
        if (reply.isBlank()) return SystemStatusResult(text = FALLBACK_TEXT, attentionItems = emptyList())
        return parseAiReply(reply)
    }

    /**
     * `health` wordt één keer opgehaald en gedeeld door [backupsCheckData] en [openShiftCheckData]
     * — allebei lezen uit dezelfde node-metrics-route, en `clusterHealth()` vangt netwerkfouten
     * zelf al op (levert een `error`-veld i.p.v. te gooien), dus een gedeelde `runCatching` hier
     * volstaat.
     */
    private fun buildChecks(): List<CheckData> {
        val health = runCatching { openShiftClient.clusterHealth() }.getOrNull()
        return listOf(
            runCatching { solarCheckData() }.getOrElse { CheckData("Zonnepanelen", "kon status niet ophalen (${it.message}).") },
            runCatching { backupsCheckData(health) }.getOrElse { CheckData("Backups", "kon status niet ophalen (${it.message}).") },
            runCatching { openShiftCheckData(health) }.getOrElse { CheckData("OpenShift", "kon status niet ophalen (${it.message}).") },
            runCatching { automowerCheckData() }.getOrElse { CheckData("Robotmaaier", "kon status niet ophalen (${it.message}).") },
            runCatching { softwareFactoryCheckData() }.getOrElse { CheckData("Software Factory", "kon stories niet ophalen (${it.message}).") },
        )
    }

    private fun solarCheckData(): CheckData {
        val result = zonneplanClient.status()
        result.error?.let { return CheckData("Zonnepanelen", "kon status niet ophalen ($it).") }
        val current = result.currentPowerWatt?.let { "$it W" } ?: "onbekend"
        val yesterday = result.yesterdayYieldKwh?.let { "$it kWh" } ?: "onbekend"
        return CheckData("Zonnepanelen", "huidig vermogen=$current, gisteren opgewekt=$yesterday.")
    }

    /**
     * Time Machine-backups van beide MacBooks — zelfde `nodeMetrics.timeMachine` als
     * [nl.vdzon.robbertsassistent.openshift.TimeMachineNightlyCheck], maar hier bewust zonder
     * hardcoded staleness-drempel: de AI beoordeelt zelf of het laatste schrijfmoment "aandacht
     * nodig" is (zie klasse-KDoc). Voor een harde, deterministische drempel is er de aparte
     * nightly check.
     */
    private fun backupsCheckData(health: ClusterHealthResult?): CheckData {
        if (health == null) return CheckData("Backups", "kon status niet ophalen.")
        health.error?.let { return CheckData("Backups", "kon status niet ophalen ($it).") }
        val timeMachine = health.nodeMetrics?.timeMachine
            ?: return CheckData("Backups", "geen Time Machine-gegevens ontvangen van node-metrics.")
        return CheckData("Backups", "${timeMachine.describe()}.")
    }

    private fun openShiftCheckData(health: ClusterHealthResult?): CheckData {
        if (health == null) return CheckData("OpenShift", "kon status niet ophalen.")
        health.error?.let { return CheckData("OpenShift", "kon status niet ophalen ($it).") }
        val degraded = health.degradedOperators.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "geen"
        val updates = if (health.updateAvailable) health.availableUpdateVersions.joinToString(" of ") else "geen"
        val metrics = health.nodeMetrics?.let { ", ${it.describe()}" }.orEmpty()
        return CheckData(
            "OpenShift",
            "gezond=${health.healthy}, versie=${health.clusterVersion ?: "onbekend"}, " +
                "beschikbare update=$updates, gedegradeerde operators=$degraded$metrics.",
        )
    }

    private fun automowerCheckData(): CheckData {
        val result = automowerClient.status()
        result.error?.let { return CheckData("Robotmaaier", "kon status niet ophalen ($it).") }
        if (result.mowers.isEmpty()) return CheckData("Robotmaaier", "geen maaier gevonden op het account.")
        val text = result.mowers.joinToString("\n") { mower ->
            "Robotmaaier ${mower.name}: activiteit=${activityDescription(mower.activity)}, " +
                "status=${stateDescription(mower.state)}, errorCode=${mower.errorCode}, verbonden=${mower.connected}."
        }
        return CheckData("Robotmaaier", text)
    }

    private fun softwareFactoryCheckData(): CheckData {
        val result = softwareFactoryClient.stories()
        result.error?.let { return CheckData("Software Factory", "kon stories niet ophalen ($it).") }
        if (result.stories.isEmpty()) return CheckData("Software Factory", "geen stories gevonden.")
        val text = result.stories.joinToString("\n") { story ->
            "Software factory-story ${story.key}: fase=${story.phase ?: "onbekend"}, " +
                "merged=${story.merged}, error=${story.error ?: "geen"}."
        }
        return CheckData("Software Factory", text)
    }

    /** Eén ruwe, per-check statusregel (kop + de bestaande, niet-AI-samengevatte tekst). */
    internal data class CheckData(val heading: String, val content: String)

    internal data class SystemStatusResult(
        val text: String,
        val attentionItems: List<String>,
        val items: List<BriefingItem> = emptyList(),
    ) {
        val hasAttention: Boolean get() = attentionItems.isNotEmpty()
    }

    internal companion object {
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
