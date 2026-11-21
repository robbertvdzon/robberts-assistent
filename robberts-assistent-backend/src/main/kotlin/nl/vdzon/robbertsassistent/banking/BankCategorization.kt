package nl.vdzon.robbertsassistent.banking

import org.springframework.stereotype.Component
import org.springframework.context.event.EventListener
import org.springframework.boot.context.event.ApplicationReadyEvent
import jakarta.annotation.PreDestroy
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/** Deze poort krijgt uitsluitend geminimaliseerde betaalinformatie, nooit banktoegang. */
interface BankAiPort { fun assess(items: List<BankAiItem>): List<BankAiDecision> }
data class BankAiItem(val id: String, val merchant: String, val description: String, val amounts: List<String>)
data class BankAiDecision(val id: String, val category: BankCategory, val confidence: Double,
    val needsUser: Boolean, val reason: String, val question: String)
data class BankCategorizationJob(val id: String = "", val status: String = "IDLE", val total: Int = 0,
    val completed: Int = 0, val questions: Int = 0, val updatedAt: Instant = Instant.EPOCH, val message: String = "")

fun bankAiText(text: String): String = text.replace(Regex("(?i)[A-Z]{2}\\d{2}[A-Z0-9 ]{10,30}"), "[rekening]")
    .replace(Regex("\\d{5,}"), "[nummer]").replace(Regex("\\s+"), " ").trim().take(260)
fun bankAiGroup(t: BankTransaction): String = bankHash(bankAiText(t.counterparty).uppercase() + "|" + bankAiText(t.description).uppercase())

fun applyBankAi(t: BankTransaction, decision: BankAiDecision?): BankTransaction {
    val platform = Regex("(?i)bol[ .]|amazon|paypal|tikkie|mollie|adyen|klarna|sumup|geldmaat|hema|ikea|action|marktplaats")
        .containsMatchIn(t.counterparty + " " + t.description)
    val accepted = decision != null && decision.category !in listOf(BankCategory.REVIEW, BankCategory.INTERNAL) &&
        !decision.needsUser && decision.confidence.isFinite() && decision.confidence in 0.92..1.0 &&
        !platform
    return t.copy(category = if (accepted) decision!!.category else BankCategory.REVIEW,
        classificationSource = if (accepted) "AI" else "QUESTION", aiReviewed = true,
        suggestedCategory = decision?.category?.takeIf { it != BankCategory.INTERNAL && it != BankCategory.REVIEW },
        reason = decision?.reason.orEmpty().take(500),
        question = if (accepted) "" else decision?.question?.takeIf { it.isNotBlank() }?.take(300)
            ?: "Waarvoor was deze betaling bij ${t.counterparty.take(80)}? Was dit nodig of een extra aankoop/activiteit?")
}

