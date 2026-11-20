package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.automower.AutomowerClient
import nl.vdzon.robbertsassistent.automower.activityDescription
import nl.vdzon.robbertsassistent.automower.stateDescription
import java.time.Duration
import java.time.Instant
import nl.vdzon.robbertsassistent.openshift.ClusterHealthResult
import nl.vdzon.robbertsassistent.openshift.NodeMetrics
import nl.vdzon.robbertsassistent.openshift.OpenShiftClient
import nl.vdzon.robbertsassistent.openshift.describe
import nl.vdzon.robbertsassistent.softwarefactory.SoftwareFactoryClient
import nl.vdzon.robbertsassistent.zonneplan.SolarService
import nl.vdzon.robbertsassistent.zonneplan.SolarVerdict
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
    // Zonder dit overzicht (in tests) blijft de zonnepanelencheck bij het vermogen en gisteren.
    private val solarService: SolarService? = null,
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
        val items = checks.map { BriefingItem(text = it.content, heading = it.heading, status = it.status) }

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
            runCatching { solarCheckData() }.getOrElse { CheckData("Zonnepanelen", "kon status niet ophalen (${it.message}).", BriefingStatus.NIET) },
            runCatching { backupsCheckData(health) }.getOrElse { CheckData("Backups", "kon status niet ophalen (${it.message}).", BriefingStatus.NIET) },
            runCatching { openShiftCheckData(health) }.getOrElse { CheckData("OpenShift", "kon status niet ophalen (${it.message}).", BriefingStatus.NIET) },
            runCatching { automowerCheckData() }.getOrElse { CheckData("Robotmaaier", "kon status niet ophalen (${it.message}).", BriefingStatus.NIET) },
            runCatching { softwareFactoryCheckData() }.getOrElse { CheckData("Software Factory", "kon stories niet ophalen (${it.message}).", BriefingStatus.NIET) },
        )
    }

    private fun solarCheckData(): CheckData {
        val result = zonneplanClient.status()
        result.error?.let { return CheckData("Zonnepanelen", "kon status niet ophalen ($it).", BriefingStatus.NIET) }
        val current = result.currentPowerWatt?.let { "$it W" } ?: "onbekend"
        val yesterday = result.yesterdayYieldKwh?.let { "$it kWh" } ?: "onbekend"
        val base = "huidig vermogen=$current, gisteren opgewekt=$yesterday."
        val overview = solarService?.let { service -> runCatching { service.overview() }.getOrNull() }
            ?: return CheckData("Zonnepanelen", base, BriefingStatus.GOED)
        val status = when (overview.verdict) {
            SolarVerdict.NIET -> BriefingStatus.NIET
            SolarVerdict.LET_OP -> BriefingStatus.LET_OP
            // Zonder vergelijking met het weer is er geen reden voor alarm.
            SolarVerdict.GOED, SolarVerdict.ONBEKEND -> BriefingStatus.GOED
        }
        return CheckData("Zonnepanelen", "$base\n${overview.verdictText}", status)
    }

    /**
     * Time Machine-backups van beide MacBooks — zelfde `nodeMetrics.timeMachine` als
     * [nl.vdzon.robbertsassistent.openshift.TimeMachineNightlyCheck], maar hier bewust zonder
     * hardcoded staleness-drempel: de AI beoordeelt zelf of het laatste schrijfmoment "aandacht
     * nodig" is (zie klasse-KDoc). Voor een harde, deterministische drempel is er de aparte
     * nightly check.
     */
    private fun backupsCheckData(health: ClusterHealthResult?): CheckData {
        if (health == null) return CheckData("Backups", "kon status niet ophalen.", BriefingStatus.NIET)
        health.error?.let { return CheckData("Backups", "kon status niet ophalen ($it).", BriefingStatus.NIET) }
        val timeMachine = health.nodeMetrics?.timeMachine
            ?: return CheckData("Backups", "geen Time Machine-gegevens ontvangen van node-metrics.", BriefingStatus.LET_OP)
        val now = Instant.now()
        val stale = timeMachine.error != null || timeMachine.backups.isEmpty() || timeMachine.backups.any {
            it.error != null || it.lastModified == null || Duration.between(it.lastModified, now) > BACKUP_STALE_AFTER
        }
        return CheckData("Backups", "${timeMachine.describe()}.", if (stale) BriefingStatus.LET_OP else BriefingStatus.GOED)
    }

    private fun openShiftCheckData(health: ClusterHealthResult?): CheckData {
        if (health == null) return CheckData("OpenShift", "kon status niet ophalen.", BriefingStatus.NIET)
        health.error?.let { return CheckData("OpenShift", "kon status niet ophalen ($it).", BriefingStatus.NIET) }
        val degraded = health.degradedOperators.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "geen"
        val updates = if (health.updateAvailable) health.availableUpdateVersions.joinToString(" of ") else "geen"
        val metrics = health.nodeMetrics?.let { ", ${it.describe()}" }.orEmpty()
        return CheckData(
            "OpenShift",
            "gezond=${health.healthy}, versie=${health.clusterVersion ?: "onbekend"}, " +
                "beschikbare update=$updates, gedegradeerde operators=$degraded$metrics.",
            when {
                !health.healthy -> BriefingStatus.NIET
                health.nodeMetrics.isNearlyFull() -> BriefingStatus.LET_OP
                else -> BriefingStatus.GOED
            },
        )
    }

    private fun automowerCheckData(): CheckData {
        val result = automowerClient.status()
        result.error?.let { return CheckData("Robotmaaier", "kon status niet ophalen ($it).", BriefingStatus.NIET) }
        if (result.mowers.isEmpty()) return CheckData("Robotmaaier", "geen maaier gevonden op het account.", BriefingStatus.LET_OP)
        val text = result.mowers.joinToString("\n") { mower ->
            "Robotmaaier ${mower.name}: activiteit=${activityDescription(mower.activity)}, " +
                "status=${stateDescription(mower.state)}, errorCode=${mower.errorCode}, verbonden=${mower.connected}."
        }
        val status = when {
            result.mowers.any { it.state in MOWER_ERROR_STATES || it.errorCode != 0 } -> BriefingStatus.NIET
            result.mowers.any { !it.connected } -> BriefingStatus.LET_OP
            else -> BriefingStatus.GOED
        }
        return CheckData("Robotmaaier", text, status)
    }

    /**
     * Toont sinds SF-1275 alleen nog stories die aandacht behoeven: een fout ([FactoryStory.error]
     * gezet), of nog niet gemerged mét een gezette fase (dus gestart/gerefined, maar nog niet klaar).
     * Gemergede stories en stories zonder fase (nog niet gestart) zijn "normaal" en worden
     * weggelaten — anders groeit deze check ongelimiteerd met historische ruis.
     */
    private fun softwareFactoryCheckData(): CheckData {
        val result = softwareFactoryClient.stories()
        result.error?.let { return CheckData("Software Factory", "kon stories niet ophalen ($it).", BriefingStatus.NIET) }
        val relevant = result.stories.filter { it.error != null || (it.phase != null && !it.merged) }
        if (relevant.isEmpty()) return CheckData("Software Factory", "geen lopende of error-stories.", BriefingStatus.GOED)
        val text = relevant.joinToString("\n") { story ->
            "Software factory-story ${story.key}: fase=${story.phase ?: "onbekend"}, " +
                "merged=${story.merged}, error=${story.error ?: "geen"}."
        }
        return CheckData("Software Factory", text, if (relevant.any { it.error != null }) BriefingStatus.LET_OP else BriefingStatus.GOED)
    }

    /**
     * Eén ruwe, per-check statusregel (kop + de bestaande, niet-AI-samengevatte tekst). [status] is
     * een vaste regel per check voor het Health check-scherm; wat in de 18:00-push komt beoordeelt
     * de AI nog steeds zelf.
     */
    internal data class CheckData(val heading: String, val content: String, val status: BriefingStatus)

    private fun NodeMetrics?.isNearlyFull(): Boolean = this != null &&
        listOfNotNull(memory?.usedPercent, ssd?.usedPercent, externalHdd?.usedPercent).any { it >= NEARLY_FULL_PERCENT }

    internal data class SystemStatusResult(
        val text: String,
        val attentionItems: List<String>,
        val items: List<BriefingItem> = emptyList(),
    ) {
        val hasAttention: Boolean get() = attentionItems.isNotEmpty()
    }

    internal companion object {
        private val BACKUP_STALE_AFTER = Duration.ofHours(48)
        private const val NEARLY_FULL_PERCENT = 90.0
        private val MOWER_ERROR_STATES = setOf("ERROR", "FATAL_ERROR", "ERROR_AT_POWER_UP")
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
