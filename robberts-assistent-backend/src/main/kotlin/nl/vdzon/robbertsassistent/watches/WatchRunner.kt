package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class PushServiceWatchNotifier(private val pushService: PushService) : WatchPushNotifier {
    override fun found(watch: Watch) {
        pushService.sendToAll(
            title = "Zoekopdracht gevonden",
            body = "${watch.title}: ${watch.statusDescription}",
            data = mapOf("type" to "watch"),
        )
    }
}

@Component
class WatchRunner(
    private val repository: WatchRepository,
    private val pageFetcher: WatchPageFetcher,
    private val evaluator: WatchEvaluator,
    private val pushNotifier: WatchPushNotifier,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${ra.watches.poll-interval-ms:300000}")
    fun poll() = poll(Instant.now())

    internal fun poll(now: Instant) {
        repository.all()
            .filter { WatchSchedule.isDue(it, now) }
            .forEach { watch -> check(watch, now) }
    }

    private fun check(watch: Watch, now: Instant) {
        val assessment = runCatching {
            evaluator.assess(watch.instruction, pageFetcher.fetch(watch.url))
        }.getOrElse {
            logger.warn("Controle van watch {} mislukte: {}", watch.id, it.message)
            repository.updateIfPresent(
                watch.copy(
                    status = WatchStatus.ONBEKEND,
                    statusDescription = "Controle mislukt; de opdracht wordt later opnieuw geprobeerd.",
                    lastCheckedAt = now,
                ),
            )
            return
        }

        val updated = watch.copy(
            status = if (assessment.found) WatchStatus.GEVONDEN else WatchStatus.NIET_GEVONDEN,
            statusDescription = assessment.description,
            lastCheckedAt = now,
            active = !assessment.found,
        )
        val persisted = repository.updateIfPresent(updated) ?: return
        if (assessment.found && watch.status != WatchStatus.GEVONDEN && watch.notifyOnFound) {
            runCatching { pushNotifier.found(persisted) }
                .onFailure { logger.warn("Push van gevonden watch {} mislukte: {}", watch.id, it.message) }
        }
    }
}
