package nl.vdzon.robbertsassistent.banking

import org.springframework.stereotype.Component
import org.springframework.context.event.EventListener
import org.springframework.boot.context.event.ApplicationReadyEvent
import jakarta.annotation.PreDestroy
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Future
import java.util.concurrent.ExecutionException
import org.springframework.beans.factory.annotation.Value

/** Deze poort krijgt uitsluitend geminimaliseerde betaalinformatie, nooit banktoegang. */
interface BankAiPort { fun assess(items: List<BankAiItem>): List<BankAiDecision> }
data class BankAiItem(val id: String, val merchant: String, val description: String, val amounts: List<String>)
data class BankAiDecision(val id: String, val category: BankCategory, val confidence: Double,
    val needsUser: Boolean, val reason: String, val question: String)
data class BankCategorizationJob(val id: String = "", val status: String = "IDLE", val total: Int = 0,
    val completed: Int = 0, val questions: Int = 0, val batchesTotal: Int = 0, val batchesCompleted: Int = 0, val batchSize: Int = 0, val parallelism: Int = 1, val updatedAt: Instant = Instant.EPOCH, val message: String = "")

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
    private val store: BankStore, private val ai: BankAiPort,
    @Value("\${RA_BANKING_AI_BATCH_SIZE:100}") private val batchSize: Int = 100,
    @Value("\${RA_BANKING_AI_PARALLELISM:3}") private val parallelism: Int = 3) {
    init { require(batchSize in 1..200); require(parallelism in 1..4) }
    private val batchWorkers = Executors.newFixedThreadPool(parallelism) { task ->
        Thread(task, "bank-ai-batch").apply { isDaemon = true }
    }
    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)
    private val rerun = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "bank-rubricering").apply { isDaemon = true } }
    fun status(owner: String): BankCategorizationJob = store.read(owner, "categorization", BankCategorizationJob::class.java) ?: BankCategorizationJob()

    @Synchronized fun start(owner: String, recover: Boolean = false): BankCategorizationJob {
        bank.requireOwner(owner)
        var launch = false
        val job = store.exclusive(owner) {
            val current = status(owner)
            if (!recover && current.status == "RUNNING" && current.updatedAt > Instant.now().minusSeconds(1800)) current.also { rerun.add(owner) }
            else {
                val completed = if (recover && current.status in setOf("RUNNING", "ERROR")) current.completed else 0
                BankCategorizationJob(UUID.randomUUID().toString(), "RUNNING", completed = completed,
                    updatedAt = Instant.now(), message = "Vaste regels worden toegepast.")
                    .also { store.write(owner, "categorization", it); launch = true }
            }
        }
        if (launch) worker.submit { process(owner, job) }
        return job
    }
    @EventListener fun imported(event: BankSynced) { runCatching { start(event.owner) } }
    @EventListener(ApplicationReadyEvent::class) fun resume() {
        if (settings.configured) runCatching { if (bank.connection(settings.owner).accounts.isNotEmpty()) start(settings.owner, recover = true) }
    }
    @PreDestroy fun close() { worker.shutdownNow(); batchWorkers.shutdownNow() }

    private fun process(owner: String, initial: BankCategorizationJob) {
        var job = initial
        val inFlight = mutableSetOf<Future<AssessedBankBatch>>()
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
            val batches = candidates.groupBy { bankAiGroup(it.second) }.values.toList().chunked(batchSize)
            job = job.copy(total = initial.completed + candidates.size, questions = existingQuestions, batchSize = batchSize, parallelism = parallelism,
                batchesTotal = batches.size, message = "AI beoordeelt batches van maximaal $batchSize betaalpatronen, maximaal $parallelism tegelijk.")
            saveJob(owner, job)
            val completion = ExecutorCompletionService<AssessedBankBatch>(batchWorkers)
            val remaining = batches.iterator()
            var failures = 0
            fun submitNext() {
                if (!remaining.hasNext()) return
                val batch = remaining.next()
                inFlight += completion.submit(java.util.concurrent.Callable {
                    val items = batch.mapIndexed { index, entries -> BankAiItem("item-$index", bankAiText(entries.first().second.counterparty),
                        bankAiText(entries.first().second.description), entries.map { it.second.amount.toPlainString() }.distinct().take(3)) }
                    val started = System.nanoTime()
                    logger.info("Bank-AI batch gestart: patronen={}, betalingen={}", items.size, batch.sumOf { it.size })
                    val decisions = ai.assess(items)
                    require(decisions.size == items.size && decisions.map { it.id }.distinct().size == decisions.size && decisions.all { d -> items.any { it.id == d.id } })
                    logger.info("Bank-AI batch klaar: patronen={}, duurMs={}", items.size, (System.nanoTime() - started) / 1_000_000)
                    AssessedBankBatch(batch, decisions.associateBy { it.id })
                })
            }
            repeat(minOf(parallelism, batches.size)) { submitNext() }
            while (inFlight.isNotEmpty()) {
                if (Thread.currentThread().isInterrupted || status(owner).id != job.id) return
                val finished = completion.take()
                inFlight.remove(finished)
                val result = try { finished.get() } catch (ex: ExecutionException) {
                    logger.warn("Bank-AI batch mislukt: type={}", ex.cause?.javaClass?.simpleName)
                    failures++
                    submitNext()
                    continue
                }
                val batch = result.batch
                val byId = result.decisions
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
                    job = job.copy(completed = job.completed + batch.sumOf { it.size }, batchesCompleted = job.batchesCompleted + 1,
                        questions = job.questions + batch.mapIndexed { index, entries ->
                            if (applyBankAi(entries.first().second, byId["item-$index"]).category == BankCategory.REVIEW) entries.size else 0
                        }.sum(), updatedAt = Instant.now())
                    store.write(owner, "categorization", job)
                }
                submitNext()
            }
            saveJob(owner, job.copy(status = if (failures == 0) "DONE" else "ERROR", updatedAt = Instant.now(),
                message = if (failures == 0) "Regels en AI zijn toegepast. Beantwoord de resterende vragen."
                    else "$failures AI-batch(es) zijn niet gelukt. Voltooide resultaten zijn opgeslagen; opnieuw rubriceren pakt alleen de resterende betalingen op."))
        } catch (_: Exception) {
            saveJob(owner, job.copy(status = "ERROR", updatedAt = Instant.now(),
                message = "Vaste regels zijn toegepast. AI-rubricering is onderbroken; je kunt opnieuw rubriceren of vragen zelf beantwoorden."))
        } finally {
            inFlight.forEach { it.cancel(true) }
            if (rerun.remove(owner) && !worker.isShutdown) runCatching { start(owner) }
        }
    }
    private data class AssessedBankBatch(val batch: List<List<Pair<String, BankTransaction>>>, val decisions: Map<String, BankAiDecision>)
    private fun saveJob(owner: String, job: BankCategorizationJob) = store.exclusive(owner) {
        if (status(owner).id == job.id) store.write(owner, "categorization", job.copy(updatedAt = Instant.now()))
    }
}