@Component
class BankCategorizationRunner(private val settings: BankSettings, private val bank: BankService,
    private val store: BankStore, private val ai: BankAiPort) {
    private val rerun = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "bank-rubricering").apply { isDaemon = true } }
    fun status(owner: String): BankCategorizationJob = store.read(owner, "categorization", BankCategorizationJob::class.java) ?: BankCategorizationJob()

    @Synchronized fun start(owner: String, recover: Boolean = false): BankCategorizationJob {
        bank.requireOwner(owner)
        var launch = false
        val job = store.exclusive(owner) {
            val current = status(owner)
            if (!recover && current.status == "RUNNING" && current.updatedAt > Instant.now().minusSeconds(1800)) current.also { rerun.add(owner) }
            else BankCategorizationJob(UUID.randomUUID().toString(), "RUNNING", updatedAt = Instant.now(), message = "Vaste regels worden toegepast.")
                .also { store.write(owner, "categorization", it); launch = true }
        }
        if (launch) worker.submit { process(owner, job) }
        return job
    }
    @EventListener fun imported(event: BankSynced) { runCatching { start(event.owner) } }
    @EventListener(ApplicationReadyEvent::class) fun resume() {
        if (settings.configured) runCatching { if (bank.connection(settings.owner).accounts.isNotEmpty()) start(settings.owner, recover = true) }
    }
    @PreDestroy fun close() { worker.shutdownNow() }

    private fun process(owner: String, initial: BankCategorizationJob) {
        var job = initial
        try {
            val months = store.keys(owner).filter { it.startsWith("month-") }.sorted()
            val candidates = mutableListOf<Pair<String, BankTransaction>>()
            var existingQuestions = 0
            months.forEach { key ->
                store.exclusive(owner) {
                    check(status(owner).id == job.id) { "Rubricering vervangen door een nieuwe opdracht" }
                    val connection = bank.connection(owner)
                    val rules = bank.rules(owner)
                    val rows = store.read(owner, key, BankTransactions::class.java)?.transactions.orEmpty().map {
                        BankLedger.classify(it, connection.accounts, rules.rules, rules.ownIbans)
                    }
                    store.write(owner, key, BankTransactions(rows))
                    existingQuestions += rows.count { it.category == BankCategory.REVIEW && it.aiReviewed }
                    rows.filter { it.category == BankCategory.REVIEW && !it.aiReviewed }.forEach { candidates += key to it }
                }
            }
            job = job.copy(total = candidates.size, questions = existingQuestions, message = "AI beoordeelt de overige betalingen.")
            saveJob(owner, job)
            val groups = candidates.groupBy { bankAiGroup(it.second) }.values.toList()
            groups.chunked(20).forEach { batch ->
                if (Thread.currentThread().isInterrupted || status(owner).id != job.id) return
                val items = batch.mapIndexed { index, entries -> BankAiItem("item-$index", bankAiText(entries.first().second.counterparty),
                    bankAiText(entries.first().second.description), entries.map { it.second.amount.toPlainString() }.distinct().take(3)) }
                val decisions = ai.assess(items)
                require(decisions.map { it.id }.distinct().size == decisions.size && decisions.all { d -> items.any { it.id == d.id } })
                val byId = decisions.associateBy { it.id }
                store.exclusive(owner) {
                    check(status(owner).id == job.id) { "Rubricering vervangen door een nieuwe opdracht" }
                    val connection = bank.connection(owner)
                    val rules = bank.rules(owner)
                    batch.flatMapIndexed { index, entries -> entries.map { Triple(it.first, it.second.id, byId["item-$index"]) } }
                        .groupBy { it.first }.forEach { (key, updates) ->
                            val mapping = updates.associate { it.second to it.third }
                            val rows = store.read(owner, key, BankTransactions::class.java)?.transactions.orEmpty().map { raw ->
                                val current = BankLedger.classify(raw, connection.accounts, rules.rules, rules.ownIbans)
                                if (raw.id in mapping && current.category == BankCategory.REVIEW && !current.manual && !current.aiReviewed)
                                    applyBankAi(current, mapping[raw.id]) else current
                            }
                            store.write(owner, key, BankTransactions(rows))
                        }
                    job = job.copy(completed = job.completed + batch.sumOf { it.size },
                        questions = job.questions + batch.mapIndexed { index, entries ->
                            if (applyBankAi(entries.first().second, byId["item-$index"]).category == BankCategory.REVIEW) entries.size else 0
                        }.sum(), updatedAt = Instant.now())
                    store.write(owner, "categorization", job)
                }
            }
            saveJob(owner, job.copy(status = "DONE", updatedAt = Instant.now(), message = "Regels en AI zijn toegepast. Beantwoord de resterende vragen."))
        } catch (_: Exception) {
            saveJob(owner, job.copy(status = "ERROR", updatedAt = Instant.now(),
                message = "Vaste regels zijn toegepast. AI-rubricering is onderbroken; je kunt opnieuw rubriceren of vragen zelf beantwoorden."))
        } finally {
            if (rerun.remove(owner) && !worker.isShutdown) runCatching { start(owner) }
        }
    }
    private fun saveJob(owner: String, job: BankCategorizationJob) = store.exclusive(owner) {
        if (status(owner).id == job.id) store.write(owner, "categorization", job.copy(updatedAt = Instant.now()))
    }
}
